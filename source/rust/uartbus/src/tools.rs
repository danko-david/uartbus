use num_bigint::{BigInt, Sign};
use std::error::Error;
use std::fmt;

use crate::packet::ParsedUartBusPacket;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum PackError {
    NegativeUnsignedValue,
    StartIndexOutOfBounds,
    DestinationTooSmall { required: usize },
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum UnpackError {
    IncompleteValue,
}

impl fmt::Display for PackError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            PackError::NegativeUnsignedValue => write!(f, "value must be unsigned"),
            PackError::StartIndexOutOfBounds => write!(f, "start index is out of bounds"),
            PackError::DestinationTooSmall { required } => {
                write!(
                    f,
                    "destination buffer is too small; required {required} bytes"
                )
            }
        }
    }
}

impl fmt::Display for UnpackError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            UnpackError::IncompleteValue => write!(f, "incomplete variable-length value"),
        }
    }
}

impl Error for PackError {}
impl Error for UnpackError {}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PacketFormattingMode {
    RawColon,
    RawColonWithVerify,
    ParsedPacket,
}

pub fn crc8(data: &[u8]) -> u8 {
    crc8_prefix(data, data.len())
}

pub fn crc8_prefix(data: &[u8], length: usize) -> u8 {
    let mut crc = 0u8;

    for &byte in data.iter().take(length) {
        let v = byte ^ crc;
        crc = 0;
        if (v & 0x01) != 0 {
            crc ^= 0x5e;
        }
        if (v & 0x02) != 0 {
            crc ^= 0xbc;
        }
        if (v & 0x04) != 0 {
            crc ^= 0x61;
        }
        if (v & 0x08) != 0 {
            crc ^= 0xc2;
        }
        if (v & 0x10) != 0 {
            crc ^= 0x9d;
        }
        if (v & 0x20) != 0 {
            crc ^= 0x23;
        }
        if (v & 0x40) != 0 {
            crc ^= 0x46;
        }
        if (v & 0x80) != 0 {
            crc ^= 0x8c;
        }
    }

    crc
}

/// Colon separated decimal bytes. Like the Java tools, takes the low 8 bits of
/// every number, so `-1` is 255.
pub fn parse_colon_data(data: &str) -> Result<Vec<u8>, std::num::ParseIntError> {
    data.split(':')
        .map(|number| number.parse::<i32>().map(|value| (value & 0xff) as u8))
        .collect()
}

pub fn format_colon_data(data: &[u8]) -> String {
    data.iter()
        .map(|byte| byte.to_string())
        .collect::<Vec<_>>()
        .join(":")
}

pub fn format_colon_data_with_validation(data: &[u8]) -> String {
    if !data.is_empty() && crc8_prefix(data, data.len() - 1) == *data.last().unwrap() {
        format_colon_data(data)
    } else {
        format!("!{}", format_colon_data(data))
    }
}

pub fn format_packet_with_mode(mode: PacketFormattingMode, data: &[u8]) -> String {
    match mode {
        PacketFormattingMode::ParsedPacket => {
            match ParsedUartBusPacket::from_raw_packet_with_crc(data) {
                Ok(packet) => packet.to_short_user_text(),
                Err(_) => format_colon_data_with_validation(data),
            }
        }
        PacketFormattingMode::RawColon => format_colon_data(data),
        PacketFormattingMode::RawColonWithVerify => format_colon_data_with_validation(data),
    }
}

pub fn pack_i128(signed: bool, value: i128) -> Result<Vec<u8>, PackError> {
    pack_value(signed, BigInt::from(value))
}

pub fn pack_u128(value: u128) -> Result<Vec<u8>, PackError> {
    pack_value(false, BigInt::from(value))
}

pub fn pack_value(signed: bool, value: BigInt) -> Result<Vec<u8>, PackError> {
    let (negative, magnitude) = magnitude_bytes_for_pack(signed, &value)?;
    let required = calc_pack_req_bytes_bytes(negative.is_some(), &magnitude);
    let mut dst = vec![0; required];
    pack_value_magnitude(negative, &magnitude, &mut dst, 0)?;
    Ok(dst)
}

pub fn pack_value_into(
    signed: bool,
    value: BigInt,
    dst: &mut [u8],
    start_index: usize,
) -> Result<usize, PackError> {
    let (negative, magnitude) = magnitude_bytes_for_pack(signed, &value)?;
    let required = calc_pack_req_bytes_bytes(negative.is_some(), &magnitude);

    if start_index > dst.len() {
        return Err(PackError::StartIndexOutOfBounds);
    }

    let end = start_index
        .checked_add(required)
        .ok_or(PackError::DestinationTooSmall { required })?;

    if end > dst.len() {
        return Err(PackError::DestinationTooSmall { required });
    }

    pack_value_magnitude(negative, &magnitude, dst, start_index)?;
    Ok(required)
}

