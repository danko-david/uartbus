//! A device on the bus and the calls of its RPC functions.

use std::fmt;
use std::time::Duration;

use crate::packet::{PacketAssembler, PacketReader};
use crate::rpc::bus::{TransactionError, UartBus};
use crate::rpc::tools::{append_elements, read_type, RpcType, RpcValue};

/// Error numbers the devices and services answer with (the Linux values).
pub mod errno {
    pub const ENOENT: u8 = 2;
    pub const EIO: u8 = 5;
    pub const ENXIO: u8 = 6;
    pub const EAGAIN: u8 = 11;
    pub const EACCES: u8 = 13;
    pub const EEXIST: u8 = 17;
    pub const ENOTDIR: u8 = 20;
    pub const EMSGSIZE: u8 = 90;
    pub const EOPNOTSUPP: u8 = 95;
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum RpcError {
    /// No answer arrived in time.
    Transaction(String),
    /// The function answered with an error number.
    Posix(u8),
    Other(String),
}

impl fmt::Display for RpcError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            RpcError::Transaction(msg) => write!(f, "TransactionException: {msg}"),
            RpcError::Posix(errno) => write!(f, "PosixErrnoException: errno {errno}"),
            RpcError::Other(msg) => f.write_str(msg),
        }
    }
}

impl std::error::Error for RpcError {}

impl From<TransactionError> for RpcError {
    fn from(err: TransactionError) -> Self {
        RpcError::Transaction(err.0)
    }
}

#[derive(Debug, Clone, PartialEq)]
pub enum UbRet {
    /// The function never answers (eg. reset), nothing is waited for.
    NoReturn,
    /// The function answers without value.
    Void,
    Value(RpcType),
}

/// Description of a remote function.
#[derive(Debug, Clone, PartialEq)]
pub struct UbFunction {
    /// Index of the function in its namespace.
    pub index: u8,
    pub ret: UbRet,
    /// The answer starts with an error number byte (0: success).
    pub posix: bool,
    /// Safe to call again when the request or the answer may have been lost.
    pub retransmittable: bool,
}

impl UbFunction {
    pub const fn new(index: u8, ret: UbRet) -> Self {
        Self {
            index,
            ret,
            posix: false,
            retransmittable: false,
        }
    }

    pub const fn posix(mut self) -> Self {
        self.posix = true;
        self
    }

    pub const fn retransmittable(mut self) -> Self {
        self.retransmittable = true;
        self
    }
}

/// Reads the answer of a function from the bytes after the path.
pub fn extract_or_throw_result(
    function: &UbFunction,
    data: &[u8],
) -> Result<Option<RpcValue>, RpcError> {
    let mut reader = PacketReader::from_slice(data);

    if function.posix {
        let err = reader
            .read_ubyte()
            .ok_or_else(|| RpcError::Other("Answer without error number".to_string()))?;
        if err != 0 {
            return Err(RpcError::Posix(err));
        }
    }

    match &function.ret {
        UbRet::NoReturn | UbRet::Void => Ok(None),
        UbRet::Value(ty) => read_type(&mut reader, ty)
            .map(Some)
            .map_err(|err| RpcError::Other(format!("Unextractable returning value: {err}"))),
    }
}

/// Standard namespaces of a device.
pub const NS_BUS: u8 = 1;
pub const NS_BOOTLOADER: u8 = 2;
pub const NS_APP: u8 = 32;
pub const NS_REFLECT: u8 = 255;

/// Variables of the bootloader (host program).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum UbBootloaderVariable {
    IsApplicationRunning = 0,
    IsSignalingSos = 1,
    ResetFlag = 2,
}

const NS_POWER: [u8; 2] = [NS_BOOTLOADER, 0];
const NS_FLASH: [u8; 2] = [NS_BOOTLOADER, 4];

#[derive(Clone)]
pub struct UartBusDevice {
    bus: UartBus,
    address: i32,
    pub timeout: Duration,
    /// How many times a retransmittable call is sent before giving up.
    pub retry_count: u32,
}

impl UartBusDevice {
    pub fn new(bus: UartBus, address: i32) -> Self {
        Self {
            bus,
            address,
            timeout: Duration::from_millis(100),
            retry_count: 5,
        }
    }

    pub fn get_address(&self) -> i32 {
        self.address
    }

    pub fn get_bus(&self) -> &UartBus {
        &self.bus
    }

    /// Calls the function of the namespace `ns_path` with the parameters.
    pub fn call(
        &self,
        ns_path: &[u8],
        function: &UbFunction,
        params: &[RpcValue],
    ) -> Result<Option<RpcValue>, RpcError> {
        let mut path = ns_path.to_vec();
        path.push(function.index);

        let mut assembler = PacketAssembler::new();
        assembler.write_blob(&path);
        append_elements(&mut assembler, params).map_err(|e| RpcError::Other(e.to_string()))?;
        let data = assembler.done();

        let mut last_error = None;
        for _ in 0..self.retry_count {
            let transaction = self
                .bus
                .new_transaction(self.address, &path, &data, true)
                .map_err(|e| RpcError::Other(format!("Can't send request: {e}")))?;

            if function.ret == UbRet::NoReturn {
                return Ok(None);
            }

            match transaction.ensure_response(self.timeout, None) {
                Ok(response) => {
                    //the answer is: 0, path, values
                    return extract_or_throw_result(function, &response[path.len() + 1..]);
                }
                Err(err) => {
                    if !function.retransmittable {
                        return Err(err.into());
                    }
                    last_error = Some(err);
                }
            }
        }

        Err(RpcError::Transaction(format!(
            "Device not responded after {} attempt.{}",
            self.retry_count,
            last_error.map_or(String::new(), |e| format!(" {e}"))
        )))
    }

