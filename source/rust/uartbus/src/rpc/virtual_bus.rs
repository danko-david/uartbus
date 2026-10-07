//! A bus that exists only in this process, for tests and experiments.

use std::io;

use crate::rpc::bus::UartBus;
use crate::rpc::endpoint::UartbusRpcEndpoint;
use crate::rpc::streamer::SharedConnection;

pub struct VirtualUartBus {
    endpoint: UartbusRpcEndpoint,
    bus: UartBus,
}

impl VirtualUartBus {
    pub fn create_env() -> io::Result<Self> {
        Self::create_env_from(63)
    }

    pub fn create_env_from(bus_from: i32) -> io::Result<Self> {
        let endpoint = UartbusRpcEndpoint::new_dummy();
        let bus = Self::bus_of(&endpoint, bus_from)?;
        Ok(Self { endpoint, bus })
    }

    fn bus_of(endpoint: &UartbusRpcEndpoint, bus_from: i32) -> io::Result<UartBus> {
        let endpoint = endpoint.clone();
        UartBus::from_connection(Box::new(move || endpoint.create_new_connection()), bus_from)
    }

    /// The bus created with the environment.
    pub fn get_bus(&self) -> &UartBus {
        &self.bus
    }

    /// One more participant of the bus with its own address.
    pub fn new_bus(&self, bus_from: i32) -> io::Result<UartBus> {
        Self::bus_of(&self.endpoint, bus_from)
    }

    pub fn create_new_connection(&self) -> io::Result<SharedConnection> {
        self.endpoint.create_new_connection()
    }

    pub fn get_endpoint(&self) -> &UartbusRpcEndpoint {
        &self.endpoint
    }

    pub fn close(&self) {
        self.bus.close();
        self.endpoint.close();
    }
}

impl Drop for VirtualUartBus {
    fn drop(&mut self) {
        self.close();
    }
}
