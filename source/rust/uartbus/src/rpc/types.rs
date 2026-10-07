use num_bigint::BigInt;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct VSigned {
    pub value: BigInt,
}

impl VSigned {
    pub fn new(value: impl Into<BigInt>) -> Self {
        Self {
            value: value.into(),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct VUnsigned {
    pub value: BigInt,
}

impl VUnsigned {
    pub fn new(value: impl Into<BigInt>) -> Self {
        Self {
            value: value.into(),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct UInt8 {
    pub value: u8,
}

impl UInt8 {
    pub fn new(value: u8) -> Self {
        Self { value }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct UInt16 {
    pub value: u16,
}

impl UInt16 {
    pub fn new(value: u16) -> Self {
        Self { value }
    }
}
