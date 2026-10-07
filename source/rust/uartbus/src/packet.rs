use num_bigint::BigInt;
use std::error::Error;
use std::fmt;

use crate::tools::{crc8, pack_value_into, unpack_value, PackError, UnpackError};

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum PacketParseError {
    MissingCrc,
    Address(UnpackError),
}

impl fmt::Display for PacketParseError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            PacketParseError::MissingCrc => write!(f, "packet is missing crc8"),
            PacketParseError::Address(err) => write!(f, "failed to parse packet address: {err}"),
        }
    }
}

impl Error for PacketParseError {}

impl From<UnpackError> for PacketParseError {
    fn from(value: UnpackError) -> Self {
        PacketParseError::Address(value)
    }
}

pub struct PacketAssembler {
    buffer: Vec<u8>,
}

impl Default for PacketAssembler {
    fn default() -> Self {
        Self::new()
    }
}

impl PacketAssembler {
    pub fn new() -> Self {
        Self { buffer: Vec::new() }
    }

    pub fn write_u8(&mut self, value: u8) {
        self.buffer.push(value);
    }

    pub fn write_i8(&mut self, value: i8) {
        self.write_u8(value as u8);
    }

    pub fn write_u16_be(&mut self, value: u16) {
        self.buffer.extend_from_slice(&value.to_be_bytes());
    }

    pub fn write_i16_be(&mut self, value: i16) {
        self.write_u16_be(value as u16);
    }

    pub fn write_u32_be(&mut self, value: u32) {
        self.buffer.extend_from_slice(&value.to_be_bytes());
    }

    pub fn write_i32_be(&mut self, value: i32) {
        self.write_u32_be(value as u32);
    }

    pub fn write_u64_be(&mut self, value: u64) {
        self.buffer.extend_from_slice(&value.to_be_bytes());
    }

    pub fn write_i64_be(&mut self, value: i64) {
        self.write_u64_be(value as u64);
    }

    pub fn write_f32_be(&mut self, value: f32) {
        self.write_u32_be(value.to_bits());
    }

    pub fn write_f64_be(&mut self, value: f64) {
        self.write_u64_be(value.to_bits());
    }

    pub fn write_blob(&mut self, data: &[u8]) {
        self.buffer.extend_from_slice(data);
    }

    pub fn write_string(&mut self, value: &str) {
        self.write_blob(value.as_bytes());
        self.write_u8(0);
    }

    pub fn write_packed_value(
        &mut self,
        signed: bool,
        value: impl Into<BigInt>,
    ) -> Result<(), PackError> {
        let value = value.into();
        let required = crate::tools::pack_value(signed, value.clone())?.len();
        let start = self.buffer.len();
        self.buffer.resize(start + required, 0);
        pack_value_into(signed, value, &mut self.buffer, start)?;
        Ok(())
    }

    pub fn write_address(&mut self, value: i32) -> Result<(), PackError> {
        self.write_packed_value(true, i64::from(value))
    }

    pub fn write_addressing(&mut self, from: i32, to: i32) -> Result<(), PackError> {
        self.write_address(to)?;
        self.write_address(from)
    }

    pub fn append_crc8(&mut self) {
        let checksum = crc8(&self.buffer);
        self.write_u8(checksum);
    }

    pub fn done(&mut self) -> Vec<u8> {
        std::mem::take(&mut self.buffer)
    }
}

pub struct PacketReader {
    data: Vec<u8>,
    ep: usize,
}

impl PacketReader {
    pub fn new(data: Vec<u8>) -> Self {
        Self { data, ep: 0 }
    }

    pub fn from_slice(data: &[u8]) -> Self {
        Self::new(data.to_vec())
    }

    pub fn get_parse_state_index(&self) -> usize {
        self.ep
    }

    pub fn set_parse_state_index(&mut self, index: usize) {
        self.ep = index;
    }

    pub fn get_remaining_bytes(&self) -> usize {
        self.data.len().saturating_sub(self.ep)
    }

    pub fn read_sbyte(&mut self) -> Option<i8> {
        Some(self.read_ubyte()? as i8)
    }

    pub fn read_ubyte(&mut self) -> Option<u8> {
        let value = *self.data.get(self.ep)?;
        self.ep += 1;
        Some(value)
    }

    pub fn read_vuint(&mut self) -> Result<BigInt, UnpackError> {
        self.read_int(false).map(|(value, _)| value)
    }

    pub fn read_vsint(&mut self) -> Result<BigInt, UnpackError> {
        self.read_int(true).map(|(value, _)| value)
    }

    pub fn read_int(&mut self, signed: bool) -> Result<(BigInt, usize), UnpackError> {
        let start = self.ep;
        let (value, used) = unpack_value(signed, &self.data, start)?;
        self.ep += used;
        Ok((value, used))
    }

    pub fn read_blob(&mut self, length: usize) -> Option<Vec<u8>> {
        let end = self.ep.checked_add(length)?;
        let data = self.data.get(self.ep..end)?.to_vec();
        self.ep = end;
        Some(data)
    }

