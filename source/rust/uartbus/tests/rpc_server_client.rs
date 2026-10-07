//! The TCP RPC server and its client: how programs share the bus.

mod common;

use std::io::{BufRead, BufReader, Write};
use std::net::{TcpListener, TcpStream};
use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::mpsc;
use std::sync::Arc;
use std::thread;
use std::time::Duration;

use common::wait_until;
use uartbus::rpc::connection::UartbusConnection;
use uartbus::rpc::endpoint::UartbusRpcEndpoint;
use uartbus::rpc::server;
use uartbus::rpc::streamer::{open_ip_endpoint, wait_reconnect};
use uartbus::rpc::TcpRpcClient;

/// Starts a server without gateway on a free port.
fn dummy_server() -> (u16, UartbusRpcEndpoint) {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let port = listener.local_addr().unwrap().port();
    let endpoint = UartbusRpcEndpoint::new_dummy();
    server::spawn(listener, endpoint.clone()).unwrap();
    (port, endpoint)
}

fn connect(port: u16) -> TcpRpcClient {
    TcpRpcClient::connect("127.0.0.1", port).unwrap()
}

/// Sends raw request lines and reads the raw response lines.
struct RawClient(TcpStream, BufReader<TcpStream>);

impl RawClient {
    fn new(port: u16) -> Self {
        let stream = TcpStream::connect(("127.0.0.1", port)).unwrap();
        stream
            .set_read_timeout(Some(Duration::from_secs(2)))
            .unwrap();
        let reader = BufReader::new(stream.try_clone().unwrap());
        Self(stream, reader)
    }

    fn send(&mut self, line: &str) {
        self.0.write_all(line.as_bytes()).unwrap();
        self.0.write_all(b"\n").unwrap();
    }

    fn read(&mut self) -> String {
        let mut line = String::new();
        self.1.read_line(&mut line).unwrap();
        line.trim_end().to_string()
    }

    fn request(&mut self, line: &str) -> String {
        self.send(line);
        self.read()
    }
}

#[test]
fn client_receives_the_packets_other_clients_send() {
    let (port, _endpoint) = dummy_server();
    let listener = connect(port);
    let sender = connect(port);

    listener.init().unwrap();
    sender.send_packet(&[5, 63, 1, 0, 196]).unwrap();
    sender.send_packet(&[]).unwrap();
    sender.send_packet(&[0xff, 0, 0xff]).unwrap();

    assert_eq!(listener.get_next_packet().unwrap(), vec![5, 63, 1, 0, 196]);
    assert_eq!(listener.get_next_packet().unwrap(), Vec::<u8>::new());
    assert_eq!(listener.get_next_packet().unwrap(), vec![0xff, 0, 0xff]);
}

#[test]
fn client_that_is_waiting_for_a_packet_can_send_meanwhile() {
    let (port, _endpoint) = dummy_server();
    let client = Arc::new(connect(port));
    client.init().unwrap();

    let (tx, rx) = mpsc::channel();
    let receiver = Arc::clone(&client);
    thread::spawn(move || tx.send(receiver.get_next_packet()).unwrap());

    //the receive call is blocked now, the send must get through on the same connection
    thread::sleep(Duration::from_millis(50));
    client.send_packet(&[1, 2, 3]).unwrap();

    let received = rx.recv_timeout(Duration::from_secs(2)).unwrap().unwrap();
    assert_eq!(received, vec![1, 2, 3]);
}

#[test]
fn packets_are_collected_only_after_the_client_registered() {
    let (port, endpoint) = dummy_server();
    let sender = connect(port);

    sender.send_packet(&[1]).unwrap();
    assert_eq!(endpoint.session_count(), 0);

    sender.init().unwrap();
    assert_eq!(endpoint.session_count(), 1);
    sender.send_packet(&[2]).unwrap();
    assert_eq!(sender.get_next_packet().unwrap(), vec![2]);
}

#[test]
fn session_ends_when_the_client_disconnects() {
    let (port, endpoint) = dummy_server();
    let client = connect(port);
    client.init().unwrap();
    assert_eq!(endpoint.session_count(), 1);

    drop(client);
    wait_until("session removed", || endpoint.session_count() == 0);
}