fn magnitude_bytes_for_pack(
    signed: bool,
    value: &BigInt,
) -> Result<(Option<bool>, Vec<u8>), PackError> {
    if !signed && value.sign() == Sign::Minus {
        return Err(PackError::NegativeUnsignedValue);
    }

    let magnitude = if signed && value.sign() == Sign::Minus {
        -value - BigInt::from(1)
    } else {
        value.clone()
    };

    Ok((
        signed.then_some(value.sign() == Sign::Minus),
        strip_leading_zero(magnitude.to_bytes_be().1),
    ))
}

fn strip_leading_zero(mut bytes: Vec<u8>) -> Vec<u8> {
    if bytes.len() > 1 && bytes[0] == 0 {
        bytes.remove(0);
    }
    bytes
}

fn calc_pack_req_bytes_bytes(signed: bool, value: &[u8]) -> usize {
    if value.is_empty() {
        return 1;
    }

    let mut required = 9 * (value.len() - 1) + (value.len() - 1) / 7;
    let mut last = value[0] as usize;
    let msb = (last & 0x80) != 0;

    while last != 0 {
        required += 1;
        last >>= 1;
    }

    if msb {
        required += 1;
    }

    if signed {
        required += 1;
    }

    (required + 8) / 8
}

fn pack_value_magnitude(
    negative: Option<bool>,
    magnitude: &[u8],
    dst: &mut [u8],
    start_index: usize,
) -> Result<usize, PackError> {
    if magnitude.is_empty() {
        if start_index >= dst.len() {
            return Err(PackError::StartIndexOutOfBounds);
        }

        dst[start_index] = if negative == Some(true) { 0x40 } else { 0 };
        return Ok(1);
    }

    let required = calc_pack_req_bytes_bytes(negative.is_some(), magnitude);
    let end = start_index
        .checked_add(required)
        .ok_or(PackError::DestinationTooSmall { required })?;

    if end > dst.len() {
        return Err(PackError::DestinationTooSmall { required });
    }

    let mut offset = 0i32;
    let mut index = magnitude.len() as isize - 1;

    for output_index in (0..required).rev() {
        let mut value = 0usize;

        if offset == 0 {
            if index >= 0 {
                value = magnitude[index as usize] as usize;
            }
        } else if index + 1 >= 0 && ((index + 1) as usize) < magnitude.len() {
            value = (magnitude[(index + 1) as usize] as usize) >> (8 - offset);
            if index >= 0 {
                value |= (magnitude[index as usize] as usize) << offset;
            }
        }

        value &= 0x7f;
        offset += 1;

        if offset == 8 {
            offset = 0;
            index += 1;
        }

        if output_index != required - 1 {
            value |= 0x80;
        }

        if negative == Some(true) && output_index == 0 {
            value |= 0x40;
        }

        dst[start_index + output_index] = value as u8;
        index -= 1;
    }

    Ok(required)
}

pub fn unpack_value(
    signed: bool,
    data: &[u8],
    start_index: usize,
) -> Result<(BigInt, usize), UnpackError> {
    let mut used = 1usize;

    loop {
        match data.get(start_index + used - 1) {
            None => return Err(UnpackError::IncompleteValue),
            Some(byte) if byte & 0x80 == 0 => break,
            Some(_) => used += 1,
        }
    }

    let first_cut = if signed { 0x3f } else { 0x7f };
    let mut magnitude = BigInt::from(0);

    for offset in 0..used {
        let mask = if offset == 0 { first_cut } else { 0x7f };
        let digit = (data[start_index + offset] & mask) as u64;
        magnitude = magnitude * BigInt::from(128) + BigInt::from(digit);
    }

    let result = if signed && (data[start_index] & 0x40) != 0 {
        -magnitude - BigInt::from(1)
    } else {
        magnitude
    };

    Ok((result, used))
}

pub fn get_valid_packet(data: &[u8]) -> Option<Vec<u8>> {
    if !data.is_empty() && crc8_prefix(data, data.len() - 1) == data[data.len() - 1] {
        Some(data[..data.len() - 1].to_vec())
    } else {
        None
    }
}

pub fn is_packet_crc8_valid(data: &[u8]) -> bool {
    !data.is_empty() && crc8_prefix(data, data.len() - 1) == data[data.len() - 1]
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn crc8_empty_prefix() {
        assert_eq!(crc8(&[]), 0);
    }
}
