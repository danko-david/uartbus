//! A device simulated in the test process: the standard namespaces of the
//! host program (source/uc/bootloader/ub_bootloader.c) with a flash memory.

use std::sync::{Arc, Mutex};

use uartbus::packet::PacketReader;
use uartbus::rpc::device::errno;
use uartbus::rpc::service::{
    read_param, ServiceCall, ServiceError, ServiceReply, UartbusServiceBridge, UbService,
};
use uartbus::rpc::{to_packet, RpcType, RpcValue, UartBus};

pub const APP_START_ADDRESS: u16 = 0x2000;

pub struct DeviceState {
    pub memory: Vec<u8>,
    pub app_run: bool,
    pub flash_stage: u8,
    pub next_address: u16,
    pub resets: u32,
    pub led: bool,
    /// requests that were executed
    pub requests: u32,
    /// every n-th answer is not sent (0: all sent), as if it was lost on the bus
    pub lose_every: u32,
    /// reading the code gives a wrong byte at this address
    pub broken_cell: Option<u16>,
}

#[derive(Clone, Copy)]
enum Namespace {
    Root,
    Bus,
    Bootloader,
    Power,
    Flash,
}

#[derive(Clone)]
struct DeviceNs {
    namespace: Namespace,
    address: i32,
    bus: UartBus,
    state: Arc<Mutex<DeviceState>>,
}

fn byte(params: &mut PacketReader) -> Result<u8, ServiceError> {
    match read_param(params, &RpcType::U8)? {
        RpcValue::U8(v) => Ok(v),
        _ => unreachable!(),
    }
}

fn word(params: &mut PacketReader) -> Result<u16, ServiceError> {
    match read_param(params, &RpcType::U16)? {
        RpcValue::U16(v) => Ok(v),
        _ => unreachable!(),
    }
}

impl DeviceNs {
    fn sub(&self, namespace: Namespace) -> Result<ServiceReply, ServiceError> {
        Ok(ServiceReply::Namespace(Box::new(DeviceNs {
            namespace,
            ..self.clone()
        })))
    }

    /// The answer of an executed function, unless it is its turn to get lost.
    fn answer(&self, bytes: Vec<u8>) -> Result<ServiceReply, ServiceError> {
        let mut state = self.state.lock().unwrap();
        state.requests += 1;
        if state.lose_every != 0 && state.requests % state.lose_every == 0 {
            return Err(ServiceError::Internal("answer lost (simulated)".into()));
        }
        Ok(ServiceReply::Value(RpcValue::Bytes(bytes)))
    }

    fn reset(&self) -> Result<ServiceReply, ServiceError> {
        {
            let mut state = self.state.lock().unwrap();
            state.resets += 1;
            //after watchdog reset the application is not started
            state.app_run = false;
            state.flash_stage = 0;
        }
        //the "online" broadcast of the starting device: reset flags (watchdog), has application
        let online = to_packet(-1, self.address, &[RpcValue::Bytes(vec![0, 8, 1])]).unwrap();
        self.bus.send_raw_packet(&online).unwrap();
        Ok(ServiceReply::NoReturn)
    }

