use std::collections::{BTreeMap, HashMap};
use std::sync::{Arc, Mutex};

use num_traits::ToPrimitive;

use crate::apps::ping::{COUNT_OF_PACKETS, INTERVAL};
use crate::apps::{
    catch_interrupt, format_double, is_interrupted, open_cli_endpoint, sleep_interruptible,
    AppResult,
};
use crate::cli::{self, CliEntry, FROM, RPC_HOST, RPC_PORT};
use crate::packet::{PacketAssembler, ParsedUartBusPacket};
use crate::tools::{is_packet_crc8_valid, unpack_value, PackError};

const ENTRIES: &[&CliEntry] = &[&RPC_HOST, &RPC_PORT, &FROM, &INTERVAL, &COUNT_OF_PACKETS];

/// Broadcast "replay" request (bus functions 1, replay 1) with a sequence number every device answers.
pub fn generate_packet_sequence(from: i32, seq: u32) -> Result<Vec<u8>, PackError> {
    let mut asm = PacketAssembler::new();
    asm.write_addressing(from, 0)?;
    asm.write_blob(&[1, 1]);
    asm.write_packed_value(false, seq)?;
    asm.append_crc8();
    Ok(asm.done())
}

#[derive(Default)]
pub struct Statistic {
    /// device address => sequence number => how many times it was answered. Device 0: the damaged packets
    pub replies: BTreeMap<i32, HashMap<i64, u32>>,
    /// length of damaged packet => count
    pub collision_lengths: BTreeMap<usize, u32>,
}

impl Statistic {
    /// Counts a packet received from the bus (with crc8).
    pub fn feed(&mut self, from: i32, packet: &[u8]) {
        if packet.len() > 1 && is_packet_crc8_valid(packet) {
            //the crc8 stays at the end of the payload, it does not disturb reading the sequence
            let Ok(parsed) = ParsedUartBusPacket::new(packet.to_vec()) else {
                return;
            };
            if parsed.to != from {
                return;
            }
            //the answer payload is: 0, 1, 1, sequence
            if let Ok((seq, _)) = unpack_value(false, &parsed.payload, 3) {
                let seq = seq.to_i64().unwrap_or(-1);
                *self
                    .replies
                    .entry(parsed.from)
                    .or_default()
                    .entry(seq)
                    .or_insert(0) += 1;
            }
        } else {
            *self.replies.entry(0).or_default().entry(0).or_insert(0) += 1;
            *self.collision_lengths.entry(packet.len()).or_insert(0) += 1;
        }
    }

    pub fn render_device(device: i32, responses: &HashMap<i64, u32>, sent: u32) -> String {
        if sent == 0 {
            return String::new();
        }

        if device == 0 {
            let collided = responses.get(&0).copied().unwrap_or(responses.len() as u32);
            return format!(
                "Statistic of collisions:\tSent packets: {sent}\tCollided packets: {collided}\tCollision ratio: {} %",
                format_double((100.0 * collided as f64) / sent as f64)
            );
        }

        let received = responses.len() as i64;
        let loss = sent as i64 - received;
        let mut out = format!(
            "Statistic for bus device {device}: \tSent packets: {sent}\tReceived packets: {received}\tLost packets: {loss}\tPacket loss ratio: {} %",
            format_double((100.0 * loss as f64) / sent as f64)
        );

        let duplicates: u32 = responses.values().filter(|v| **v > 1).map(|v| v - 1).sum();
        if duplicates > 0 {
            out.push_str(&format!(
                " Duplication ({duplicates}) ratio: {} %",
                format_double((100.0 * duplicates as f64) / sent as f64)
            ));
        }
        out
    }

    pub fn render(&self, sent: u32) -> String {
        if sent == 0 {
            return "No broadcast packet sent.".to_string();
        }
        if self.replies.is_empty() {
            return "No broadcast response packet received.".to_string();
        }

        let mut out = String::new();
        for (device, responses) in &self.replies {
            out.push_str(&Self::render_device(*device, responses, sent));
            out.push('\n');
        }

        out.push_str("Collisions: ");
        if self.collision_lengths.is_empty() {
            out.push_str("{}");
        } else {
            out.push_str("{\n");
            for (length, count) in &self.collision_lengths {
                out.push_str(&format!("\t\"{length}\":\"{count}\"\n"));
            }
            out.push('}');
        }
        out
    }
}

