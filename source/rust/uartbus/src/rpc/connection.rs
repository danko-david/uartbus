use std::collections::BTreeMap;
use std::io;

/// Access to the bus as the RPC server offers it: send packets and poll the received ones.
pub trait UartbusConnection: Send + Sync {
    /// Registers this client at the server, so packets are collected for it from now on.
    fn init(&self) -> io::Result<()>;

    fn send_packet(&self, data: &[u8]) -> io::Result<()>;

    fn list_attributes(&self) -> io::Result<BTreeMap<String, String>>;
    fn get_attribute(&self, key: &str) -> io::Result<Option<String>>;
    fn set_attribute(&self, key: &str, value: &str) -> io::Result<()>;

    /// Blocks until a packet arrives.
    fn get_next_packet(&self) -> io::Result<Vec<u8>>;

    fn close(&self);
}
