//! Serving RPC functions on the bus from this process (the PC acts like a device).

use std::sync::Arc;

use crate::log::{log, LogLevel};
use crate::packet::{PacketAssembler, PacketReader, ParsedUartBusPacket};
use crate::rpc::bus::UartBus;
use crate::rpc::tools::{append_elements, RpcValue};

const LOG: &str = "UartbusServiceBridge";

pub enum ServiceReply {
    /// Nothing is answered.
    NoReturn,
    /// Answered without value.
    Void,
    Value(RpcValue),
    /// The function selected a sub namespace, the dispatch continues in it with the next byte.
    Namespace(Box<dyn UbService>),
}

pub enum ServiceError {
    Posix(u8),
    /// Not reported to the caller (it runs into timeout), only logged.
    Internal(String),
}

pub struct ServiceCall {
    /// The answer starts with an error number byte.
    pub posix: bool,
    pub result: Result<ServiceReply, ServiceError>,
}

impl ServiceCall {
    pub fn plain(result: Result<ServiceReply, ServiceError>) -> Self {
        Self {
            posix: false,
            result,
        }
    }

    pub fn posix(result: Result<ServiceReply, ServiceError>) -> Self {
        Self {
            posix: true,
            result,
        }
    }
}

/// A namespace of functions selectable with an index byte.
pub trait UbService: Send + Sync {
    /// Calls the function with the given index; it reads its parameters from the reader.
    /// None, if there is no function with this index.
    fn call(&self, index: u8, params: &mut PacketReader) -> Option<ServiceCall>;
}

pub type ResponseHandler = Box<dyn Fn(&[u8]) + Send + Sync>;

pub struct UartbusServiceBridge {
    service: Arc<dyn UbService>,
    response_handler: ResponseHandler,
    /// Only the packets sent to this address are served (None: all).
    target_address: Option<i32>,
    /// Only the requests starting with this path are served.
    path: Vec<u8>,
}

impl UartbusServiceBridge {
    pub fn new(
        service: Arc<dyn UbService>,
        response_handler: ResponseHandler,
        target_address: Option<i32>,
        path: &[u8],
    ) -> Self {
        Self {
            service,
            response_handler,
            target_address,
            path: path.to_vec(),
        }
    }

    /// Serves the functions on the bus: answers the requests that are sent to
    /// `service_address` and start with `path`.
    pub fn attach_service_to(
        service: Arc<dyn UbService>,
        bus: &UartBus,
        service_address: Option<i32>,
        path: &[u8],
    ) -> Arc<Self> {
        let sender = bus.clone();
        let bridge = Arc::new(Self::new(
            service,
            Box::new(move |packet| {
                if let Err(err) = sender.send_raw_packet(packet) {
                    log(
                        LOG,
                        LogLevel::Warning,
                        &format!("Can't send response: {err}"),
                    );
                }
            }),
            service_address,
            path,
        ));

        let feed = Arc::clone(&bridge);
        bus.add_unrelated_packet_listener(Arc::new(move |raw| feed.feed_packet(raw)));
        bridge
    }

    fn response_packet(&self, request: &ParsedUartBusPacket, path_end: usize, values: &[RpcValue]) {
        let mut assembler = PacketAssembler::new();
        let built = assembler
            .write_addressing(request.to, request.from)
            .and_then(|_| {
                assembler.write_u8(0);
                assembler.write_blob(&request.payload[..path_end]);
                append_elements(&mut assembler, values)
            });

        match built {
            Ok(()) => {
                assembler.append_crc8();
                (self.response_handler)(&assembler.done());
            }
            Err(err) => log(
                LOG,
                LogLevel::Warning,
                &format!("Can't build response: {err}"),
            ),
        }
    }