    fn call_value(
        &self,
        ns_path: &[u8],
        function: &UbFunction,
        params: &[RpcValue],
    ) -> Result<RpcValue, RpcError> {
        self.call(ns_path, function, params)?
            .ok_or_else(|| RpcError::Other("Function returned no value".to_string()))
    }

    /***************************** bus functions (1) *****************************/

    pub fn ping(&self) -> Result<(), RpcError> {
        self.call(
            &[NS_BUS],
            &UbFunction::new(0, UbRet::Void).retransmittable(),
            &[],
        )
        .map(|_| ())
    }

    /************************** bootloader functions (2) *************************/

    /// Reset using the watchdog timer.
    pub fn hardware_reset(&self) -> Result<(), RpcError> {
        self.call(&NS_POWER, &UbFunction::new(0, UbRet::NoReturn), &[])
            .map(|_| ())
    }

    /// Reset by jumping to the reset vector.
    pub fn software_reset(&self) -> Result<(), RpcError> {
        self.call(&NS_POWER, &UbFunction::new(1, UbRet::NoReturn), &[])
            .map(|_| ())
    }

    pub fn get_var(&self, var: UbBootloaderVariable) -> Result<i8, RpcError> {
        match self.call_value(
            &[NS_BOOTLOADER],
            &UbFunction::new(1, UbRet::Value(RpcType::S8)).retransmittable(),
            &[RpcValue::Enum(var as usize)],
        )? {
            RpcValue::S8(value) => Ok(value),
            other => Err(RpcError::Other(format!("Unexpected value: {other:?}"))),
        }
    }

    pub fn set_var(&self, var: UbBootloaderVariable, value: i8) -> Result<(), RpcError> {
        self.call(
            &[NS_BOOTLOADER],
            &UbFunction::new(2, UbRet::Void),
            &[RpcValue::Enum(var as usize), RpcValue::S8(value)],
        )
        .map(|_| ())
    }

    /// Returns the address the code was read from and the code.
    pub fn read_program_code(&self, address: u16, length: u8) -> Result<(u16, Vec<u8>), RpcError> {
        let ret = UbRet::Value(RpcType::Struct(vec![RpcType::U16, RpcType::Bytes]));
        match self.call_value(
            &[NS_BOOTLOADER],
            &UbFunction::new(3, ret).retransmittable(),
            &[RpcValue::U16(address), RpcValue::U8(length)],
        )? {
            RpcValue::Struct(fields) => match fields.as_slice() {
                [RpcValue::U16(address), RpcValue::Bytes(code)] => Ok((*address, code.clone())),
                other => Err(RpcError::Other(format!("Unexpected value: {other:?}"))),
            },
            other => Err(RpcError::Other(format!("Unexpected value: {other:?}"))),
        }
    }

    /************************ flash functions (2:4) *************************/

    /// 0: the device is not under code upload
    pub fn flash_get_stage(&self) -> Result<i8, RpcError> {
        match self.call_value(
            &NS_FLASH,
            &UbFunction::new(0, UbRet::Value(RpcType::S8))
                .posix()
                .retransmittable(),
            &[],
        )? {
            RpcValue::S8(value) => Ok(value),
            other => Err(RpcError::Other(format!("Unexpected value: {other:?}"))),
        }
    }

    pub fn flash_start(&self) -> Result<(), RpcError> {
        self.call(&NS_FLASH, &UbFunction::new(1, UbRet::Void).posix(), &[])
            .map(|_| ())
    }

    fn address_value(value: RpcValue) -> Result<u16, RpcError> {
        match value {
            RpcValue::U16(address) => Ok(address),
            other => Err(RpcError::Other(format!("Unexpected value: {other:?}"))),
        }
    }

    /// The address the next pushed code must start at.
    pub fn flash_get_next_address(&self) -> Result<u16, RpcError> {
        Self::address_value(self.call_value(
            &NS_FLASH,
            &UbFunction::new(2, UbRet::Value(RpcType::U16)).retransmittable(),
            &[],
        )?)
    }

    /// Returns the next address.
    pub fn flash_push_code(&self, start_address: u16, data: &[u8]) -> Result<u16, RpcError> {
        Self::address_value(self.call_value(
            &NS_FLASH,
            &UbFunction::new(3, UbRet::Value(RpcType::U16)).posix(),
            &[RpcValue::U16(start_address), RpcValue::Bytes(data.to_vec())],
        )?)
    }

    pub fn flash_commit(&self) -> Result<(), RpcError> {
        self.call(&NS_FLASH, &UbFunction::new(4, UbRet::Void).posix(), &[])
            .map(|_| ())
    }
}
