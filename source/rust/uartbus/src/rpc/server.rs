//! TCP server of the bus access: one JSON object per line in both directions.
//!
//! Request: `{"N":"uartbus","t":<id>,"f":"<function>","p":[<parameters>]}`
//! Response: `{"r":<result>,"t":"<id>"}` or `{"e":{"type":..,"message":..,"detail":..},"t":"<id>"}`
//! Packets are base64 strings. The functions are the ones of `UartbusConnection`.

use std::io::{self, BufRead, BufReader, Write};
use std::net::{TcpListener, TcpStream};
use std::sync::mpsc;
use std::sync::{Arc, Mutex};
use std::thread;

use serde_json::{json, Map, Value};

use crate::base64;
use crate::rpc::endpoint::{list_attributes, Session, UartbusRpcEndpoint};

pub const NAMESPACE: &str = "uartbus";

type SharedWriter = Arc<Mutex<TcpStream>>;

fn send_line(writer: &SharedWriter, value: &Value) -> io::Result<()> {
    let mut line = serde_json::to_vec(value).map_err(io::Error::other)?;
    line.push(b'\n');
    let mut stream = writer.lock().unwrap();
    stream.write_all(&line)?;
    stream.flush()
}

fn with_trace(mut response: Map<String, Value>, trace: &Option<String>) -> Value {
    if let Some(trace) = trace {
        response.insert("t".into(), Value::String(trace.clone()));
    }
    Value::Object(response)
}

pub fn result_response(trace: &Option<String>, result: Value) -> Value {
    let mut response = Map::new();
    response.insert("r".into(), result);
    with_trace(response, trace)
}

pub fn error_response(trace: &Option<String>, kind: &str, message: &str) -> Value {
    let mut response = Map::new();
    response.insert(
        "e".into(),
        json!({
            "type": kind,
            "message": message,
            "detail": format!("{kind}: {message}"),
        }),
    );
    with_trace(response, trace)
}

fn trace_of(request: &Value) -> Option<String> {
    match request.get("t") {
        None | Some(Value::Null) => None,
        Some(Value::String(s)) => Some(s.clone()),
        Some(other) => Some(other.to_string()),
    }
}

fn string_param(params: &[Value], index: usize) -> Result<&str, String> {
    params
        .get(index)
        .and_then(Value::as_str)
        .ok_or_else(|| "wrong number of arguments".to_string())
}

/// The state of one client connection.
struct ClientHandler {
    endpoint: UartbusRpcEndpoint,
    writer: SharedWriter,
    session: Option<Arc<Session>>,
    /// trace ids of the `getNextPacket` calls, answered in order by the pump thread
    next_packet_requests: Option<mpsc::Sender<Option<String>>>,
}

impl ClientHandler {
    /// The session is created by the first call that needs it; a client that
    /// only sends packets does not collect the received ones.
    fn session(&mut self) -> io::Result<Arc<Session>> {
        if let Some(session) = &self.session {
            return Ok(Arc::clone(session));
        }
        let session = self.endpoint.new_session()?;
        self.session = Some(Arc::clone(&session));
        Ok(session)
    }

    fn queue_next_packet(&mut self, trace: Option<String>) -> io::Result<()> {
        let session = self.session()?;

        if self.next_packet_requests.is_none() {
            let (tx, rx) = mpsc::channel::<Option<String>>();
            let writer = Arc::clone(&self.writer);
            thread::Builder::new()
                .name("ub-rpc-pump".into())
                .spawn(move || {
                    for trace in rx {
                        let response = match session.take() {
                            Ok(packet) => {
                                result_response(&trace, Value::String(base64::encode(&packet)))
                            }
                            Err(err) => error_response(&trace, "EOFException", &err.to_string()),
                        };
                        if send_line(&writer, &response).is_err() {
                            break;
                        }
                    }
                })?;
            self.next_packet_requests = Some(tx);
        }

        self.next_packet_requests
            .as_ref()
            .unwrap()
            .send(trace)
            .map_err(|_| io::Error::other("packet pump stopped"))
    }

