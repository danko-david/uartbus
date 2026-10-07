//! The server side of the bus access: distributes the packets of the gateway
//! between the sessions (clients) and the packets of the clients to the gateway.

use std::collections::{BTreeMap, VecDeque};
use std::io;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Condvar, Mutex};

use crate::connector::UartbusPacketConnector;
use crate::log::{log, LogLevel};
use crate::rpc::connection::UartbusConnection;

const LOG: &str = "UartbusRpcEndpoint";

pub const ATTR_LOOPBACK_SEND_PACKETS: &str = "loopback_send_packets";
pub const ATTR_ECHO_LOOPBACK: &str = "echo_loopback";

struct QueueState {
    packets: VecDeque<Vec<u8>>,
    closed: bool,
}

/// Packet queue and settings of one client.
pub struct Session {
    echo_loopback: AtomicBool,
    loopback_send_packets: AtomicBool,
    queue: Mutex<QueueState>,
    arrived: Condvar,
}

impl Session {
    fn put(&self, packet: Vec<u8>) {
        let mut queue = self.queue.lock().unwrap();
        if !queue.closed {
            queue.packets.push_back(packet);
            self.arrived.notify_one();
        }
    }

    /// Blocks until a packet arrives; error when the session or the endpoint is closed.
    pub fn take(&self) -> io::Result<Vec<u8>> {
        let mut queue = self.queue.lock().unwrap();
        loop {
            if let Some(packet) = queue.packets.pop_front() {
                return Ok(packet);
            }
            if queue.closed {
                return Err(io::Error::new(
                    io::ErrorKind::UnexpectedEof,
                    "End of receive.",
                ));
            }
            queue = self.arrived.wait(queue).unwrap();
        }
    }

    pub fn close(&self) {
        self.queue.lock().unwrap().closed = true;
        self.arrived.notify_all();
    }

    pub fn get_attribute(&self, key: &str) -> Option<String> {
        match key {
            ATTR_LOOPBACK_SEND_PACKETS => Some(
                self.loopback_send_packets
                    .load(Ordering::SeqCst)
                    .to_string(),
            ),
            ATTR_ECHO_LOOPBACK => Some(self.echo_loopback.load(Ordering::SeqCst).to_string()),
            _ => None,
        }
    }

    pub fn set_attribute(&self, key: &str, value: &str) -> Result<(), String> {
        let target = match key {
            ATTR_LOOPBACK_SEND_PACKETS => &self.loopback_send_packets,
            ATTR_ECHO_LOOPBACK => &self.echo_loopback,
            _ => return Ok(()),
        };

        let set = parse_bool(value)
            .ok_or_else(|| format!("The given value `{value}` can not be casted to boolean."))?;
        log(
            LOG,
            LogLevel::Debug,
            &format!("Set loopback '{key}' to `{set}`"),
        );
        target.store(set, Ordering::SeqCst);
        Ok(())
    }
}

fn parse_bool(value: &str) -> Option<bool> {
    match value.trim().to_ascii_lowercase().as_str() {
        "true" | "1" => Some(true),
        "false" | "0" => Some(false),
        _ => None,
    }
}

pub fn list_attributes() -> BTreeMap<String, String> {
    let mut ret = BTreeMap::new();
    ret.insert(
        ATTR_LOOPBACK_SEND_PACKETS.to_string(),
        "The call of `getNextPacket` functions returns also the you sent with `sendPacket`. Used to implement full network sniffing".to_string(),
    );
    ret
}

struct Inner {
    connector: Box<dyn UartbusPacketConnector>,
    sessions: Mutex<Vec<Arc<Session>>>,
    closed: AtomicBool,
    default_echo_loopback: bool,
    default_loopback_send_packets: bool,
}

#[derive(Clone)]
pub struct UartbusRpcEndpoint {
    inner: Arc<Inner>,
}

impl UartbusRpcEndpoint {
    /// `default_echo_loopback`: sessions get the packets sent by clients (set when a real gateway is attached).
    /// `default_loopback_send_packets`: same effect, this is the one a client usually switches (set in dummy mode).
    pub fn new(
        connector: Box<dyn UartbusPacketConnector>,
        default_echo_loopback: bool,
        default_loopback_send_packets: bool,
    ) -> io::Result<Self> {
        let inner = Arc::new(Inner {
            connector,
            sessions: Mutex::new(Vec::new()),
            closed: AtomicBool::new(false),
            default_echo_loopback,
            default_loopback_send_packets,
        });

        let weak = Arc::downgrade(&inner);
        inner.connector.set_packet_hook(Box::new(move |packet| {
            if let Some(inner) = weak.upgrade() {
                let sessions = inner.sessions.lock().unwrap().clone();
                for session in sessions {
                    session.put(packet.clone());
                }
            }
        }));
        inner.connector.start_listen()?;

        Ok(Self { inner })
    }

