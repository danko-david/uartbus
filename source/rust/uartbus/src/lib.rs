//! UARTBus PC side library: packet encoding, the RPC server that shares the
//! bus gateway between programs, bus and device access, and the `ub` commands.

pub mod apps;
pub mod base64;
pub mod cli;
pub mod connector;
pub mod console;
pub mod framing;
pub mod ihex;
pub mod log;
pub mod packet;
pub mod rpc;
pub mod time;
pub mod tools;

pub use connector::{EscapedStreamPacketConnector, UartbusPacketConnector};
pub use console::{ConsoleInquiry, ConsoleResponse, UartbusConsoleEnv};
pub use framing::{frame_bytes, FramedPacketCutter};
pub use ihex::{CodeSegment, IntelHexError, IntelHexFile, IntelHexLine, RecordType};
pub use packet::{PacketAssembler, PacketReader, ParsedUartBusPacket};
pub use rpc::types::{UInt16, UInt8, VSigned, VUnsigned};
pub use rpc::{
    append_elements, read_type, to_packet, RpcReadError, RpcType, RpcValue, TcpRpcClient,
};
pub use tools::{
    format_colon_data, format_colon_data_with_validation, format_packet_with_mode,
    get_valid_packet, is_packet_crc8_valid, pack_i128, pack_u128, pack_value, parse_colon_data,
    unpack_value, PackError, PacketFormattingMode, UnpackError,
};
