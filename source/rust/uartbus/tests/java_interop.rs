//! The Rust and the Java tools must work with each other in every combination.
//!
//! These tests need the Java implementation, so they do not run by default:
//!   UB_TEST_JAVA: the java executable (Java 8, the Java tools do not start on newer ones)
//!   UB_TEST_JAR: the built source/java/uartbus/target/uartbus-jar-with-dependencies.jar
//! Run: cargo test --test java_interop -- --ignored

mod common;

use std::path::Path;
use std::process::{Child, Command, Stdio};
use std::time::Duration;

use common::device::{SimulatedDevice, APP_START_ADDRESS};
use common::{free_port, run_ub, start_dummy_server, wait_port, TempDir};
use uartbus::ihex::IntelHexFile;

fn java(args: &[&str]) -> Command {
    let java = std::env::var("UB_TEST_JAVA").expect("UB_TEST_JAVA is not set");
    let jar = std::env::var("UB_TEST_JAR").expect("UB_TEST_JAR is not set");
    let mut command = Command::new(java);
    command.arg("-jar").arg(jar).args(args);
    command
}

/// Runs a Java tool until it exits (they call exit when done); gives its stdout.
fn run_java(args: &[&str]) -> (i32, String) {
    let output = java(args).stderr(Stdio::inherit()).output().unwrap();
    (
        output.status.code().unwrap_or(-1),
        String::from_utf8_lossy(&output.stdout).into_owned(),
    )
}

struct KillOnDrop(Child);

impl Drop for KillOnDrop {
    fn drop(&mut self) {
        let _ = self.0.kill();
        let _ = self.0.wait();
    }
}

fn sample_code(length: usize) -> Vec<u8> {
    (0..length).map(|i| (i * 7 + 13) as u8).collect()
}

fn write_hex(path: &Path, code: &[u8]) {
    let mut file = IntelHexFile::new();
    for (i, chunk) in code.chunks(16).enumerate() {
        file.push_line(
            file.create_data_line(u32::from(APP_START_ADDRESS) + (i * 16) as u32, chunk),
        );
    }
    file.push_line(file.create_eof_line());
    file.write_to_file(path).unwrap();
}

#[test]
#[ignore = "needs Java 8 and the built Java jar"]
fn java_commands_work_through_the_rust_server() {
    let (_server, port) = start_dummy_server();
    let device = SimulatedDevice::attach(port, 4);
    let port = port.to_string();

    let (exit, stdout) = run_java(&["packetloss", "-p", &port, "-t", "4", "-c", "2", "-i", "0"]);
    assert_eq!(exit, 0);
    assert!(stdout.contains("0. pong\n1. pong\n"), "{stdout}");
    assert!(stdout.contains("\tPacket loss ratio: 0 %\n"), "{stdout}");

    let dir = TempDir::new("java_upload");
    let hex = dir.path().join("app.hex");
    let code = sample_code(100);
    write_hex(&hex, &code);

    let (exit, stdout) = run_java(&[
        "upload",
        "-p",
        &port,
        "-t",
        "4",
        "-c",
        hex.to_str().unwrap(),
    ]);
    assert_eq!(exit, 0, "{stdout}");
    assert_eq!(device.application_code(code.len()), code);
    assert!(device.state.lock().unwrap().app_run);
}

#[test]
#[ignore = "needs Java 8 and the built Java jar"]
fn rust_commands_work_through_the_java_server() {
    let port = free_port();
    let port_text = port.to_string();
    let _server = KillOnDrop(
        java(&["rpcServer", "-m", "dummy", "-p", &port_text])
            .stdout(Stdio::null())
            .spawn()
            .unwrap(),
    );
    //the JVM needs its time
    std::thread::sleep(Duration::from_millis(500));
    wait_port(port);

    let device = SimulatedDevice::attach(port, 4);

    let (exit, stdout, _) = run_ub(&[
        "packetloss",
        "-p",
        &port_text,
        "-t",
        "4",
        "-c",
        "2",
        "-i",
        "0",
    ]);
    assert_eq!(exit, 0);
    assert!(stdout.starts_with("0. pong\n1. pong\n"), "{stdout}");

    let dir = TempDir::new("rust_upload_java_server");
    let hex = dir.path().join("app.hex");
    let code = sample_code(100);
    write_hex(&hex, &code);

    let (exit, stdout, stderr) = run_ub(&[
        "upload",
        "-p",
        &port_text,
        "-t",
        "4",
        "-c",
        hex.to_str().unwrap(),
    ]);
    assert_eq!(exit, 0, "{stdout}\n{stderr}");
    assert_eq!(device.application_code(code.len()), code);
}

#[test]
#[ignore = "needs Java 8 and the built Java jar"]
fn rust_and_java_upload_print_the_same_steps() {
    let dir = TempDir::new("upload_compare");
    let hex = dir.path().join("app.hex");
    write_hex(&hex, &sample_code(70));

    //the messages of the upload without the time, process and logger columns
    let steps = |output: &str| -> Vec<String> {
        output
            .lines()
            .filter(|line| line.contains("{UartbusCodeUploader}"))
            .map(|line| line.rsplit('\t').next().unwrap().to_string())
            .collect()
    };

    let (_server, port) = start_dummy_server();
    let port = port.to_string();
    let args = [
        "upload",
        "-p",
        &port,
        "-t",
        "4",
        "-c",
        hex.to_str().unwrap(),
    ];

    let rust_steps = {
        let _device = SimulatedDevice::attach(port.parse().unwrap(), 4);
        let (exit, stdout, _) = run_ub(&args);
        assert_eq!(exit, 0);
        steps(&stdout)
    };

    let java_steps = {
        let _device = SimulatedDevice::attach(port.parse().unwrap(), 4);
        let (exit, stdout) = run_java(&args);
        assert_eq!(exit, 0);
        steps(&stdout)
    };

    assert!(rust_steps.len() > 10, "{rust_steps:?}");
    assert_eq!(rust_steps, java_steps);
}