pub fn main(args: &[String]) -> AppResult {
    let pa = cli::parse_or_help("UartbusCollisionPacketloss", args, ENTRIES);

    let from = cli::parse_from(&pa)?;
    let interval: u64 = INTERVAL.parse_or(&pa, 1000)?;
    let count: i64 = COUNT_OF_PACKETS.parse_or(&pa, 0)?;

    let stat = Arc::new(Mutex::new(Statistic::default()));

    let rpc = open_cli_endpoint(&pa, None)?;
    let feed = Arc::clone(&stat);
    rpc.start_streaming(move |packet| feed.lock().unwrap().feed(from, packet))?;
    let conn = rpc.get_api()?;

    //the statistic is printed on ^C too
    catch_interrupt();

    let mut sent = 0u32;
    let mut result = Ok(());
    let mut i = 0i64;
    while (i < count || count <= 0) && !is_interrupted() {
        if let Err(err) = conn.send_packet(&generate_packet_sequence(from, i as u32)?) {
            result = Err(err.into());
            break;
        }
        sent += 1;
        println!("{i}. broadcast ping ");
        sleep_interruptible(interval);
        i += 1;
    }

    if result.is_ok() && !is_interrupted() {
        println!("Last turn, waiting a bit for the last packets.");
        sleep_interruptible(2000);
    }

    println!("{}", stat.lock().unwrap().render(sent));
    result
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rpc::tools::{to_packet, RpcValue};

    fn answer(device: i32, to: i32, seq: u8) -> Vec<u8> {
        to_packet(to, device, &[RpcValue::Bytes(vec![0, 1, 1, seq])]).unwrap()
    }

    #[test]
    fn request_is_a_broadcast_replay_with_the_sequence_number() {
        assert_eq!(
            generate_packet_sequence(63, 5).unwrap()[..5],
            [0, 63, 1, 1, 5]
        );
    }

    #[test]
    fn answers_are_counted_per_device_and_sequence() {
        let mut stat = Statistic::default();
        stat.feed(63, &answer(4, 63, 0));
        stat.feed(63, &answer(4, 63, 1));
        stat.feed(63, &answer(4, 63, 1));
        stat.feed(63, &answer(7, 63, 0));
        //not for us
        stat.feed(63, &answer(9, 12, 0));

        assert_eq!(stat.replies.len(), 2);
        assert_eq!(stat.replies[&4][&1], 2);

        let text = stat.render(4);
        assert!(text.contains("Statistic for bus device 4: \tSent packets: 4\tReceived packets: 2\tLost packets: 2\tPacket loss ratio: 50 % Duplication (1) ratio: 25 %"), "{text}");
        assert!(text.contains("Statistic for bus device 7: \tSent packets: 4\tReceived packets: 1\tLost packets: 3\tPacket loss ratio: 75 %"), "{text}");
        assert!(text.ends_with("Collisions: {}"), "{text}");
    }

    #[test]
    fn damaged_packets_are_counted_as_collisions_by_length() {
        let mut stat = Statistic::default();
        let mut damaged = answer(4, 63, 0);
        *damaged.last_mut().unwrap() ^= 0xff;
        stat.feed(63, &damaged);
        stat.feed(63, &[1]);

        let text = stat.render(4);
        assert!(text.starts_with("Statistic of collisions:\tSent packets: 4\tCollided packets: 2\tCollision ratio: 50 %"), "{text}");
        assert!(
            text.ends_with("Collisions: {\n\t\"1\":\"1\"\n\t\"7\":\"1\"\n}"),
            "{text}"
        );
    }

    #[test]
    fn nothing_sent_or_nothing_received_is_said_so() {
        assert_eq!(Statistic::default().render(0), "No broadcast packet sent.");
        assert_eq!(
            Statistic::default().render(3),
            "No broadcast response packet received."
        );
    }
}
