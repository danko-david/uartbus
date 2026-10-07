//! Client view of the bus: sends requests and pairs the received packets with the waiting requests.

use std::fmt;
use std::io;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Condvar, Mutex};
use std::time::{Duration, Instant};

use crate::log::{log, LogLevel};
use crate::packet::ParsedUartBusPacket;
use crate::rpc::device::UartBusDevice;
use crate::rpc::streamer::{open_ip_endpoint, ConnectionFactory, UartbusStreamerEndpoint};
use crate::rpc::tools::{to_packet, RpcValue};
use crate::tools::get_valid_packet;

const LOG: &str = "UartBus";

pub type PacketSender = Box<dyn Fn(&[u8]) -> io::Result<()> + Send + Sync>;
pub type PacketListener = Arc<dyn Fn(&[u8]) + Send + Sync>;

/// The device did not answer in time.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TransactionError(pub String);

impl fmt::Display for TransactionError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

impl std::error::Error for TransactionError {}

struct TransactionState {
    to: i32,
    from: i32,
    path: Vec<u8>,
    zero_namespace_answer: bool,
    response: Mutex<Option<Vec<u8>>>,
    responded: Condvar,
}

impl TransactionState {
    /// Is this packet the answer: sent between the two addresses in reverse
    /// and its payload starts with (0 and) the path of the request.
    fn try_accept_response(&self, packet: &ParsedUartBusPacket) -> bool {
        if packet.to != self.from || packet.from != self.to {
            return false;
        }

        let diff = usize::from(self.zero_namespace_answer);
        if packet.payload.len() < self.path.len() + diff {
            return false;
        }

        if self.zero_namespace_answer && packet.payload[0] != 0 {
            return false;
        }

        if packet.payload[diff..diff + self.path.len()] != self.path[..] {
            return false;
        }

        *self.response.lock().unwrap() = Some(packet.payload.clone());
        self.responded.notify_all();
        true
    }
}

/// A request and its awaited response. Stops waiting for the response when dropped.
pub struct UartbusTransaction {
    bus: UartBus,
    state: Arc<TransactionState>,
}

impl UartbusTransaction {
    pub fn is_responsed(&self) -> bool {
        self.state.response.lock().unwrap().is_some()
    }

    /// The payload of the response, or None if it did not arrive in time.
    pub fn wait_response(&self, timeout: Duration) -> Option<Vec<u8>> {
        let deadline = Instant::now() + timeout;
        let mut response = self.state.response.lock().unwrap();
        loop {
            if let Some(payload) = response.as_ref() {
                return Some(payload.clone());
            }
            let now = Instant::now();
            if now >= deadline {
                return None;
            }
            response = self
                .state
                .responded
                .wait_timeout(response, deadline - now)
                .unwrap()
                .0;
        }
    }

    pub fn ensure_response(
        &self,
        timeout: Duration,
        err_append_msg: Option<&str>,
    ) -> Result<Vec<u8>, TransactionError> {
        self.wait_response(timeout).ok_or_else(|| {
            TransactionError(format!(
                "Device (`{}`) not responded within {} MILLISECONDS for the request{}",
                self.state.to,
                timeout.as_millis(),
                err_append_msg.map_or(String::new(), |msg| format!(": {msg}"))
            ))
        })
    }
}

impl Drop for UartbusTransaction {
    fn drop(&mut self) {
        self.bus.revoke_pending_request(&self.state);
    }
}

struct Inner {
    from_address: i32,
    send_packet: PacketSender,
    stream: Option<UartbusStreamerEndpoint>,
    pending_requests: Mutex<Vec<Arc<TransactionState>>>,
    next_listener_id: AtomicU64,
    unrelated_packets: Mutex<Vec<(u64, PacketListener)>>,
    invalid_packets: Mutex<Vec<(u64, PacketListener)>>,
}

#[derive(Clone)]
pub struct UartBus {
    inner: Arc<Inner>,
}

impl UartBus {
    /// A bus that sends with the given function. Received packets must be fed to `process_packet`.
    pub fn new(send_packet: PacketSender, from_address: i32) -> Self {
        Self::build(send_packet, None, from_address)
    }

    fn build(
        send_packet: PacketSender,
        stream: Option<UartbusStreamerEndpoint>,
        from_address: i32,
    ) -> Self {
        Self {
            inner: Arc::new(Inner {
                from_address,
                send_packet,
                stream,
                pending_requests: Mutex::new(Vec::new()),
                next_listener_id: AtomicU64::new(0),
                unrelated_packets: Mutex::new(Vec::new()),
                invalid_packets: Mutex::new(Vec::new()),
            }),
        }
    }

    fn from_stream(stream: UartbusStreamerEndpoint, from_address: i32) -> io::Result<Self> {
        let sender = stream.clone();
        let bus = Self::build(
            Box::new(move |data| sender.send_packet(data)),
            Some(stream.clone()),
            from_address,
        );

        //the stream must not keep the bus alive, otherwise it never stops
        let weak = Arc::downgrade(&bus.inner);
        stream.start_streaming(move |packet| {
            if let Some(inner) = weak.upgrade() {
                UartBus { inner }.process_packet(packet);
            }
        })?;
        Ok(bus)
    }

