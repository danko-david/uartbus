use num_traits::ToPrimitive;
use std::error::Error;
use std::fmt;

use crate::packet::{PacketAssembler, PacketParseError, PacketReader};
use crate::rpc::types::{UInt16, UInt8, VSigned, VUnsigned};
use crate::tools::{PackError, UnpackError};

#[derive(Debug, Clone, PartialEq)]
pub enum RpcValue {
    Bool(bool),
    S8(i8),
    U8(u8),
    UInt8(UInt8),
    U16(u16),
    UInt16(UInt16),
    S16(i16),
    Bytes(Vec<u8>),
    String(String),
    VSigned(VSigned),
    VUnsigned(VUnsigned),
    Enum(usize),
    Struct(Vec<RpcValue>),
    Array(Vec<RpcValue>),
}

#[derive(Debug, Clone, PartialEq)]
pub enum RpcType {
    Bool,
    S8,
    U8,
    UInt8,
    U16,
    UInt16,
    S16,
    Bytes,
    String,
    VSigned,
    VUnsigned,
    Enum(Vec<String>),
    Struct(Vec<RpcType>),
    Array(Box<RpcType>),
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum RpcReadError {
    Packet(UnpackError),
    Utf8(std::string::FromUtf8Error),
    EnumIndexOutOfRange { index: usize, len: usize },
}

impl fmt::Display for RpcReadError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            RpcReadError::Packet(err) => write!(f, "packet read error: {err}"),
            RpcReadError::Utf8(err) => write!(f, "UTF-8 error: {err}"),
            RpcReadError::EnumIndexOutOfRange { index, len } => {
                write!(f, "enum index {index} is out of range for {len} values")
            }
        }
    }
}

impl Error for RpcReadError {}

impl From<PacketParseError> for RpcReadError {
    fn from(value: PacketParseError) -> Self {
        match value {
            PacketParseError::MissingCrc => RpcReadError::Packet(UnpackError::IncompleteValue),
            PacketParseError::Address(err) => RpcReadError::Packet(err),
        }
    }
}

impl From<UnpackError> for RpcReadError {
    fn from(value: UnpackError) -> Self {
        RpcReadError::Packet(value)
    }
}

impl From<std::string::FromUtf8Error> for RpcReadError {
    fn from(value: std::string::FromUtf8Error) -> Self {
        RpcReadError::Utf8(value)
    }
}

pub fn append_elements(
    assembler: &mut PacketAssembler,
    elements: &[RpcValue],
) -> Result<(), PackError> {
    for value in elements {
        append_value(assembler, value)?;
    }

    Ok(())
}

pub fn append_value(assembler: &mut PacketAssembler, value: &RpcValue) -> Result<(), PackError> {
    match value {
        RpcValue::Bool(value) => assembler.write_u8(u8::from(*value)),
        RpcValue::S8(value) => assembler.write_i8(*value),
        RpcValue::U8(value) => assembler.write_u8(*value),
        RpcValue::UInt8(value) => assembler.write_u8(value.value),
        RpcValue::U16(value) => assembler.write_u16_be(*value),
        RpcValue::UInt16(value) => assembler.write_u16_be(value.value),
        RpcValue::S16(value) => assembler.write_i16_be(*value),
        RpcValue::Bytes(value) => assembler.write_blob(value),
        RpcValue::String(value) => assembler.write_string(value),
        RpcValue::VSigned(value) => assembler.write_packed_value(true, value.value.clone())?,
        RpcValue::VUnsigned(value) => assembler.write_packed_value(true, value.value.clone())?,
        RpcValue::Enum(index) => assembler.write_packed_value(false, *index as i128)?,
        RpcValue::Struct(values) => append_elements(assembler, values)?,
        RpcValue::Array(values) => {
            for value in values {
                append_value(assembler, value)?;
            }
        }
    }

    Ok(())
}

pub fn to_packet(to: i32, from: i32, elements: &[RpcValue]) -> Result<Vec<u8>, PackError> {
    let mut assembler = PacketAssembler::new();
    assembler.write_addressing(from, to)?;
    append_elements(&mut assembler, elements)?;
    assembler.append_crc8();
    Ok(assembler.done())
}

pub fn read_type(reader: &mut PacketReader, ty: &RpcType) -> Result<RpcValue, RpcReadError> {
    match ty {
        RpcType::Bool => Ok(RpcValue::Bool(
            reader
                .read_sbyte()
                .ok_or(RpcReadError::Packet(UnpackError::IncompleteValue))?
                != 0,
        )),
        RpcType::S8 => {
            Ok(RpcValue::S8(reader.read_sbyte().ok_or(
                RpcReadError::Packet(UnpackError::IncompleteValue),
            )?))
        }
        RpcType::U8 => {
            Ok(RpcValue::U8(reader.read_ubyte().ok_or(
                RpcReadError::Packet(UnpackError::IncompleteValue),
            )?))
        }
        RpcType::UInt8 => {
            Ok(RpcValue::UInt8(UInt8::new(reader.read_ubyte().ok_or(
                RpcReadError::Packet(UnpackError::IncompleteValue),
            )?)))
        }
        RpcType::U16 => {
            Ok(RpcValue::U16(reader.read_u16_be().ok_or(
                RpcReadError::Packet(UnpackError::IncompleteValue),
            )?))
        }
        RpcType::UInt16 => {
            Ok(RpcValue::UInt16(UInt16::new(reader.read_u16_be().ok_or(
                RpcReadError::Packet(UnpackError::IncompleteValue),
            )?)))
        }
        RpcType::S16 => {
            Ok(RpcValue::S16(reader.read_i16_be().ok_or(
                RpcReadError::Packet(UnpackError::IncompleteValue),
            )?))
        }
        RpcType::Bytes => Ok(RpcValue::Bytes(reader.read_blob_remain())),
        RpcType::String => Ok(RpcValue::String(reader.read_string()?)),
        RpcType::VSigned => Ok(RpcValue::VSigned(VSigned::new(reader.read_vsint()?))),
        RpcType::VUnsigned => Ok(RpcValue::VUnsigned(VUnsigned::new(reader.read_vuint()?))),
        RpcType::Enum(values) => {
            let index = usize::try_from(
                reader
                    .read_vuint()?
                    .to_u64()
                    .ok_or(RpcReadError::Packet(UnpackError::IncompleteValue))?,
            )
            .map_err(|_| RpcReadError::Packet(UnpackError::IncompleteValue))?;

            if index >= values.len() {
                return Err(RpcReadError::EnumIndexOutOfRange {
                    index,
                    len: values.len(),
                });
            }

            Ok(RpcValue::Enum(index))
        }
        RpcType::Struct(types) => {
            let mut values = Vec::with_capacity(types.len());
            for ty in types {
                values.push(read_type(reader, ty)?);
            }
            Ok(RpcValue::Struct(values))
        }
        RpcType::Array(item_type) => {
            let mut values = Vec::new();
            while reader.has_unprocessed_bytes() {
                values.push(read_type(reader, item_type)?);
            }
            Ok(RpcValue::Array(values))
        }
    }
}
