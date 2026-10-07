//! Keeps a connection to the RPC server and turns the packet polling into a stream of packets.

use std::io;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::thread::{self, JoinHandle};
use std::time::Duration;

use crate::log::{log, LogLevel};
use crate::rpc::client::TcpRpcClient;
use crate::rpc::connection::UartbusConnection;

const LOG: &str = "UartbusStreamerEndpoint";

pub type SharedConnection = Arc<dyn UartbusConnection>;
pub type ConnectionFactory = Box<dyn Fn() -> io::Result<SharedConnection> + Send + Sync>;
/// Called on every new connection before use, eg. to set attributes.
pub type ConnectionInitializer = Box<dyn Fn(&dyn UartbusConnection) + Send + Sync>;

pub const RECONNECT_TIMES_MS: [u64; 7] = [100, 200, 500, 1_000, 2_000, 5_000, 10_000];

struct Inner {
    factory: ConnectionFactory,
    working: Mutex<Option<SharedConnection>>,
    run: AtomicBool,
    streamer: Mutex<Option<JoinHandle<()>>>,
}

#[derive(Clone)]
pub struct UartbusStreamerEndpoint {
    inner: Arc<Inner>,
}

impl UartbusStreamerEndpoint {
    pub fn new(factory: ConnectionFactory) -> Self {
        Self {
            inner: Arc::new(Inner {
                factory,
                working: Mutex::new(None),
                run: AtomicBool::new(false),
                streamer: Mutex::new(None),
            }),
        }
    }

    /// The working connection; opens (and initialises) one if there is none.
    pub fn get_api(&self) -> io::Result<SharedConnection> {
        let mut working = self.inner.working.lock().unwrap();
        if let Some(connection) = working.as_ref() {
            return Ok(Arc::clone(connection));
        }

        let connection = (self.inner.factory)()?;
        if let Err(err) = connection.init() {
            log(
                "UartbusTools",
                LogLevel::Notice,
                &format!("Exception while calling init(). This might happened beacuse you connect to and older version of connection implementation: {err}"),
            );
        }
        *working = Some(Arc::clone(&connection));
        Ok(connection)
    }

    pub fn send_packet(&self, data: &[u8]) -> io::Result<()> {
        self.get_api()?.send_packet(data)
    }

    /// Forgets the working connection, the next use opens a new one.
    pub fn cleanup_connection(&self) {
        if let Some(connection) = self.inner.working.lock().unwrap().take() {
            connection.close();
        }
    }

    /// Connects, then delivers every received packet to the listener on a background thread.
    pub fn start_streaming(
        &self,
        mut listener: impl FnMut(&[u8]) + Send + 'static,
    ) -> io::Result<()> {
        let mut streamer = self.inner.streamer.lock().unwrap();
        if streamer.is_some() {
            return Err(io::Error::new(
                io::ErrorKind::AlreadyExists,
                "Packet streaming already running",
            ));
        }

        //the caller may send right after this returns, the connection must be registered by then
        self.get_api()?;
        self.inner.run.store(true, Ordering::SeqCst);

        let this = self.clone();
        *streamer = Some(
            thread::Builder::new()
                .name("ub-packet-stream".into())
                .spawn(move || {
                    while this.inner.run.load(Ordering::SeqCst) {
                        let connection = match this.get_api() {
                            Ok(connection) => connection,
                            Err(err) => {
                                if this.inner.run.load(Ordering::SeqCst) {
                                    log(
                                        LOG,
                                        LogLevel::Fatal,
                                        &format!("Exception occurred while getting connection for packet streaming (stopping): {err}"),
                                    );
                                }
                                break;
                            }
                        };

                        match connection.get_next_packet() {
                            Ok(packet) => listener(&packet),
                            Err(err) => {
                                if !this.inner.run.load(Ordering::SeqCst) {
                                    break;
                                }
                                log(
                                    LOG,
                                    LogLevel::Error,
                                    &format!("Exception while receiving packet (continue): {err}"),
                                );
                                this.cleanup_connection();
                            }
                        }
                    }
                    this.inner.run.store(false, Ordering::SeqCst);
                })?,
        );

        Ok(())
    }

    /// Blocks until the packet streaming ends (connection lost and not reopened, or closed).
    pub fn join(&self) {
        let handle = self.inner.streamer.lock().unwrap().take();
        if let Some(handle) = handle {
            let _ = handle.join();
        }
    }

    pub fn close(&self) {
        self.inner.run.store(false, Ordering::SeqCst);
        self.cleanup_connection();
    }
}

/// Calls `connect` until it succeeds, waiting the given times between the
/// tries (the last one repeats forever).
pub fn wait_reconnect<T>(
    connect: impl Fn() -> io::Result<T>,
    entity: &str,
    wait_times_ms: &[u64],
) -> io::Result<T> {
    let mut attempt = 0usize;
    loop {
        match connect() {
            Ok(connection) => return Ok(connection),
            Err(err) => {
                let Some(wait) = wait_times_ms
                    .get(attempt.min(wait_times_ms.len().saturating_sub(1)))
                    .copied()
                else {
                    return Err(err);
                };

                log(
                    "RetryTools",
                    LogLevel::Warning,
                    &format!("Can't open `{entity}`, waiting `{wait}` millisec before trying reconnect again. {err}"),
                );
                thread::sleep(Duration::from_millis(wait));
                attempt += 1;
            }
        }
    }
}

/// Endpoint of an RPC server on TCP. With `reconnect` a lost (or not yet
/// available) server is waited for, otherwise it is an error.
pub fn open_ip_endpoint(
    host: &str,
    port: u16,
    initializer: Option<ConnectionInitializer>,
    reconnect: bool,
) -> UartbusStreamerEndpoint {
    let host = host.to_string();

    UartbusStreamerEndpoint::new(Box::new(move || {
        let connect = || -> io::Result<SharedConnection> {
            let client = TcpRpcClient::connect(&host, port)?;
            if let Some(initializer) = &initializer {
                initializer(&client);
            }
            Ok(Arc::new(client))
        };

        if reconnect {
            wait_reconnect(
                connect,
                &format!("Uartbus API connection to {host}:{port}"),
                &RECONNECT_TIMES_MS,
            )
        } else {
            connect()
        }
    }))
}
