//! Connection between the RPC server and the bus gateway.
//!
//! The gateway is reached through a child process (`socat` on a serial device
//! by default), packets travel on its stdin/stdout with escaped framing.

use std::io::{self, Read, Write};
use std::process::{Child, Command, Stdio};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::Duration;

use crate::framing::{frame_bytes, FramedPacketCutter};
use crate::log::{log, may_log, LogLevel};
use crate::tools::format_colon_data;

const LOG: &str = "UartbusPacketConnector";

pub type PacketHook = Box<dyn Fn(Vec<u8>) + Send + Sync>;

pub trait UartbusPacketConnector: Send + Sync {
    fn set_packet_hook(&self, hook: PacketHook);
    fn send_packet(&self, data: &[u8]) -> io::Result<()>;
    fn start_listen(&self) -> io::Result<()>;
    fn close(&self);
}

/// Connector of the `dummy` server mode: nothing is attached, sent packets vanish.
pub struct DummyConnector;

impl UartbusPacketConnector for DummyConnector {
    fn set_packet_hook(&self, _hook: PacketHook) {}

    fn send_packet(&self, _data: &[u8]) -> io::Result<()> {
        Ok(())
    }

    fn start_listen(&self) -> io::Result<()> {
        Ok(())
    }

    fn close(&self) {}
}

/// Two directions of a byte stream, with the child process that provides them (if any).
pub struct IoStream {
    pub reader: Box<dyn Read + Send>,
    pub writer: Box<dyn Write + Send>,
    pub child: Option<Child>,
}

pub type IoStreamFactory = Box<dyn Fn() -> io::Result<IoStream> + Send + Sync>;

/// What to do when the stream breaks.
pub enum OnStreamClosed {
    /// Open a new stream with the factory and continue.
    Reconnect,
    /// Call this (the RPC server exits with status 5).
    Call(Box<dyn Fn() + Send + Sync>),
}

struct IoState {
    generation: u64,
    reader: Option<Box<dyn Read + Send>>,
    writer: Box<dyn Write + Send>,
    child: Option<Child>,
}

struct Inner {
    escape: u8,
    factory: IoStreamFactory,
    on_closed: OnStreamClosed,
    io: Mutex<IoState>,
    hook: Mutex<Option<PacketHook>>,
    run: AtomicBool,
}

#[derive(Clone)]
pub struct EscapedStreamPacketConnector {
    inner: Arc<Inner>,
}

impl EscapedStreamPacketConnector {
    /// Opens the first stream with the factory.
    pub fn new(
        factory: IoStreamFactory,
        escape: u8,
        on_closed: OnStreamClosed,
    ) -> io::Result<Self> {
        let io = factory()?;
        Ok(Self {
            inner: Arc::new(Inner {
                escape,
                factory,
                on_closed,
                io: Mutex::new(IoState {
                    generation: 0,
                    reader: Some(io.reader),
                    writer: io.writer,
                    child: io.child,
                }),
                hook: Mutex::new(None),
                run: AtomicBool::new(false),
            }),
        })
    }

    pub fn send_with_crc8(&self, data: &[u8]) -> io::Result<()> {
        let mut packet = data.to_vec();
        packet.push(crate::tools::crc8(data));
        self.send_packet(&packet)
    }
}

impl Inner {
    fn dispatch_packet(&self, data: Vec<u8>) {
        if may_log(LogLevel::Debug) {
            log(
                LOG,
                LogLevel::Debug,
                &format!("dispaching packet: {}", format_colon_data(&data)),
            );
        }

        if let Some(hook) = self.hook.lock().unwrap().as_ref() {
            hook(data);
        }
    }

    /// Handles the stream of the given generation being broken.
    /// Returns false if the connector can not continue.
    fn manage_closed_connection(&self, generation: u64, cause: &str, while_op: &str) -> bool {
        log(
            LOG,
            LogLevel::Warning,
            &format!("Communication process broken while: `{while_op}` : {cause}"),
        );

        let mut io = self.io.lock().unwrap();
        if io.generation != generation {
            //somebody else already replaced the broken stream
            return true;
        }

        match &self.on_closed {
            OnStreamClosed::Call(call) => {
                call();
                false
            }
            OnStreamClosed::Reconnect => {
                log(LOG, LogLevel::Error, "Socket error, reconnecting");
                if let Some(mut child) = io.child.take() {
                    let _ = child.kill();
                    let _ = child.wait();
                }

                match (self.factory)() {
                    Ok(new_io) => {
                        io.generation += 1;
                        io.reader = Some(new_io.reader);
                        io.writer = new_io.writer;
                        io.child = new_io.child;
                        true
                    }
                    Err(err) => {
                        log(
                            LOG,
                            LogLevel::Fatal,
                            &format!("Can't reopen the communication process: {err}"),
                        );
                        false
                    }
                }
            }
        }
    }