    /// Returns the response to send now, or None if it will be sent later.
    fn handle(&mut self, request: &Value) -> Option<Value> {
        let trace = trace_of(request);

        let namespace = request.get("N").and_then(Value::as_str);
        if namespace != Some(NAMESPACE) {
            return Some(error_response(
                &trace,
                "RuntimeException",
                &format!(
                    "Unknown RPC namespace: {}. The only namespace of this server is `{NAMESPACE}`",
                    namespace.unwrap_or("null")
                ),
            ));
        }

        let function = request.get("f").and_then(Value::as_str).unwrap_or("");
        let no_params = Vec::new();
        let params = request
            .get("p")
            .and_then(Value::as_array)
            .unwrap_or(&no_params);

        let result: Result<Value, (&str, String)> = match function {
            "init" => self
                .session()
                .map(|_| Value::Null)
                .map_err(|e| ("EOFException", e.to_string())),

            "sendPacket" => string_param(params, 0)
                .and_then(base64::decode)
                .map_err(|e| ("IllegalArgumentException", e))
                .and_then(|packet| {
                    self.endpoint
                        .send_packet(&packet)
                        .map(|_| Value::Null)
                        .map_err(|e| ("IOException", e.to_string()))
                }),

            "listAttributes" => Ok(json!(list_attributes())),

            "getAttribute" => string_param(params, 0)
                .map(str::to_string)
                .map_err(|e| ("IllegalArgumentException", e))
                .and_then(|key| {
                    self.session()
                        .map(|s| s.get_attribute(&key).map_or(Value::Null, Value::String))
                        .map_err(|e| ("EOFException", e.to_string()))
                }),

            "setAttribute" => string_param(params, 0)
                .and_then(|key| Ok((key.to_string(), string_param(params, 1)?.to_string())))
                .map_err(|e| ("IllegalArgumentException", e))
                .and_then(|(key, value)| {
                    self.session()
                        .map_err(|e| ("EOFException", e.to_string()))?
                        .set_attribute(&key, &value)
                        .map(|_| Value::Null)
                        .map_err(|e| ("RuntimeException", e))
                }),

            "getNextPacket" => match self.queue_next_packet(trace.clone()) {
                Ok(()) => return None,
                Err(e) => Err(("EOFException", e.to_string())),
            },

            //ends the packet receiving of this client only, the bus stays open for the others
            "close" => {
                if let Some(session) = &self.session {
                    session.close();
                }
                Ok(Value::Null)
            }

            other => Err(("RuntimeException", format!("Method not found: {other}"))),
        };

        Some(match result {
            Ok(value) => result_response(&trace, value),
            Err((kind, message)) => error_response(&trace, kind, &message),
        })
    }
}

fn serve_client(stream: TcpStream, endpoint: UartbusRpcEndpoint) {
    let _ = stream.set_nodelay(true);
    let reader = match stream.try_clone() {
        Ok(clone) => BufReader::new(clone),
        Err(_) => return,
    };

    let mut handler = ClientHandler {
        endpoint,
        writer: Arc::new(Mutex::new(stream)),
        session: None,
        next_packet_requests: None,
    };

    for line in reader.lines() {
        let Ok(line) = line else { break };
        //a line that is not a JSON object ends the connection
        let Ok(request @ Value::Object(_)) = serde_json::from_str::<Value>(&line) else {
            break;
        };

        if let Some(response) = handler.handle(&request) {
            if send_line(&handler.writer, &response).is_err() {
                break;
            }
        }
    }

    if let Some(session) = &handler.session {
        handler.endpoint.remove_session(session);
    }
    let _ = handler
        .writer
        .lock()
        .unwrap()
        .shutdown(std::net::Shutdown::Both);
}

/// Accepts clients forever, each one on its own thread.
pub fn serve(listener: TcpListener, endpoint: UartbusRpcEndpoint) -> io::Result<()> {
    for stream in listener.incoming() {
        match stream {
            Ok(stream) => {
                let endpoint = endpoint.clone();
                thread::Builder::new()
                    .name("ub-rpc-client".into())
                    .spawn(move || serve_client(stream, endpoint))?;
            }
            Err(err) if err.kind() == io::ErrorKind::Interrupted => continue,
            Err(err) => return Err(err),
        }
    }
    Ok(())
}

/// Starts serving on a background thread.
pub fn spawn(listener: TcpListener, endpoint: UartbusRpcEndpoint) -> io::Result<()> {
    thread::Builder::new()
        .name("ub-rpc-accept".into())
        .spawn(move || {
            let _ = serve(listener, endpoint);
        })?;
    Ok(())
}
