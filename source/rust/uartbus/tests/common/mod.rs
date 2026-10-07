//! Helpers shared by the integration tests.
#![allow(dead_code)]

use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU32, Ordering};
use std::time::{Duration, Instant};

/// A directory under the system temp directory, removed when dropped.
pub struct TempDir(PathBuf);

impl TempDir {
    pub fn new(name: &str) -> Self {
        static COUNTER: AtomicU32 = AtomicU32::new(0);
        let dir = std::env::temp_dir().join(format!(
            "uartbus_test_{name}_{}_{}",
            std::process::id(),
            COUNTER.fetch_add(1, Ordering::SeqCst)
        ));
        std::fs::create_dir_all(&dir).unwrap();
        Self(dir)
    }

    pub fn path(&self) -> &Path {
        &self.0
    }
}

impl Drop for TempDir {
    fn drop(&mut self) {
        let _ = std::fs::remove_dir_all(&self.0);
    }
}

/// Polls the condition until it holds; panics if it does not within two seconds.
pub fn wait_until(what: &str, mut condition: impl FnMut() -> bool) {
    let deadline = Instant::now() + Duration::from_secs(2);
    while !condition() {
        assert!(
            Instant::now() < deadline,
            "timeout while waiting for: {what}"
        );
        std::thread::sleep(Duration::from_millis(5));
    }
}

pub mod device;

use std::io::{BufRead, BufReader, Read, Write};
use std::net::{TcpListener, TcpStream};
use std::process::{Child, ChildStdin, Command, ExitStatus, Stdio};
use std::sync::{Arc, Mutex};

pub const UB: &str = env!("CARGO_BIN_EXE_ub");

pub fn free_port() -> u16 {
    TcpListener::bind("127.0.0.1:0")
        .unwrap()
        .local_addr()
        .unwrap()
        .port()
}

/// A running `ub` command; its output is collected, it is killed when dropped.
pub struct Ub {
    child: Child,
    pub stdin: Option<ChildStdin>,
    stdout: Arc<Mutex<String>>,
    stderr: Arc<Mutex<String>>,
}

fn collect(stream: impl Read + Send + 'static) -> Arc<Mutex<String>> {
    let text = Arc::new(Mutex::new(String::new()));
    let sink = Arc::clone(&text);
    std::thread::spawn(move || {
        for line in BufReader::new(stream).lines() {
            let Ok(line) = line else { break };
            let mut text = sink.lock().unwrap();
            text.push_str(&line);
            text.push('\n');
        }
    });
    text
}

impl Ub {
    pub fn start(args: &[&str]) -> Self {
        Self::start_with(args, |_| {})
    }

    pub fn start_with(args: &[&str], setup: impl FnOnce(&mut Command)) -> Self {
        let mut command = Command::new(UB);
        command
            .args(args)
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::piped());
        setup(&mut command);
        let mut child = command.spawn().unwrap();
        Self {
            stdin: child.stdin.take(),
            stdout: collect(child.stdout.take().unwrap()),
            stderr: collect(child.stderr.take().unwrap()),
            child,
        }
    }

    pub fn stdout(&self) -> String {
        self.stdout.lock().unwrap().clone()
    }

    pub fn stderr(&self) -> String {
        self.stderr.lock().unwrap().clone()
    }

    pub fn type_line(&mut self, line: &str) {
        let stdin = self.stdin.as_mut().unwrap();
        writeln!(stdin, "{line}").unwrap();
        stdin.flush().unwrap();
    }

    /// Waits until the output contains the text; panics after the timeout.
    pub fn expect_stdout(&self, text: &str) {
        let deadline = Instant::now() + Duration::from_secs(5);
        while !self.stdout().contains(text) {
            assert!(
                Instant::now() < deadline,
                "`{text}` did not appear in the output:\n{}\nstderr:\n{}",
                self.stdout(),
                self.stderr()
            );
            std::thread::sleep(Duration::from_millis(10));
        }
    }

    /// Waits for the exit of the command; panics if it runs longer than the timeout.
    pub fn wait(&mut self, timeout: Duration) -> ExitStatus {
        let deadline = Instant::now() + timeout;
        loop {
            if let Some(status) = self.child.try_wait().unwrap() {
                //let the collectors read what is left in the pipes
                std::thread::sleep(Duration::from_millis(50));
                return status;
            }
            assert!(
                Instant::now() < deadline,
                "command still runs. Output:\n{}\nstderr:\n{}",
                self.stdout(),
                self.stderr()
            );
            std::thread::sleep(Duration::from_millis(10));
        }
    }
}

impl Drop for Ub {
    fn drop(&mut self) {
        let _ = self.child.kill();
        let _ = self.child.wait();
    }
}

/// Runs `ub` with the arguments until it exits; gives the exit code, stdout and stderr.
pub fn run_ub(args: &[&str]) -> (i32, String, String) {
    let mut ub = Ub::start(args);
    ub.stdin = None;
    let status = ub.wait(Duration::from_secs(30));
    (status.code().unwrap_or(-1), ub.stdout(), ub.stderr())
}

pub fn wait_port(port: u16) {
    wait_until("RPC server listening", || {
        TcpStream::connect(("127.0.0.1", port)).is_ok()
    });
}

/// Starts `ub rpcServer` with the arguments on a free port and waits until it listens.
///
/// A port that was free when we looked may be taken by the time the server
/// binds it (the tests run in parallel), then an other port is tried.
pub fn start_server(args: &[&str]) -> (Ub, u16) {
    for _ in 0..10 {
        let port = free_port();
        let port_text = port.to_string();
        let mut all = vec!["rpcServer", "-p", &port_text];
        all.extend_from_slice(args);
        let mut server = Ub::start(&all);

        let deadline = Instant::now() + Duration::from_secs(10);
        while Instant::now() < deadline {
            if server.child.try_wait().unwrap().is_some() {
                //could not bind
                break;
            }
            if TcpStream::connect(("127.0.0.1", port)).is_ok() {
                //the one listening must be our server, not the one that took the port
                std::thread::sleep(Duration::from_millis(20));
                if server.child.try_wait().unwrap().is_none() {
                    return (server, port);
                }
                break;
            }
            std::thread::sleep(Duration::from_millis(5));
        }
    }
    panic!("the RPC server could not be started");
}

/// Starts `ub rpcServer` without gateway on a free port.
pub fn start_dummy_server() -> (Ub, u16) {
    start_server(&["-m", "dummy"])
}