    fn serve(&self, index: u8, params: &mut PacketReader) -> Result<ServiceReply, ServiceError> {
        let state = || self.state.lock().unwrap();

        match (self.namespace, index) {
            (Namespace::Root, 1) => self.sub(Namespace::Bus),
            (Namespace::Root, 2) => self.sub(Namespace::Bootloader),

            //ping
            (Namespace::Bus, 0) => self.answer(vec![]),
            //replay
            (Namespace::Bus, 1) => self.answer(params.read_blob_remain()),
            //user led: 0 off, 1 on, 2 toggle
            (Namespace::Bus, 2) => {
                let led = match byte(params)? {
                    0 => false,
                    1 => true,
                    _ => !state().led,
                };
                state().led = led;
                self.answer(vec![u8::from(led)])
            }

            (Namespace::Bootloader, 0) => self.sub(Namespace::Power),
            //get var
            (Namespace::Bootloader, 1) => {
                let value = match byte(params)? {
                    0 => u8::from(state().app_run),
                    _ => 0,
                };
                self.answer(vec![value])
            }
            //set var
            (Namespace::Bootloader, 2) => {
                let var = byte(params)?;
                let value = byte(params)?;
                if var == 0 {
                    state().app_run = value != 0;
                }
                self.answer(vec![0])
            }
            //read code
            (Namespace::Bootloader, 3) => {
                let address = word(params)?;
                let length = byte(params)?.min(32);
                let mut reply = address.to_be_bytes().to_vec();
                {
                    let state = state();
                    for a in address..address + u16::from(length) {
                        let cell = state.memory[usize::from(a)];
                        reply.push(if state.broken_cell == Some(a) {
                            !cell
                        } else {
                            cell
                        });
                    }
                }
                self.answer(reply)
            }
            (Namespace::Bootloader, 4) => self.sub(Namespace::Flash),

            (Namespace::Power, 0 | 1) => self.reset(),

            //get flash stage
            (Namespace::Flash, 0) => {
                let stage = state().flash_stage;
                self.answer(vec![0, stage])
            }
            //start flash
            (Namespace::Flash, 1) => {
                {
                    let mut state = state();
                    state.app_run = false;
                    state.next_address = APP_START_ADDRESS;
                    state.flash_stage = 1;
                }
                self.answer(vec![0])
            }
            //get next address
            (Namespace::Flash, 2) => {
                let next = state().next_address;
                self.answer(next.to_be_bytes().to_vec())
            }
            //push code
            (Namespace::Flash, 3) => {
                let address = word(params)?;
                let code = params.read_blob_remain();
                let next = {
                    let mut state = state();
                    if state.flash_stage == 0 {
                        drop(state);
                        return self.answer(vec![107 /*ENOTCONN*/]);
                    }
                    if address != state.next_address {
                        drop(state);
                        return self.answer(vec![errno::ENXIO]);
                    }
                    let from = usize::from(address);
                    state.memory[from..from + code.len()].copy_from_slice(&code);
                    state.next_address += code.len() as u16;
                    state.next_address
                };
                let mut reply = vec![0];
                reply.extend_from_slice(&next.to_be_bytes());
                self.answer(reply)
            }
            //commit flash
            (Namespace::Flash, 4) => {
                state().flash_stage = 0;
                self.answer(vec![0])
            }

            _ => Err(ServiceError::Internal(format!("no function {index}"))),
        }
    }
}

impl UbService for DeviceNs {
    fn call(&self, index: u8, params: &mut PacketReader) -> Option<ServiceCall> {
        Some(ServiceCall::plain(self.serve(index, params)))
    }
}

pub struct SimulatedDevice {
    pub state: Arc<Mutex<DeviceState>>,
    _bridge: Arc<UartbusServiceBridge>,
    bus: UartBus,
}

impl SimulatedDevice {
    /// Attaches a device with the given address to the bus of the RPC server on the port.
    pub fn attach(port: u16, address: i32) -> Self {
        let bus = UartBus::from_tcp("127.0.0.1", port, address).unwrap();
        let state = Arc::new(Mutex::new(DeviceState {
            memory: vec![0xff; 0x10000],
            app_run: true,
            flash_stage: 0,
            next_address: 0,
            resets: 0,
            led: false,
            requests: 0,
            lose_every: 0,
            broken_cell: None,
        }));

        let service = DeviceNs {
            namespace: Namespace::Root,
            address,
            bus: bus.clone(),
            state: Arc::clone(&state),
        };
        let bridge =
            UartbusServiceBridge::attach_service_to(Arc::new(service), &bus, Some(address), &[]);

        Self {
            state,
            _bridge: bridge,
            bus,
        }
    }

    pub fn application_code(&self, length: usize) -> Vec<u8> {
        let from = usize::from(APP_START_ADDRESS);
        self.state.lock().unwrap().memory[from..from + length].to_vec()
    }
}

impl Drop for SimulatedDevice {
    fn drop(&mut self) {
        self.bus.close();
    }
}
