mod common;

use std::io::{self, Read, Write};
use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::mpsc::{channel, Receiver, Sender};
use std::sync::{Arc, Mutex};

use common::wait_until;
use uartbus::connector::{
    EscapedStreamPacketConnector, IoStream, OnStreamClosed, UartbusPacketConnector,
};
use uartbus::{frame_bytes, FramedPacketCutter};

fn packets(bytes: &[u8]) -> Vec<Vec<u8>> {
    let mut seen = Vec::new();
    FramedPacketCutter::new(0xff).feed_bytes(bytes, |packet| seen.push(packet));
    seen
}

#[test]
fn uartbus_unpack_matches_java_test() {
    assert_eq!(
        packets(&[
            20, 30, 1, 0, 1, 0xff, 0x00, 20, 30, 30, 30, 30, 0xff, 0x00, 20, 30, 1, 0, 76, 0xff,
            0x00
        ]),
        vec![
            vec![20, 30, 1, 0, 1],
            vec![20, 30, 30, 30, 30],
            vec![20, 30, 1, 0, 76],
        ]
    );
}

#[test]
fn uartbus_unpack2_matches_java_test() {
    assert_eq!(
        packets(&[
            20, 30, 1, 0, 76, 0xff, 0x00, 20, 30, 1, 0, 76, 0xff, 0x00, 20, 30, 1, 0, 76, 0xff,
            0x00, 20, 30, 1, 0, 76, 0xff, 0x00, 20, 30, 1, 0, 1, 0xff, 0x00, 20, 30, 1, 0, 1, 0xff,
            0x00, 20, 30, 1, 0, 76, 0xff, 0x00, 20, 30, 1, 0, 76, 0xff, 0x00, 20, 30, 1, 0, 76,
            0xff, 0x00, 20, 30, 1, 0, 76, 0xff, 0x00,
        ]),
        vec![
            vec![20, 30, 1, 0, 76],
            vec![20, 30, 1, 0, 76],
            vec![20, 30, 1, 0, 76],
            vec![20, 30, 1, 0, 76],
            vec![20, 30, 1, 0, 1],
            vec![20, 30, 1, 0, 1],
            vec![20, 30, 1, 0, 76],
            vec![20, 30, 1, 0, 76],
            vec![20, 30, 1, 0, 76],
            vec![20, 30, 1, 0, 76],
        ]
    );
}

/// The read side of a fake gateway: gives the chunks sent on the channel, ends when the sender is dropped.
struct ChannelReader(Receiver<Vec<u8>>, Vec<u8>);

impl Read for ChannelReader {
    fn read(&mut self, buf: &mut [u8]) -> io::Result<usize> {
        if self.1.is_empty() {
            match self.0.recv() {
                Ok(chunk) => self.1 = chunk,
                Err(_) => return Ok(0),
            }
        }
        let n = buf.len().min(self.1.len());
        buf[..n].copy_from_slice(&self.1[..n]);
        self.1.drain(..n);
        Ok(n)
    }
}

#[derive(Clone, Default)]
struct SharedWriter(Arc<Mutex<Vec<u8>>>);

impl Write for SharedWriter {
    fn write(&mut self, buf: &[u8]) -> io::Result<usize> {
        self.0.lock().unwrap().extend_from_slice(buf);
        Ok(buf.len())
    }

    fn flush(&mut self) -> io::Result<()> {
        Ok(())
    }
}

/// A fake gateway: every opened stream gets a new input channel, the output is collected.
struct FakeGateway {
    inputs: Arc<Mutex<Vec<Sender<Vec<u8>>>>>,
    written: SharedWriter,
    opened: Arc<AtomicU32>,
}

impl FakeGateway {
    fn new() -> Self {
        Self {
            inputs: Arc::new(Mutex::new(Vec::new())),
            written: SharedWriter::default(),
            opened: Arc::new(AtomicU32::new(0)),
        }
    }