#[test]
fn loopback_attribute_decides_whether_a_client_gets_the_sent_packets() {
    let (port, _endpoint) = dummy_server();
    let quiet = connect(port);
    let sender = connect(port);

    //in dummy mode it is on by default
    assert_eq!(
        quiet.get_attribute("loopback_send_packets").unwrap(),
        Some("true".to_string())
    );
    assert_eq!(
        quiet.get_attribute("echo_loopback").unwrap(),
        Some("false".to_string())
    );
    assert_eq!(quiet.get_attribute("no_such_attribute").unwrap(), None);
    assert!(quiet
        .list_attributes()
        .unwrap()
        .contains_key("loopback_send_packets"));

    quiet
        .set_attribute("loopback_send_packets", "false")
        .unwrap();
    sender.init().unwrap();
    sender.send_packet(&[1]).unwrap();
    quiet
        .set_attribute("loopback_send_packets", "true")
        .unwrap();
    sender.send_packet(&[2]).unwrap();

    //the first packet was not collected for the quiet client
    assert_eq!(quiet.get_next_packet().unwrap(), vec![2]);
    assert_eq!(sender.get_next_packet().unwrap(), vec![1]);
}

#[test]
fn attribute_value_that_is_not_boolean_is_refused() {
    let (port, _endpoint) = dummy_server();
    let client = connect(port);
    let err = client
        .set_attribute("loopback_send_packets", "maybe")
        .unwrap_err();
    assert!(
        err.to_string()
            .contains("The given value `maybe` can not be casted to boolean."),
        "{err}"
    );
}

#[test]
fn receivers_get_an_error_when_the_bus_is_closed() {
    let (port, endpoint) = dummy_server();
    let client = connect(port);
    client.init().unwrap();

    let closer = endpoint.clone();
    thread::spawn(move || {
        thread::sleep(Duration::from_millis(50));
        closer.close();
    });

    let err = client.get_next_packet().unwrap_err();
    assert!(err.to_string().contains("End of receive."), "{err}");
}

#[test]
fn calls_fail_when_the_server_goes_away() {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let port = listener.local_addr().unwrap().port();
    let accept = thread::spawn(move || listener.accept().unwrap().0);

    let client = connect(port);
    //the server side closes the connection without answering
    drop(accept.join().unwrap());

    assert!(client.get_next_packet().is_err());
    assert!(client.init().is_err());
}