    /// Endpoint without gateway: clients see only what the other clients (and themselves) send.
    pub fn new_dummy() -> Self {
        Self::new(Box::new(crate::connector::DummyConnector), false, true)
            .expect("dummy connector can't fail")
    }

    pub fn new_session(&self) -> io::Result<Arc<Session>> {
        if self.inner.closed.load(Ordering::SeqCst) {
            return Err(io::Error::new(
                io::ErrorKind::UnexpectedEof,
                "End of receive.",
            ));
        }

        let session = Arc::new(Session {
            echo_loopback: AtomicBool::new(self.inner.default_echo_loopback),
            loopback_send_packets: AtomicBool::new(self.inner.default_loopback_send_packets),
            queue: Mutex::new(QueueState {
                packets: VecDeque::new(),
                closed: false,
            }),
            arrived: Condvar::new(),
        });
        self.inner
            .sessions
            .lock()
            .unwrap()
            .push(Arc::clone(&session));
        Ok(session)
    }

    pub fn remove_session(&self, session: &Arc<Session>) {
        self.inner
            .sessions
            .lock()
            .unwrap()
            .retain(|s| !Arc::ptr_eq(s, session));
        session.close();
    }

    pub fn session_count(&self) -> usize {
        self.inner.sessions.lock().unwrap().len()
    }

    /// Sends the packet to the gateway and to the sessions that asked for the sent packets.
    ///
    /// Note: like in the Java server, a session with `echo_loopback` gets the
    /// packet even if it is the sender of it.
    pub fn send_packet(&self, data: &[u8]) -> io::Result<()> {
        self.inner.connector.send_packet(data)?;

        let sessions = self.inner.sessions.lock().unwrap().clone();
        for session in sessions {
            if session.loopback_send_packets.load(Ordering::SeqCst)
                || session.echo_loopback.load(Ordering::SeqCst)
            {
                session.put(data.to_vec());
            }
        }

        Ok(())
    }

    /// Closes the gateway connection and wakes every waiting receiver with an error.
    pub fn close(&self) {
        self.inner.closed.store(true, Ordering::SeqCst);
        self.inner.connector.close();
        let sessions = std::mem::take(&mut *self.inner.sessions.lock().unwrap());
        for session in sessions {
            session.close();
        }
    }

    pub fn is_closed(&self) -> bool {
        self.inner.closed.load(Ordering::SeqCst)
    }

    /// A connection that lives in this process (no TCP), with its own session.
    pub fn create_new_connection(&self) -> io::Result<Arc<dyn UartbusConnection>> {
        if self.is_closed() {
            return Err(io::Error::new(
                io::ErrorKind::UnexpectedEof,
                "Distributor closed",
            ));
        }

        Ok(Arc::new(InProcessConnection {
            endpoint: self.clone(),
            session: self.new_session()?,
        }))
    }
}

struct InProcessConnection {
    endpoint: UartbusRpcEndpoint,
    session: Arc<Session>,
}

impl UartbusConnection for InProcessConnection {
    fn init(&self) -> io::Result<()> {
        Ok(())
    }

    fn send_packet(&self, data: &[u8]) -> io::Result<()> {
        self.endpoint.send_packet(data)
    }

    fn list_attributes(&self) -> io::Result<BTreeMap<String, String>> {
        Ok(list_attributes())
    }

    fn get_attribute(&self, key: &str) -> io::Result<Option<String>> {
        Ok(self.session.get_attribute(key))
    }

    fn set_attribute(&self, key: &str, value: &str) -> io::Result<()> {
        self.session
            .set_attribute(key, value)
            .map_err(io::Error::other)
    }

    fn get_next_packet(&self) -> io::Result<Vec<u8>> {
        self.session.take()
    }

    fn close(&self) {
        self.endpoint.remove_session(&self.session);
    }
}

impl Drop for InProcessConnection {
    fn drop(&mut self) {
        self.endpoint.remove_session(&self.session);
    }
}