    pub fn read_blob_remain(&mut self) -> Vec<u8> {
        let data = self.data[self.ep..].to_vec();
        self.ep = self.data.len();
        data
    }

    pub fn read_string(&mut self) -> Result<String, PacketParseError> {
        if let Some(offset) = self.data[self.ep..].iter().position(|byte| *byte == 0) {
            let start = self.ep;
            let end = start + offset;
            let value = String::from_utf8(self.data[start..end].to_vec())
                .map_err(|_| PacketParseError::Address(UnpackError::IncompleteValue))?;
            self.ep = end + 1;
            Ok(value)
        } else {
            Err(PacketParseError::Address(UnpackError::IncompleteValue))
        }
    }

    pub fn read_i16_be(&mut self) -> Option<i16> {
        Some(self.read_u16_be()? as i16)
    }

    pub fn read_u16_be(&mut self) -> Option<u16> {
        let bytes: [u8; 2] = self.read_blob(2)?.try_into().ok()?;
        Some(u16::from_be_bytes(bytes))
    }

    pub fn read_i32_be(&mut self) -> Option<i32> {
        Some(self.read_u32_be()? as i32)
    }

    pub fn read_u32_be(&mut self) -> Option<u32> {
        let bytes: [u8; 4] = self.read_blob(4)?.try_into().ok()?;
        Some(u32::from_be_bytes(bytes))
    }

    pub fn read_i64_be(&mut self) -> Option<i64> {
        Some(self.read_u64_be()? as i64)
    }

    pub fn read_u64_be(&mut self) -> Option<u64> {
        let bytes: [u8; 8] = self.read_blob(8)?.try_into().ok()?;
        Some(u64::from_be_bytes(bytes))
    }

    pub fn read_f32_be(&mut self) -> Option<f32> {
        Some(f32::from_bits(self.read_u32_be()?))
    }

    pub fn read_f64_be(&mut self) -> Option<f64> {
        Some(f64::from_bits(self.read_u64_be()?))
    }

    pub fn has_unprocessed_bytes(&self) -> bool {
        self.ep < self.data.len()
    }
}

#[derive(Debug, Clone)]
pub struct ParsedUartBusPacket {
    pub raw_packet: Vec<u8>,
    pub from: i32,
    pub to: i32,
    pub payload: Vec<u8>,
    crc8: Option<u8>,
    valid_crc8: Option<u8>,
}

impl ParsedUartBusPacket {
    pub fn new(data: Vec<u8>) -> Result<Self, PacketParseError> {
        let mut ep = 0usize;
        let (to, used) = unpack_value(true, &data, ep)?;
        ep += used;
        let (from, used) = unpack_value(true, &data, ep)?;
        ep += used;
        let payload = data[ep..].to_vec();

        Ok(Self {
            raw_packet: data,
            from: from.try_into().unwrap_or(i32::MAX),
            to: to.try_into().unwrap_or(i32::MIN),
            payload,
            crc8: None,
            valid_crc8: None,
        })
    }

    pub fn from_raw_packet_with_crc(data: &[u8]) -> Result<Self, PacketParseError> {
        if data.is_empty() {
            return Err(PacketParseError::MissingCrc);
        }

        let mut packet = Self::new(data[..data.len() - 1].to_vec())?;
        packet.crc8 = Some(data[data.len() - 1]);
        Ok(packet)
    }

    pub fn get_crc8(&self) -> Option<u8> {
        self.crc8
    }

    pub fn set_crc8(&mut self, value: Option<u8>) {
        self.crc8 = value;
    }

    pub fn read_payload(&self) -> PacketReader {
        PacketReader::new(self.payload.clone())
    }

    pub fn get_packet_valid_crc8_value(&mut self) -> u8 {
        if self.valid_crc8.is_none() {
            self.valid_crc8 = Some(crc8(&self.raw_packet));
        }
        self.valid_crc8.unwrap()
    }

    pub fn is_valid(&mut self) -> bool {
        match self.crc8 {
            Some(expected) => self.get_packet_valid_crc8_value() == expected,
            None => true,
        }
    }

    pub fn to_short_user_text(&self) -> String {
        let mut text = format!(
            "From: {}, To: {}, payload: {}",
            self.from,
            self.to,
            crate::tools::format_colon_data(&self.payload)
        );

        if let Some(expected) = self.crc8 {
            text.push_str(&format!(", crc8: {expected}"));
            let valid = crc8(&self.raw_packet);
            if valid != expected {
                text.push_str(&format!(" != {valid}"));
            }
        }

        text
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn packet_reader_reads_string_and_blob() {
        let mut reader = PacketReader::from_slice(b"abc\0def");
        assert_eq!(reader.read_string().unwrap(), "abc");
        assert_eq!(reader.read_blob(3).unwrap(), b"def");
        assert!(!reader.has_unprocessed_bytes());
    }
}