#[test]
fn responses_have_the_line_format_of_the_java_server() {
    let (port, _endpoint) = dummy_server();
    let mut raw = RawClient::new(port);

    //the trace id comes back as string, whatever it was
    assert_eq!(
        raw.request(r#"{"N":"uartbus","t":1,"f":"init","p":[]}"#),
        r#"{"r":null,"t":"1"}"#
    );
    assert_eq!(
        raw.request(r#"{"N":"uartbus","t":"2","f":"getAttribute","p":["loopback_send_packets"]}"#),
        r#"{"r":"true","t":"2"}"#
    );
    //packets are base64 strings
    assert_eq!(
        raw.request(r#"{"N":"uartbus","t":4,"f":"sendPacket","p":["AQI="]}"#),
        r#"{"r":null,"t":"4"}"#
    );
    assert_eq!(
        raw.request(r#"{"N":"uartbus","t":5,"f":"getNextPacket","p":[]}"#),
        r#"{"r":"AQI=","t":"5"}"#
    );
    //without trace id the response has none
    assert_eq!(
        raw.request(r#"{"N":"uartbus","f":"getAttribute","p":["echo_loopback"]}"#),
        r#"{"r":"false"}"#
    );
    //what the java client sends: extra `_` member, other order
    assert_eq!(
        raw.request(r#"{"p":["BT8BAMQ="],"t":3,"f":"sendPacket","N":"uartbus","_":null}"#),
        r#"{"r":null,"t":"3"}"#
    );
}

#[test]
fn bad_requests_are_answered_with_an_error_object() {
    let (port, _endpoint) = dummy_server();
    let mut raw = RawClient::new(port);

    let error_of = |line: String| -> (String, String, String) {
        let response: serde_json::Value = serde_json::from_str(&line).unwrap();
        (
            response["t"].as_str().unwrap().to_string(),
            response["e"]["type"].as_str().unwrap().to_string(),
            response["e"]["message"].as_str().unwrap().to_string(),
        )
    };

    let (t, kind, message) = error_of(raw.request(r#"{"N":"uartbus","t":7,"f":"nope","p":[]}"#));
    assert_eq!((t.as_str(), kind.as_str()), ("7", "RuntimeException"));
    assert_eq!(message, "Method not found: nope");

    let (_, _, message) = error_of(raw.request(r#"{"N":"zzz","t":8,"f":"init","p":[]}"#));
    assert!(
        message.starts_with("Unknown RPC namespace: zzz"),
        "{message}"
    );

    let (_, _, message) = error_of(raw.request(r#"{"t":9,"f":"init","p":[]}"#));
    assert!(
        message.starts_with("Unknown RPC namespace: null"),
        "{message}"
    );

    let (_, _, message) =
        error_of(raw.request(r#"{"N":"uartbus","t":11,"f":"sendPacket","p":[]}"#));
    assert_eq!(message, "wrong number of arguments");

    //the connection is still usable after errors
    assert_eq!(
        raw.request(r#"{"N":"uartbus","t":12,"f":"init","p":[]}"#),
        r#"{"r":null,"t":"12"}"#
    );
}

#[test]
fn line_that_is_not_json_closes_the_connection() {
    let (port, _endpoint) = dummy_server();
    let mut raw = RawClient::new(port);
    raw.send("garbage");
    assert_eq!(raw.read(), "");
}

#[test]
fn remote_close_ends_only_the_receiving_of_that_client() {
    let (port, endpoint) = dummy_server();
    let closing = connect(port);
    let other = connect(port);
    closing.init().unwrap();
    other.init().unwrap();

    closing.call("close", serde_json::json!([])).unwrap();
    assert!(closing.get_next_packet().is_err());

    assert!(!endpoint.is_closed());
    closing.send_packet(&[9]).unwrap();
    assert_eq!(other.get_next_packet().unwrap(), vec![9]);
}

#[test]
fn connecting_is_retried_until_it_succeeds() {
    let attempts = AtomicU32::new(0);
    let result = wait_reconnect(
        || {
            if attempts.fetch_add(1, Ordering::SeqCst) < 3 {
                Err(std::io::Error::other("not yet"))
            } else {
                Ok("connected")
            }
        },
        "test entity",
        &[1, 2],
    );
    assert_eq!(result.unwrap(), "connected");
    assert_eq!(attempts.load(Ordering::SeqCst), 4);
}

#[test]
fn reconnecting_endpoint_waits_for_a_server_that_starts_later() {
    //find a free port, the server starts listening on it only later
    let port = TcpListener::bind("127.0.0.1:0")
        .unwrap()
        .local_addr()
        .unwrap()
        .port();

    let starter = thread::spawn(move || {
        thread::sleep(Duration::from_millis(250));
        let endpoint = UartbusRpcEndpoint::new_dummy();
        server::spawn(TcpListener::bind(("127.0.0.1", port)).unwrap(), endpoint).unwrap();
    });

    let rpc = open_ip_endpoint("127.0.0.1", port, None, true);
    let (tx, rx) = mpsc::channel();
    rpc.start_streaming(move |packet| tx.send(packet.to_vec()).unwrap())
        .unwrap();
    starter.join().unwrap();

    rpc.send_packet(&[4, 5, 6]).unwrap();
    assert_eq!(
        rx.recv_timeout(Duration::from_secs(2)).unwrap(),
        vec![4, 5, 6]
    );
    rpc.close();
}

#[test]
fn endpoint_without_reconnect_fails_when_there_is_no_server() {
    let port = TcpListener::bind("127.0.0.1:0")
        .unwrap()
        .local_addr()
        .unwrap()
        .port();
    let rpc = open_ip_endpoint("127.0.0.1", port, None, false);
    assert!(rpc.start_streaming(|_| {}).is_err());
}

#[test]
fn connection_initializer_runs_on_every_new_connection() {
    let (port, _endpoint) = dummy_server();
    let rpc = open_ip_endpoint(
        "127.0.0.1",
        port,
        Some(Box::new(|connection| {
            connection
                .set_attribute("loopback_send_packets", "false")
                .unwrap()
        })),
        false,
    );

    let api = rpc.get_api().unwrap();
    assert_eq!(
        api.get_attribute("loopback_send_packets").unwrap(),
        Some("false".to_string())
    );
    rpc.close();
}
