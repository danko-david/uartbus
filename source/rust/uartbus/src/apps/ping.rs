use std::thread;
use std::time::Duration;

use crate::apps::{open_cli_endpoint, print_packet_stdout, AppResult};
use crate::cli::{self, CliEntry, FROM, RPC_HOST, RPC_PORT, TO};
use crate::packet::PacketAssembler;

pub const INTERVAL: CliEntry = CliEntry {
    names: &["i", "-interval"],
    description: "Ping repeat delay",
};

pub const COUNT_OF_PACKETS: CliEntry = CliEntry {
    names: &["c", "-c"],
    description: "Number of packets to send. (default: 0 aka no \"limit\")",
};

const ENTRIES: &[&CliEntry] = &[
    &RPC_HOST,
    &RPC_PORT,
    &FROM,
    &TO,
    &INTERVAL,
    &COUNT_OF_PACKETS,
];

/// The ping request: bus functions (1), ping (0)
pub fn ping_packet(from: i32, to: i32) -> Result<Vec<u8>, crate::tools::PackError> {
    let mut asm = PacketAssembler::new();
    asm.write_addressing(from, to)?;
    asm.write_blob(&[1, 0]);
    asm.append_crc8();
    Ok(asm.done())
}

pub fn main(args: &[String]) -> AppResult {
    let pa = cli::parse_or_help("UartbusPing", args, ENTRIES);

    let from = cli::parse_from(&pa)?;
    let to: i32 = TO.parse_or(&pa, -1)?;
    let interval: u64 = INTERVAL.parse_or(&pa, 1000)?;
    let count: i64 = COUNT_OF_PACKETS.parse_or(&pa, 0)?;

    let rpc = open_cli_endpoint(&pa, None)?;
    rpc.start_streaming(print_packet_stdout)?;
    let conn = rpc.get_api()?;

    let packet = ping_packet(from, to)?;

    let mut i = 0i64;
    while i < count || count <= 0 {
        conn.send_packet(&packet)?;
        println!("ping {from} > {to}");
        thread::sleep(Duration::from_millis(interval));
        i += 1;
    }

    //the answers are shown until the server goes away
    rpc.join();
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ping_packet_is_addressing_then_bus_ping_then_crc() {
        assert_eq!(ping_packet(63, 5).unwrap(), vec![5, 63, 1, 0, 0xc4]);
    }
}