    /// Bus through the RPC server; waits for the server if it is not available.
    pub fn from_tcp(host: &str, port: u16, from_address: i32) -> io::Result<Self> {
        Self::from_stream(open_ip_endpoint(host, port, None, true), from_address)
    }

    pub fn from_connection(factory: ConnectionFactory, from_address: i32) -> io::Result<Self> {
        Self::from_stream(UartbusStreamerEndpoint::new(factory), from_address)
    }

    pub fn get_from_address(&self) -> i32 {
        self.inner.from_address
    }

    pub fn device(&self, address: i32) -> UartBusDevice {
        UartBusDevice::new(self.clone(), address)
    }

    pub fn send_raw_packet(&self, data: &[u8]) -> io::Result<()> {
        (self.inner.send_packet)(data)
    }

    fn add_listener(
        &self,
        list: &Mutex<Vec<(u64, PacketListener)>>,
        listener: PacketListener,
    ) -> u64 {
        let id = self.inner.next_listener_id.fetch_add(1, Ordering::SeqCst);
        list.lock().unwrap().push((id, listener));
        id
    }

    /// Listener of the valid packets that are not answers of a waiting request (without crc8).
    pub fn add_unrelated_packet_listener(&self, listener: PacketListener) -> u64 {
        self.add_listener(&self.inner.unrelated_packets, listener)
    }

    pub fn remove_unrelated_packet_listener(&self, id: u64) {
        self.inner
            .unrelated_packets
            .lock()
            .unwrap()
            .retain(|(lid, _)| *lid != id);
    }

    /// Listener of the packets with wrong crc8.
    pub fn add_invalid_packet_listener(&self, listener: PacketListener) -> u64 {
        self.add_listener(&self.inner.invalid_packets, listener)
    }

    fn dispatch(list: &Mutex<Vec<(u64, PacketListener)>>, packet: &[u8]) {
        //listeners may use the bus, so they are called without holding the lock
        let listeners: Vec<PacketListener> = list
            .lock()
            .unwrap()
            .iter()
            .map(|(_, l)| Arc::clone(l))
            .collect();
        for listener in listeners {
            listener(packet);
        }
    }

    /// Handles a packet received from the bus (with crc8).
    pub fn process_packet(&self, packet: &[u8]) {
        let Some(data) = get_valid_packet(packet) else {
            Self::dispatch(&self.inner.invalid_packets, packet);
            return;
        };

        let parsed = match ParsedUartBusPacket::new(data) {
            Ok(parsed) => parsed,
            Err(err) => {
                log(
                    LOG,
                    LogLevel::Notice,
                    &format!("Exception while parsing packet: {err}"),
                );
                return;
            }
        };

        {
            let mut pending = self.inner.pending_requests.lock().unwrap();
            if let Some(index) = pending.iter().position(|t| t.try_accept_response(&parsed)) {
                pending.remove(index);
                return;
            }
        }

        Self::dispatch(&self.inner.unrelated_packets, &parsed.raw_packet);
    }

    fn revoke_pending_request(&self, state: &Arc<TransactionState>) {
        self.inner
            .pending_requests
            .lock()
            .unwrap()
            .retain(|t| !Arc::ptr_eq(t, state));
    }

    fn register(
        &self,
        from: i32,
        to: i32,
        path: &[u8],
        zero_namespace_answer: bool,
    ) -> UartbusTransaction {
        let state = Arc::new(TransactionState {
            to,
            from,
            path: path.to_vec(),
            zero_namespace_answer,
            response: Mutex::new(None),
            responded: Condvar::new(),
        });
        self.inner
            .pending_requests
            .lock()
            .unwrap()
            .push(Arc::clone(&state));
        UartbusTransaction {
            bus: self.clone(),
            state,
        }
    }

    /// Sends `payload` to the device and waits for an answer that starts with `path`
    /// (after a 0 byte, if the device answers in the zero namespace).
    pub fn new_transaction(
        &self,
        to: i32,
        path: &[u8],
        payload: &[u8],
        zero_namespace_answer: bool,
    ) -> io::Result<UartbusTransaction> {
        let packet = to_packet(
            to,
            self.inner.from_address,
            &[RpcValue::Bytes(payload.to_vec())],
        )
        .map_err(io::Error::other)?;

        //registered before sending, the answer may arrive at once
        let transaction = self.register(self.inner.from_address, to, path, zero_namespace_answer);
        self.send_raw_packet(&packet)?;
        Ok(transaction)
    }

    /// Waits for a packet without sending anything: `to` is the address the
    /// awaited packet comes from, `from` is the address it is sent to.
    pub fn subscribe_response(
        &self,
        from: i32,
        to: i32,
        path: &[u8],
        zero_namespace: bool,
    ) -> UartbusTransaction {
        self.register(from, to, path, zero_namespace)
    }

    pub fn close(&self) {
        if let Some(stream) = &self.inner.stream {
            stream.close();
        }
    }
}