    fn receive_loop(&self) {
        let mut buffer = [0u8; 10240];
        let mut cutter = FramedPacketCutter::new(self.escape);

        while self.run.load(Ordering::SeqCst) {
            let (generation, reader) = {
                let mut io = self.io.lock().unwrap();
                (io.generation, io.reader.take())
            };

            let Some(mut reader) = reader else {
                //the reader of this generation is already consumed
                thread::sleep(Duration::from_millis(100));
                continue;
            };

            cutter.clear();
            let cause = loop {
                match reader.read(&mut buffer) {
                    Ok(0) => break "end of stream".to_string(),
                    Ok(read) => cutter.feed_bytes(&buffer[..read], |p| self.dispatch_packet(p)),
                    Err(err) if err.kind() == io::ErrorKind::Interrupted => continue,
                    Err(err) => break err.to_string(),
                }
            };

            if !self.run.load(Ordering::SeqCst) {
                break;
            }

            if !self.manage_closed_connection(generation, &cause, "reading packet") {
                log(
                    LOG,
                    LogLevel::Warning,
                    "Communication process closed and no reconnection was set.",
                );
                break;
            }
        }
    }
}

impl UartbusPacketConnector for EscapedStreamPacketConnector {
    fn set_packet_hook(&self, hook: PacketHook) {
        *self.inner.hook.lock().unwrap() = Some(hook);
    }

    fn send_packet(&self, data: &[u8]) -> io::Result<()> {
        let framed = frame_bytes(data, self.inner.escape);

        loop {
            if !self.inner.run.load(Ordering::SeqCst) {
                return Err(io::Error::new(
                    io::ErrorKind::NotConnected,
                    "This UartbusPacketConnector is not running, can't send packet to the network. Call start_listen() to run this instance.",
                ));
            }

            let (generation, result) = {
                let mut io = self.inner.io.lock().unwrap();
                let result = io.writer.write_all(&framed).and_then(|_| io.writer.flush());
                (io.generation, result)
            };

            match result {
                Ok(()) => return Ok(()),
                Err(err) => {
                    let cause = err.to_string();
                    if !self.inner.manage_closed_connection(
                        generation,
                        &cause,
                        "writing framed packet",
                    ) {
                        return Err(err);
                    }
                    thread::sleep(Duration::from_millis(100));
                }
            }
        }
    }

    fn start_listen(&self) -> io::Result<()> {
        if self.inner.run.swap(true, Ordering::SeqCst) {
            return Err(io::Error::new(
                io::ErrorKind::AlreadyExists,
                "Listening already started",
            ));
        }

        let inner = Arc::clone(&self.inner);
        thread::Builder::new()
            .name("ub-gateway-rx".into())
            .spawn(move || inner.receive_loop())?;
        Ok(())
    }

    fn close(&self) {
        self.inner.run.store(false, Ordering::SeqCst);
        if let Some(mut child) = self.inner.io.lock().unwrap().child.take() {
            let _ = child.kill();
            let _ = child.wait();
        }
    }
}

/// Starts a process and uses its stdin/stdout as the stream. Stderr is inherited.
pub fn open_communicator_process(program: &str, args: &[String]) -> io::Result<IoStream> {
    let mut child = Command::new(program)
        .args(args)
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::inherit())
        .spawn()
        .map_err(|err| io::Error::new(err.kind(), format!("can't start `{program}`: {err}")))?;

    let reader = child.stdout.take().expect("piped stdout");
    let writer = child.stdin.take().expect("piped stdin");

    Ok(IoStream {
        reader: Box::new(reader),
        writer: Box::new(writer),
        child: Some(child),
    })
}

/// Arguments of `socat` that connect a serial device to stdin/stdout.
pub fn serial_socat_args(device: &str, baud: u32) -> Vec<String> {
    vec![format!("{device},b{baud},raw,echo=0"), "-".to_string()]
}

pub fn open_serial(device: &str, baud: u32) -> io::Result<IoStream> {
    open_communicator_process("socat", &serial_socat_args(device, baud))
}

/// Uses a custom program instead of socat, started as: `program device baud`
pub fn open_direct_serial(program: &str, device: &str, baud: u32) -> io::Result<IoStream> {
    open_communicator_process(program, &[device.to_string(), baud.to_string()])
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn serial_device_is_opened_raw_without_echo_at_the_given_baud() {
        assert_eq!(
            serial_socat_args("/dev/ttyUSB0", 115200),
            vec!["/dev/ttyUSB0,b115200,raw,echo=0", "-"]
        );
    }
}
