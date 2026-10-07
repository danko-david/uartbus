use std::cell::Cell;

use crate::packet::PacketAssembler;
use crate::tools::{parse_colon_data, PackError};

pub const HELP_LINES: &[&str] = &[
    "help:",
    "\t? : prints this help",
    "\t> : sets the destination address like: >3",
    "\t?> : prints the current destination address",
    "\t< : sets the source address like: >12",
    "\t?< : prints the current source address",
    "\tlines started with numbers and separated with colon is recognised as packet to send",
];

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ConsoleInquiry {
    NoOp,
    SendPacket,
    QueryAddressFrom,
    QueryAddressTo,
    NeedHelp,
    UnknownCommand,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ConsoleResponse {
    inquiry: ConsoleInquiry,
    data: Option<Vec<u8>>,
    message: Option<String>,
    from: i32,
    to: i32,
}

impl ConsoleResponse {
    pub fn new(inquiry: ConsoleInquiry, data: Option<Vec<u8>>, message: Option<String>) -> Self {
        Self {
            inquiry,
            data,
            message,
            from: 0,
            to: 0,
        }
    }

    pub fn with_addresses(
        inquiry: ConsoleInquiry,
        data: Option<Vec<u8>>,
        message: Option<String>,
        from: i32,
        to: i32,
    ) -> Self {
        Self {
            inquiry,
            data,
            message,
            from,
            to,
        }
    }

    pub fn get_type(&self) -> ConsoleInquiry {
        self.inquiry
    }

    pub fn get_data(&self) -> Option<&[u8]> {
        self.data.as_deref()
    }

    pub fn get_message(&self) -> Option<&str> {
        self.message.as_deref()
    }

    pub fn to_packet(&self) -> Result<Vec<u8>, PackError> {
        let mut assembler = PacketAssembler::new();
        assembler.write_addressing(self.from, self.to)?;
        if let Some(data) = &self.data {
            assembler.write_blob(data);
        }
        assembler.append_crc8();
        Ok(assembler.done())
    }
}

#[derive(Debug, Clone)]
pub struct UartbusConsoleEnv {
    address_from: Cell<i32>,
    address_to: Cell<i32>,
}

impl Default for UartbusConsoleEnv {
    fn default() -> Self {
        Self::new()
    }
}

impl UartbusConsoleEnv {
    pub fn new() -> Self {
        Self {
            address_from: Cell::new(0),
            address_to: Cell::new(0),
        }
    }

    pub fn feed(&self, line: &str) -> ConsoleResponse {
        let mut line = line.trim().to_string();

        if line.is_empty() {
            return ConsoleResponse::new(ConsoleInquiry::NoOp, None, None);
        }

        if line.starts_with('>') {
            match line[1..].trim().parse::<i32>() {
                Ok(address) => {
                    self.set_from_address(address);
                    line = "?>".to_string();
                }
                Err(_) => return self.unrecognised_cmd(&line),
            }
        } else if line.starts_with('<') {
            match line[1..].trim().parse::<i32>() {
                Ok(address) => {
                    self.set_to_address(address);
                    line = "?<".to_string();
                }
                Err(_) => return self.unrecognised_cmd(&line),
            }
        }

        if line.starts_with('?') {
            if line == "?" {
                return ConsoleResponse::new(ConsoleInquiry::NeedHelp, None, None);
            }

            if line.len() == 2 {
                return match line.as_bytes()[1] {
                    b'>' => ConsoleResponse::new(
                        ConsoleInquiry::QueryAddressFrom,
                        None,
                        Some(self.address_from.get().to_string()),
                    ),
                    b'<' => ConsoleResponse::new(
                        ConsoleInquiry::QueryAddressTo,
                        None,
                        Some(self.address_to.get().to_string()),
                    ),
                    _ => self.unrecognised_cmd(&line),
                };
            }

            return self.unrecognised_cmd(&line);
        }

        match parse_colon_data(&line) {
            Ok(data) => ConsoleResponse::with_addresses(
                ConsoleInquiry::SendPacket,
                Some(data),
                None,
                self.address_from.get(),
                self.address_to.get(),
            ),
            Err(_) => self.unrecognised_cmd(&line),
        }
    }

    fn unrecognised_cmd(&self, line: &str) -> ConsoleResponse {
        ConsoleResponse::new(ConsoleInquiry::UnknownCommand, None, Some(line.to_string()))
    }

    pub fn set_from_address(&self, address: i32) {
        self.address_from.set(address);
    }

    pub fn set_to_address(&self, address: i32) {
        self.address_to.set(address);
    }

    pub fn get_address_from(&self) -> i32 {
        self.address_from.get()
    }

    pub fn get_address_to(&self) -> i32 {
        self.address_to.get()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn feed_parses_colon_packet() {
        let env = UartbusConsoleEnv::new();
        let response = env.feed("1:2:3");
        assert_eq!(response.get_type(), ConsoleInquiry::SendPacket);
        assert_eq!(response.get_data(), Some(&[1, 2, 3][..]));
    }
}