    fn connector(&self, on_closed: OnStreamClosed) -> EscapedStreamPacketConnector {
        let inputs = Arc::clone(&self.inputs);
        let written = self.written.clone();
        let opened = Arc::clone(&self.opened);
        EscapedStreamPacketConnector::new(
            Box::new(move || {
                let (tx, rx) = channel();
                inputs.lock().unwrap().push(tx);
                opened.fetch_add(1, Ordering::SeqCst);
                Ok(IoStream {
                    reader: Box::new(ChannelReader(rx, Vec::new())),
                    writer: Box::new(written.clone()),
                    child: None,
                })
            }),
            0xff,
            on_closed,
        )
        .unwrap()
    }

    /// Bytes arrive from the gateway on the latest stream.
    fn feed(&self, bytes: &[u8]) {
        self.inputs
            .lock()
            .unwrap()
            .last()
            .unwrap()
            .send(bytes.to_vec())
            .unwrap();
    }

    /// The gateway closes the latest stream.
    fn break_stream(&self) {
        self.inputs.lock().unwrap().pop();
    }
}

fn collect_packets(connector: &EscapedStreamPacketConnector) -> Arc<Mutex<Vec<Vec<u8>>>> {
    let seen = Arc::new(Mutex::new(Vec::new()));
    let sink = Arc::clone(&seen);
    connector.set_packet_hook(Box::new(move |packet| sink.lock().unwrap().push(packet)));
    seen
}

#[test]
fn packets_from_the_gateway_are_delivered_even_if_they_arrive_in_pieces() {
    let gateway = FakeGateway::new();
    let connector = gateway.connector(OnStreamClosed::Reconnect);
    let seen = collect_packets(&connector);
    connector.start_listen().unwrap();

    gateway.feed(&[20, 30, 1]);
    gateway.feed(&[0, 1, 0xff]);
    gateway.feed(&[0x00, 20, 30, 0xff, 0xff, 9, 0xff, 0x00]);

    wait_until("two packets", || seen.lock().unwrap().len() == 2);
    assert_eq!(
        *seen.lock().unwrap(),
        vec![vec![20, 30, 1, 0, 1], vec![20, 30, 0xff, 9]]
    );
}

#[test]
fn sent_packet_is_written_framed_to_the_gateway() {
    let gateway = FakeGateway::new();
    let connector = gateway.connector(OnStreamClosed::Reconnect);
    connector.start_listen().unwrap();

    connector.send_packet(&[12, 5, 0xff, 192]).unwrap();
    assert_eq!(
        *gateway.written.0.lock().unwrap(),
        frame_bytes(&[12, 5, 0xff, 192], 0xff)
    );
}

#[test]
fn sending_before_listening_is_an_error() {
    let gateway = FakeGateway::new();
    let connector = gateway.connector(OnStreamClosed::Reconnect);
    assert!(connector.send_packet(&[1, 2, 3]).is_err());
}

#[test]
fn broken_stream_is_reopened_and_packets_flow_again() {
    let gateway = FakeGateway::new();
    let connector = gateway.connector(OnStreamClosed::Reconnect);
    let seen = collect_packets(&connector);
    connector.start_listen().unwrap();

    //half packet, then the stream breaks: the half must not be glued to the next stream
    gateway.feed(&[1, 2, 3]);
    gateway.break_stream();
    wait_until("stream reopened", || {
        gateway.opened.load(Ordering::SeqCst) == 2
    });

    gateway.feed(&[7, 8, 0xff, 0x00]);
    wait_until("packet on the new stream", || {
        !seen.lock().unwrap().is_empty()
    });
    assert_eq!(*seen.lock().unwrap(), vec![vec![7, 8]]);
}

#[test]
fn broken_stream_without_reconnect_calls_the_close_handler() {
    let gateway = FakeGateway::new();
    let closed = Arc::new(AtomicU32::new(0));
    let counter = Arc::clone(&closed);
    let connector = gateway.connector(OnStreamClosed::Call(Box::new(move || {
        counter.fetch_add(1, Ordering::SeqCst);
    })));
    connector.start_listen().unwrap();

    gateway.break_stream();
    wait_until("close handler called", || {
        closed.load(Ordering::SeqCst) == 1
    });
    assert_eq!(gateway.opened.load(Ordering::SeqCst), 1);
}
