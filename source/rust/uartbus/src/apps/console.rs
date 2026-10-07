use std::io::{self, BufRead};

use crate::apps::{loopback_initializer, open_cli_endpoint, AppResult};
use crate::cli::{self, CliEntry, DECODE_PACKET, FROM, LOG_TIME, LOOPBACK, RPC_HOST, RPC_PORT, TO};
use crate::console::{ConsoleInquiry, UartbusConsoleEnv, HELP_LINES};
use crate::packet::PacketAssembler;
use crate::time::{now_ms, utc_sql_timestamp_ms};
use crate::tools::{
    format_colon_data_with_validation, format_packet_with_mode, parse_colon_data,
    PacketFormattingMode,
};

pub const DATA: CliEntry = CliEntry {
    names: &["d", "-data"],
    description: "Data to send (colon separated uint8_t)",
};

pub const EXIT: CliEntry = CliEntry {
    names: &["e", "-exit"],
    description: "Exit after packet sent",
};

const ENTRIES: &[&CliEntry] = &[
    &RPC_HOST,
    &RPC_PORT,
    &FROM,
    &TO,
    &DATA,
    &EXIT,
    &LOG_TIME,
    &LOOPBACK,
    &DECODE_PACKET,
];

fn get_time() -> String {
    utc_sql_timestamp_ms(now_ms())
}

/// The line shown for a received packet.
pub fn format_received(packet: &[u8], time: Option<&str>, decode: bool) -> String {
    let mut line = String::new();
    if let Some(time) = time {
        line.push_str(&format!(">[{time}] "));
    }

    if decode {
        line.push_str(&format_packet_with_mode(
            PacketFormattingMode::ParsedPacket,
            packet,
        ));
    } else {
        line.push_str(&format_colon_data_with_validation(packet));
    }
    line
}

pub fn main(args: &[String]) -> AppResult {
    let pa = cli::parse_or_help("UartbusConsole", args, ENTRIES);

    let cmd = UartbusConsoleEnv::new();
    cmd.set_from_address(cli::parse_from(&pa)?);
    cmd.set_to_address(TO.parse_or(&pa, -1)?);

    let decode = DECODE_PACKET.has_option(&pa);
    let log_times = LOG_TIME.has_option(&pa);

    let rpc = open_cli_endpoint(&pa, loopback_initializer(&pa))?;
    rpc.start_streaming(move |packet| {
        if packet.is_empty() {
            return;
        }
        let time = log_times.then(get_time);
        println!("{}", format_received(packet, time.as_deref(), decode));
    })?;

    let conn = rpc.get_api()?;

    if let Some(data) = DATA.get_simple(&pa) {
        let data = parse_colon_data(data).map_err(|e| format!("Invalid data `{data}`: {e}"))?;
        let mut asm = PacketAssembler::new();
        asm.write_addressing(cmd.get_address_from(), cmd.get_address_to())?;
        asm.write_blob(&data);
        asm.append_crc8();
        conn.send_packet(&asm.done())?;
        if EXIT.has_option(&pa) {
            return Ok(());
        }
    }

    for line in io::stdin().lock().lines() {
        let line = line?;
        let ret = cmd.feed(&line);
        match ret.get_type() {
            ConsoleInquiry::NoOp => continue,
            ConsoleInquiry::NeedHelp => {
                for help in HELP_LINES {
                    println!("{help}");
                }
            }
            ConsoleInquiry::QueryAddressFrom => {
                println!("From address: {}", cmd.get_address_from())
            }
            ConsoleInquiry::QueryAddressTo => println!("To address: {}", cmd.get_address_to()),
            ConsoleInquiry::SendPacket => {
                conn.send_packet(&ret.to_packet()?)?;
                if log_times {
                    println!("<[{}]", get_time());
                }
            }
            ConsoleInquiry::UnknownCommand => println!(
                "Unrecognised command or packet sequence: `{}` enter `?` to display help",
                ret.get_message().unwrap_or("")
            ),
        }
    }

    //no more input, but the packets are shown until the server goes away
    rpc.join();
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn received_packet_is_shown_as_colon_bytes_with_wrong_crc_marked() {
        assert_eq!(
            format_received(&[5, 63, 1, 0, 0xc4], None, false),
            "5:63:1:0:196"
        );
        assert_eq!(
            format_received(&[5, 63, 1, 0, 1], None, false),
            "!5:63:1:0:1"
        );
    }

    #[test]
    fn receive_time_is_put_before_the_packet() {
        assert_eq!(
            format_received(
                &[5, 63, 1, 0, 0xc4],
                Some("2026-10-07 01:16:08.592U"),
                false
            ),
            ">[2026-10-07 01:16:08.592U] 5:63:1:0:196"
        );
    }

    #[test]
    fn decoded_packet_names_the_addresses_and_the_payload() {
        assert_eq!(
            format_received(&[5, 63, 1, 0, 0xc4], None, true),
            "From: 63, To: 5, payload: 1:0, crc8: 196"
        );
    }
}
