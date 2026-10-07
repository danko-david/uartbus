use std::io;
use std::net::TcpListener;
use std::thread;
use std::time::Duration;

use crate::apps::AppResult;
use crate::cli::{self, CliArgs, CliEntry, RECONNECT, SERIAL_BAUD, WORK_DIR};
use crate::connector::{
    open_direct_serial, open_serial, DummyConnector, EscapedStreamPacketConnector, IoStream,
    OnStreamClosed, UartbusPacketConnector,
};
use crate::rpc::endpoint::UartbusRpcEndpoint;
use crate::rpc::server;
use crate::rpc::streamer::{wait_reconnect, RECONNECT_TIMES_MS};

pub const RPC_PORT: CliEntry = CliEntry {
    names: &["p", "-port"],
    description: "Rpc listen port",
};

pub const SERIAL_DEV: CliEntry = CliEntry {
    names: &["s", "-serial-device"],
    description: "Serial device path",
};

pub const DIRECT_SERIAL_DEVICE: CliEntry = CliEntry {
    names: &["i", "-direct-serial-device"],
    description: "Direct serial driver",
};

pub const MODE: CliEntry = CliEntry {
    names: &["m", "-mode"],
    description: "Rpc backend mode (serial, dummy)",
};

const ENTRIES: &[&CliEntry] = &[
    &WORK_DIR,
    &RPC_PORT,
    &SERIAL_DEV,
    &SERIAL_BAUD,
    &RECONNECT,
    &DIRECT_SERIAL_DEVICE,
    &MODE,
];

/// Byte that frames the packets between the PC and the gateway.
pub const GATEWAY_PACKET_ESCAPE: u8 = 0xff;

/// When the serial device is opened the arduino restarts into its bootloader,
/// which waits for code upload. If we send data meanwhile, it never starts the
/// gateway application, so we wait until the bootloader times out.
const ARDUINO_BOOTLOADER_WAIT: Duration = Duration::from_millis(2000);

fn ensure_running(mut io: IoStream) -> io::Result<IoStream> {
    if let Some(child) = io.child.as_mut() {
        if let Some(status) = child.try_wait()? {
            return Err(io::Error::other(format!(
                "Communication process is dead. Exit status: {status}"
            )));
        }
    }
    Ok(io)
}

fn create_serial_packet_connector(
    args: &CliArgs,
) -> Result<Option<Box<dyn UartbusPacketConnector>>, String> {
    let serial = SERIAL_DEV.get_simple(args).map(str::to_string);
    let baud = SERIAL_BAUD.parse::<u32>(args)?;
    let direct_command = DIRECT_SERIAL_DEVICE.get_simple(args).map(str::to_string);

    if serial.is_none() {
        eprintln!("No Serial port specified.");
    }
    if baud.is_none() {
        eprintln!("No Serial baud rate specified.");
    }
    let (Some(serial), Some(baud)) = (serial, baud) else {
        return Ok(None);
    };

    let open = move || {
        wait_reconnect(
            || match &direct_command {
                Some(command) => ensure_running(open_direct_serial(command, &serial, baud)?),
                None => {
                    let io = open_serial(&serial, baud)?;
                    thread::sleep(ARDUINO_BOOTLOADER_WAIT);
                    ensure_running(io)
                }
            },
            "serial connection to the bus gateway",
            &RECONNECT_TIMES_MS,
        )
    };

    let on_closed = if RECONNECT.has_option(args) {
        OnStreamClosed::Reconnect
    } else {
        OnStreamClosed::Call(Box::new(|| std::process::exit(5)))
    };

    let connector =
        EscapedStreamPacketConnector::new(Box::new(open), GATEWAY_PACKET_ESCAPE, on_closed)
            .map_err(|err| err.to_string())?;
    Ok(Some(Box::new(connector)))
}

fn bind(port: u16) -> io::Result<TcpListener> {
    //every interface, IPv6 too where it is available
    TcpListener::bind(("::", port)).or_else(|_| TcpListener::bind(("0.0.0.0", port)))
}

pub fn main(args: &[String]) -> AppResult {
    let pa = cli::parse_or_help("UarbusRpcServer", args, ENTRIES);

    let port: u16 = RPC_PORT.parse_or(&pa, cli::DEFAULT_RPC_PORT)?;
    let mode = MODE.get_simple(&pa).unwrap_or("serial");
    let dummy = mode == "dummy";

    let connector: Option<Box<dyn UartbusPacketConnector>> = match mode {
        "serial" => create_serial_packet_connector(&pa)?,
        "dummy" => Some(Box::new(DummyConnector)),
        _ => None,
    };

    let Some(connector) = connector else {
        cli::print_help_and_exit("UarbusRpcServer", 2, ENTRIES)
    };

    //with a gateway the clients see each other's packets (the gateway does not
    //give back what we send), in dummy mode that is all a client can see
    let endpoint = UartbusRpcEndpoint::new(connector, !dummy, dummy)?;

    let listener =
        bind(port).map_err(|err| format!("Can't listen on the RPC port {port}: {err}"))?;
    server::serve(listener, endpoint)?;
    Ok(())
}
