//! TCP client of the bus access RPC server, see `rpc::server` for the protocol.

use std::collections::{BTreeMap, HashMap};
use std::io::{self, BufRead, BufReader, Write};
use std::net::{Shutdown, TcpStream};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::mpsc;
use std::sync::{Arc, Mutex};
use std::thread;

use serde_json::{json, Value};

use crate::base64;
use crate::rpc::connection::UartbusConnection;
use crate::rpc::server::NAMESPACE;

struct Pending {
    waiting: HashMap<u64, mpsc::Sender<Value>>,
    closed: bool,
}

/// Any number of calls may wait for response at the same time (a blocked
/// `get_next_packet` does not hold up `send_packet`).
pub struct TcpRpcClient {
    stream: Mutex<TcpStream>,
    pending: Arc<Mutex<Pending>>,
    next_tid: AtomicU64,
}

fn response_tid(response: &Value) -> Option<u64> {
    match response.get("t")? {
        Value::String(s) => s.parse().ok(),
        other => other.as_u64(),
    }
}

impl TcpRpcClient {
    pub fn connect(host: &str, port: u16) -> io::Result<Self> {
        let stream = TcpStream::connect((host, port))?;
        let _ = stream.set_nodelay(true);
        let reader = BufReader::new(stream.try_clone()?);

        let pending = Arc::new(Mutex::new(Pending {
            waiting: HashMap::new(),
            closed: false,
        }));

        let reader_pending = Arc::clone(&pending);
        thread::Builder::new()
            .name("ub-rpc-rx".into())
            .spawn(move || {
                for line in reader.lines() {
                    let Ok(line) = line else { break };
                    let Ok(response) = serde_json::from_str::<Value>(&line) else {
                        break;
                    };

                    let waiter = response_tid(&response)
                        .and_then(|tid| reader_pending.lock().unwrap().waiting.remove(&tid));

                    match waiter {
                        Some(waiter) => {
                            let _ = waiter.send(response);
                        }
                        None => eprintln!("UNHANDLED: {response}"),
                    }
                }

                //dropping the senders wakes every waiting call with an error
                let mut pending = reader_pending.lock().unwrap();
                pending.closed = true;
                pending.waiting.clear();
            })?;

        Ok(Self {
            stream: Mutex::new(stream),
            pending,
            next_tid: AtomicU64::new(0),
        })
    }

    pub fn call(&self, function: &str, params: Value) -> io::Result<Value> {
        let closed = || io::Error::new(io::ErrorKind::UnexpectedEof, "RPC connection closed");

        let tid = self.next_tid.fetch_add(1, Ordering::SeqCst) + 1;
        let (tx, rx) = mpsc::channel();
        {
            let mut pending = self.pending.lock().unwrap();
            if pending.closed {
                return Err(closed());
            }
            pending.waiting.insert(tid, tx);
        }

        let mut line = serde_json::to_vec(&json!({
            "N": NAMESPACE,
            "t": tid,
            "f": function,
            "p": params,
        }))
        .map_err(io::Error::other)?;
        line.push(b'\n');

        let written = {
            let mut stream = self.stream.lock().unwrap();
            stream.write_all(&line).and_then(|_| stream.flush())
        };
        if let Err(err) = written {
            self.pending.lock().unwrap().waiting.remove(&tid);
            return Err(err);
        }

        let response = rx.recv().map_err(|_| closed())?;

        if let Some(error) = response.get("e").filter(|e| !e.is_null()) {
            let kind = error.get("type").and_then(Value::as_str).unwrap_or("Error");
            let message = error.get("message").and_then(Value::as_str).unwrap_or("");
            return Err(io::Error::other(format!("{kind}: {message}")));
        }

        Ok(response.get("r").cloned().unwrap_or(Value::Null))
    }
}

impl UartbusConnection for TcpRpcClient {
    fn init(&self) -> io::Result<()> {
        self.call("init", json!([])).map(|_| ())
    }

    fn send_packet(&self, data: &[u8]) -> io::Result<()> {
        self.call("sendPacket", json!([base64::encode(data)]))
            .map(|_| ())
    }

    fn list_attributes(&self) -> io::Result<BTreeMap<String, String>> {
        let mut ret = BTreeMap::new();
        if let Value::Object(map) = self.call("listAttributes", json!([]))? {
            for (key, value) in map {
                ret.insert(key, value.as_str().unwrap_or("").to_string());
            }
        }
        Ok(ret)
    }

    fn get_attribute(&self, key: &str) -> io::Result<Option<String>> {
        Ok(self
            .call("getAttribute", json!([key]))?
            .as_str()
            .map(str::to_string))
    }

    fn set_attribute(&self, key: &str, value: &str) -> io::Result<()> {
        self.call("setAttribute", json!([key, value])).map(|_| ())
    }

    fn get_next_packet(&self) -> io::Result<Vec<u8>> {
        match self.call("getNextPacket", json!([]))? {
            Value::String(packet) => base64::decode(&packet).map_err(io::Error::other),
            other => Err(io::Error::other(format!(
                "getNextPacket returned something that is not a packet: {other}"
            ))),
        }
    }

    fn close(&self) {
        let _ = self.stream.lock().unwrap().shutdown(Shutdown::Both);
    }
}

impl Drop for TcpRpcClient {
    fn drop(&mut self) {
        UartbusConnection::close(self);
    }
}
