//! The subcommands of the `ub` executable. Every one has a `main` that gets
//! the arguments after the name of the subcommand.

pub mod attach_process;
pub mod blink;
pub mod collision_packetloss;
pub mod console;
pub mod ihex;
pub mod log_file;
pub mod log_sql;
pub mod packetloss;
pub mod ping;
pub mod rpc_server;
pub mod service;
pub mod upload;

use std::io;
use std::sync::atomic::{AtomicBool, Ordering};

use crate::cli::{self, CliArgs};
use crate::rpc::bus::UartBus;
use crate::rpc::connection::UartbusConnection;
use crate::rpc::endpoint::ATTR_LOOPBACK_SEND_PACKETS;
use crate::rpc::streamer::{open_ip_endpoint, ConnectionInitializer, UartbusStreamerEndpoint};
use crate::tools::{format_colon_data, is_packet_crc8_valid};

pub type AppError = Box<dyn std::error::Error + Send + Sync>;
pub type AppResult = Result<(), AppError>;

/// Name, entry point and description of a subcommand.
pub type Command = (&'static str, fn(&[String]) -> AppResult, &'static str);

/// Every subcommand, in the order of the help.
pub const COMMANDS: &[Command] = &[
    (
        "rpcServer",
        rpc_server::main,
        "Connects the bus gateway and serves the bus to the other commands on TCP",
    ),
    (
        "console",
        console::main,
        "Shows the packets of the bus and sends the ones typed in",
    ),
    ("ping", ping::main, "Sends ping packets to a device"),
    (
        "packetloss",
        packetloss::main,
        "Measures the packet loss between the gateway and a device",
    ),
    (
        "collisionPacketloss",
        collision_packetloss::main,
        "Measures loss and duplication when every device answers a broadcast",
    ),
    (
        "upload",
        upload::main,
        "Uploads application code (Intel HEX) into a device",
    ),
    (
        "ihex",
        ihex::main,
        "Intel HEX utilities: validate, size, replace_interrupts",
    ),
    (
        "blink",
        blink::main,
        "Toggles the user led of a device periodically",
    ),
    (
        "logSql",
        log_sql::main,
        "Saves the packets of the bus into an SQL table",
    ),
    (
        "logFile",
        log_file::main,
        "Saves the packets of the bus into rotated log files",
    ),
    (
        "attachProcess",
        attach_process::main,
        "Connects the stdin/stdout of a program to the bus",
    ),
    (
        "grab",
        upload::grab_main,
        "Restarts a device and keeps it in the host program (application not started)",
    ),
    ("reboot", upload::reboot_main, "Restarts a device"),
    ("version", version_main, "Prints the version"),
    (
        "service",
        service::main,
        "Services provided on the bus by this machine: ftp",
    ),
];

pub fn version_main(_args: &[String]) -> AppResult {
    println!("Uartbus Cli version: ");
    println!("{}", env!("CARGO_PKG_VERSION"));
    Ok(())
}

pub fn render_command_list() -> String {
    let mut out = String::from("Usage: ub <command> [options]\n\nCommands:\n");
    for (name, _, description) in COMMANDS {
        out.push_str(&format!("\t{name}\t\t{description}\n"));
    }
    out
}

/// Prints a received packet as colon separated bytes, `!` marks a wrong crc8.
pub fn print_packet_stdout(data: &[u8]) {
    if !data.is_empty() {
        let mark = if is_packet_crc8_valid(data) { "" } else { "!" };
        println!("{mark}{}", format_colon_data(data));
    }
}

/// Endpoint of the RPC server given with the host and port options (not reconnecting).
pub fn open_cli_endpoint(
    args: &CliArgs,
    initializer: Option<ConnectionInitializer>,
) -> Result<UartbusStreamerEndpoint, AppError> {
    Ok(open_ip_endpoint(
        &cli::parse_host(args),
        cli::parse_port(args)?,
        initializer,
        false,
    ))
}

/// Bus through the RPC server given with the host, port and from options.
pub fn cli_bus_connect(args: &CliArgs) -> Result<UartBus, AppError> {
    Ok(UartBus::from_tcp(
        &cli::parse_host(args),
        cli::parse_port(args)?,
        cli::parse_from(args)?,
    )?)
}

/// Initializer that asks the server for the packets sent by this client too, if the option is given.
pub fn loopback_initializer(args: &CliArgs) -> Option<ConnectionInitializer> {
    if !cli::LOOPBACK.has_option(args) {
        return None;
    }

    Some(Box::new(|connection: &dyn UartbusConnection| {
        if let Err(err) = connection.set_attribute(ATTR_LOOPBACK_SEND_PACKETS, "true") {
            eprintln!("{err}");
        }
    }))
}

/// `#.##`: at most two fraction digits, none if not needed.
pub fn format_double(n: f64) -> String {
    let text = format!("{n:.2}");
    let text = text.trim_end_matches('0').trim_end_matches('.');
    match text {
        "" | "-" | "-0" => "0".to_string(),
        other => other.to_string(),
    }
}

pub fn to_hex(data: &[u8]) -> String {
    data.iter().map(|b| format!("{b:02x}")).collect()
}

static INTERRUPTED: AtomicBool = AtomicBool::new(false);

extern "C" fn on_interrupt(_signal: libc::c_int) {
    INTERRUPTED.store(true, Ordering::SeqCst);
}

/// After this ^C (and SIGTERM) only sets a flag, so the command can print its summary before exit.
pub fn catch_interrupt() {
    let handler = on_interrupt as extern "C" fn(libc::c_int) as libc::sighandler_t;
    // SAFETY: the handler only stores into an atomic.
    unsafe {
        libc::signal(libc::SIGINT, handler);
        libc::signal(libc::SIGTERM, handler);
    }
}

pub fn is_interrupted() -> bool {
    INTERRUPTED.load(Ordering::SeqCst)
}

/// Sleeps the given time; returns false if interrupted meanwhile.
pub fn sleep_interruptible(ms: u64) -> bool {
    let mut left = ms;
    while left > 0 && !is_interrupted() {
        let step = left.min(50);
        std::thread::sleep(std::time::Duration::from_millis(step));
        left -= step;
    }
    !is_interrupted()
}

pub fn io_other(message: impl Into<String>) -> io::Error {
    io::Error::other(message.into())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn double_is_printed_with_at_most_two_fraction_digits() {
        assert_eq!(format_double(100.0), "100");
        assert_eq!(format_double(0.0), "0");
        assert_eq!(format_double(33.333333), "33.33");
        assert_eq!(format_double(12.5), "12.5");
        assert_eq!(format_double(0.001), "0");
    }

    #[test]
    fn hex_is_lower_case_two_digits_per_byte() {
        assert_eq!(to_hex(&[0, 0x0c, 0x94, 0xff]), "000c94ff");
    }

    #[test]
    fn command_names_are_the_ones_of_the_java_cli() {
        let names: Vec<&str> = COMMANDS.iter().map(|(name, _, _)| *name).collect();
        for name in [
            "rpcServer",
            "console",
            "ping",
            "packetloss",
            "collisionPacketloss",
            "upload",
            "ihex",
            "blink",
            "logSql",
            "logFile",
            "attachProcess",
            "grab",
            "reboot",
            "version",
            "service",
        ] {
            assert!(names.contains(&name), "{name}");
        }
        assert_eq!(names.len(), 15);
    }
}