    /// Handles a valid packet (without crc8).
    pub fn feed_packet(&self, raw: &[u8]) {
        let Ok(request) = ParsedUartBusPacket::new(raw.to_vec()) else {
            return;
        };

        if self.target_address.is_some_and(|addr| addr != request.to) {
            return;
        }

        if !request.payload.starts_with(&self.path) {
            return;
        }

        let warn = |what: &str| {
            log(
                LOG,
                LogLevel::Warning,
                &format!(
                    "Exception occured while servicing uartbus request `{}`: {what}",
                    request.to_short_user_text()
                ),
            )
        };

        let mut ep = self.path.len();
        let mut sub: Option<Box<dyn UbService>> = None;

        loop {
            let Some(&index) = request.payload.get(ep) else {
                warn("request ends before the function index");
                return;
            };
            ep += 1;
            //the answer repeats the request up to (and including) this index
            let path_end = ep;

            let mut reader = PacketReader::from_slice(&request.payload[ep..]);
            let service: &dyn UbService = match &sub {
                Some(service) => service.as_ref(),
                None => self.service.as_ref(),
            };

            let Some(call) = service.call(index, &mut reader) else {
                return;
            };
            ep += reader.get_parse_state_index();

            match call.result {
                Ok(ServiceReply::NoReturn) => return,
                Ok(ServiceReply::Void) => {
                    let errno: &[RpcValue] = if call.posix { &[RpcValue::U8(0)] } else { &[] };
                    self.response_packet(&request, path_end, errno);
                    return;
                }
                Ok(ServiceReply::Value(value)) => {
                    if call.posix {
                        self.response_packet(&request, path_end, &[RpcValue::U8(0), value]);
                    } else {
                        self.response_packet(&request, path_end, &[value]);
                    }
                    return;
                }
                Ok(ServiceReply::Namespace(next)) => sub = Some(next),
                Err(ServiceError::Posix(errno)) => {
                    if call.posix {
                        self.response_packet(&request, path_end, &[RpcValue::U8(errno)]);
                    } else {
                        warn(&format!(
                            "errno {errno} from a function that can't report it"
                        ));
                    }
                    return;
                }
                Err(ServiceError::Internal(message)) => {
                    warn(&message);
                    return;
                }
            }
        }
    }
}

/// Reads one parameter; a request that is too short is an internal error.
pub fn read_param(
    params: &mut PacketReader,
    ty: &crate::rpc::tools::RpcType,
) -> Result<RpcValue, ServiceError> {
    crate::rpc::tools::read_type(params, ty)
        .map_err(|err| ServiceError::Internal(format!("can't read parameter: {err}")))
}

/// A string served as a namespace: 1: length, 2: part (from, length).
pub struct UbString {
    bytes: Vec<u8>,
}

impl UbString {
    pub fn new(string: &str) -> Self {
        Self {
            bytes: string.as_bytes().to_vec(),
        }
    }

    pub fn get_string_part(&self, from: usize, length: usize) -> &[u8] {
        let from = from.min(self.bytes.len());
        let to = from.saturating_add(length).min(self.bytes.len());
        &self.bytes[from..to]
    }
}

impl UbService for UbString {
    fn call(&self, index: u8, params: &mut PacketReader) -> Option<ServiceCall> {
        use crate::rpc::tools::RpcType;
        use crate::rpc::types::VSigned;
        use num_traits::ToPrimitive;

        let as_usize = |value: RpcValue| match value {
            RpcValue::VUnsigned(v) => v.value.to_usize().unwrap_or(usize::MAX),
            _ => 0,
        };

        Some(ServiceCall::plain(match index {
            1 => Ok(ServiceReply::Value(RpcValue::VSigned(VSigned::new(
                self.bytes.len() as u64,
            )))),
            2 => (|| {
                let from = as_usize(read_param(params, &RpcType::VUnsigned)?);
                let length = as_usize(read_param(params, &RpcType::VUnsigned)?);
                Ok(ServiceReply::Value(RpcValue::Bytes(
                    self.get_string_part(from, length).to_vec(),
                )))
            })(),
            _ => return None,
        }))
    }
}
