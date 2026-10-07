use std::thread;
use std::time::Duration;

use crate::apps::{cli_bus_connect, format_double, AppResult};
use crate::cli::{self, CliEntry, FROM, RPC_HOST, RPC_PORT, TO};
use crate::rpc::device::RpcError;

use crate::apps::ping::{COUNT_OF_PACKETS, INTERVAL};

pub const TIMEOUT: CliEntry = CliEntry {
    names: &["l", "-loss-time"],
    description: "Packet response timeout in milisec",
};

const ENTRIES: &[&CliEntry] = &[
    &RPC_HOST,
    &RPC_PORT,
    &FROM,
    &TO,
    &INTERVAL,
    &COUNT_OF_PACKETS,
    &TIMEOUT,
];

pub fn render_statistic(pass: u64, loss: u64) -> String {
    format!(
        "Statistic:\n\tSent packets: {}\n\tReceived packets: {}\n\tLost packets: {}\n\tPacket loss ratio: {} %",
        pass + loss,
        pass,
        loss,
        format_double((100.0 * loss as f64) / (loss + pass) as f64)
    )
}

pub fn main(args: &[String]) -> AppResult {
    let pa = cli::parse_or_help("UartbusPacketloss", args, ENTRIES);

    let to: i32 = TO.parse_or(&pa, -1)?;
    let interval: u64 = INTERVAL.parse_or(&pa, 1000)?;
    let count: i64 = COUNT_OF_PACKETS.parse_or(&pa, 0)?;
    let timeout: u64 = TIMEOUT.parse_or(&pa, 3000)?;

    let bus = cli_bus_connect(&pa)?;
    let mut dev = bus.device(to);
    dev.timeout = Duration::from_millis(timeout);

    let mut loss = 0u64;
    let mut pass = 0u64;

    let mut i = 0i64;
    while i < count || count <= 0 {
        match dev.ping() {
            Ok(()) => {
                pass += 1;
                println!("{i}. pong");
                if interval > 0 {
                    thread::sleep(Duration::from_millis(interval));
                }
            }
            Err(RpcError::Transaction(_)) => {
                println!("{i}. -- loss --");
                loss += 1;
            }
            Err(_) => break,
        }
        i += 1;
    }

    if pass != 0 || loss != 0 {
        println!("{}", render_statistic(pass, loss));
    }

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn statistic_lists_sent_received_lost_and_the_ratio() {
        assert_eq!(
            render_statistic(3, 1),
            "Statistic:\n\tSent packets: 4\n\tReceived packets: 3\n\tLost packets: 1\n\tPacket loss ratio: 25 %"
        );
        assert!(render_statistic(0, 2).ends_with("Packet loss ratio: 100 %"));
    }
}
