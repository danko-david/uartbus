use std::thread;
use std::time::Duration;

use crate::apps::{open_cli_endpoint, print_packet_stdout, AppResult};
use crate::cli::{self, CliEntry, FROM, RPC_HOST, RPC_PORT, TO};
use crate::packet::PacketAssembler;

pub const INTERVAL: CliEntry = CliEntry {
    names: &["i", "-interval"],
    description: "Blink interval",
};

const ENTRIES: &[&CliEntry] = &[&RPC_HOST, &RPC_PORT, &FROM, &TO, &INTERVAL];

pub fn main(args: &[String]) -> AppResult {
    let pa = cli::parse_or_help("UartbusBlink", args, ENTRIES);

    let from = cli::parse_from(&pa)?;
    let to: i32 = TO.parse_or(&pa, -1)?;
    let interval: u64 = INTERVAL.parse_or(&pa, 500)?;

    let rpc = open_cli_endpoint(&pa, None)?;
    rpc.start_streaming(print_packet_stdout)?;
    let conn = rpc.get_api()?;

    //bus functions (1), user led (2), toggle (2)
    let mut asm = PacketAssembler::new();
    asm.write_addressing(from, to)?;
    asm.write_blob(&[1, 2, 2]);
    asm.append_crc8();
    let packet = asm.done();

    loop {
        conn.send_packet(&packet)?;
        println!("blink {from} > {to}");
        thread::sleep(Duration::from_millis(interval));
    }
}
