//! Port of the Java UbRpcTest: calls of a service through the (virtual) bus, every data type.

use std::sync::Arc;
use std::time::Duration;

use num_bigint::BigInt;
use uartbus::packet::PacketReader;
use uartbus::rpc::device::{errno, RpcError, UartBusDevice, UbFunction, UbRet, NS_APP};
use uartbus::rpc::service::{
    read_param, ServiceCall, ServiceError, ServiceReply, UartbusServiceBridge, UbService,
};
use uartbus::rpc::{RpcType, RpcValue, VirtualUartBus};
use uartbus::{UInt16, UInt8, VSigned, VUnsigned};

const RPC_VALS: [&str; 3] = ["VAL_0", "VAL_1", "VAL_2"];

fn enum_type() -> RpcType {
    RpcType::Enum(RPC_VALS.iter().map(|s| s.to_string()).collect())
}

/// The type the function with the index takes and gives back.
fn echo_type(index: u8) -> Option<RpcType> {
    Some(match index {
        1 => RpcType::Bool,
        2 => RpcType::S8,
        3 => RpcType::UInt8,
        4 => RpcType::UInt16,
        5 => RpcType::S16,
        6 => RpcType::Bytes,
        7 => RpcType::String,
        8 => RpcType::VSigned,
        9 => RpcType::VUnsigned,
        10 => enum_type(),
        11 => RpcType::Struct(vec![RpcType::S16]),
        12 => RpcType::Struct(vec![RpcType::S16, RpcType::String]),
        13 => RpcType::Struct(vec![RpcType::S16, RpcType::VUnsigned, RpcType::Bytes]),
        14 => RpcType::Array(Box::new(RpcType::String)),
        15 => RpcType::Array(Box::new(RpcType::VUnsigned)),
        _ => return None,
    })
}

/// Every function gives back what it gets.
struct RpcTestService;

impl UbService for RpcTestService {
    fn call(&self, index: u8, params: &mut PacketReader) -> Option<ServiceCall> {
        if let Some(ty) = echo_type(index) {
            return Some(ServiceCall::plain(
                read_param(params, &ty).map(ServiceReply::Value),
            ));
        }

        match index {
            //call()
            16 => Some(ServiceCall::plain(Ok(ServiceReply::Void))),
            //callEx(): always fails
            50 => Some(ServiceCall::posix(Err(ServiceError::Posix(errno::EAGAIN)))),
            //callShort(v): fails with the given error number
            51 => Some(ServiceCall::posix(
                read_param(params, &RpcType::S16).and_then(|value| match value {
                    RpcValue::S16(0) => Ok(ServiceReply::Void),
                    RpcValue::S16(v) if (1..=255).contains(&v) => Err(ServiceError::Posix(v as u8)),
                    other => Err(ServiceError::Internal(format!("no such errno: {other:?}"))),
                }),
            )),
            _ => None,
        }
    }
}

struct Env {
    _bus: VirtualUartBus,
    _bridge: Arc<UartbusServiceBridge>,
    dev: UartBusDevice,
}

/// A virtual bus where the test service answers on the address 63 in the application namespace (32).
fn setup() -> Env {
    let cb = VirtualUartBus::create_env().unwrap();
    let bridge = UartbusServiceBridge::attach_service_to(
        Arc::new(RpcTestService),
        cb.get_bus(),
        Some(63),
        &[NS_APP],
    );
    let api_bus = cb.new_bus(63).unwrap();
    let mut dev = api_bus.device(63);
    dev.timeout = Duration::from_millis(100);
    Env {
        _bus: cb,
        _bridge: bridge,
        dev,
    }
}

fn echo(env: &Env, index: u8, value: RpcValue) -> RpcValue {
    let function = UbFunction::new(index, UbRet::Value(echo_type(index).unwrap()));
    env.dev
        .call(&[NS_APP], &function, &[value])
        .unwrap()
        .unwrap()
}

fn assert_echo(env: &Env, index: u8, value: RpcValue) {
    assert_eq!(echo(env, index, value.clone()), value);
}

#[test]
fn pass_call() {
    let env = setup();
    let result = env
        .dev
        .call(&[NS_APP], &UbFunction::new(16, UbRet::Void), &[]);
    assert_eq!(result, Ok(None));
}

#[test]
fn exceptions() {
    let env = setup();
    let result = env
        .dev
        .call(&[NS_APP], &UbFunction::new(50, UbRet::Void).posix(), &[]);
    assert_eq!(result, Err(RpcError::Posix(errno::EAGAIN)));
}

#[test]
fn more_exceptions() {
    let env = setup();
    let call_short = UbFunction::new(51, UbRet::Void).posix();

    assert_eq!(
        env.dev.call(&[NS_APP], &call_short, &[RpcValue::S16(0)]),
        Ok(None)
    );
    assert_eq!(
        env.dev.call(&[NS_APP], &call_short, &[RpcValue::S16(13)]),
        Err(RpcError::Posix(errno::EACCES))
    );
}

#[test]
fn internal_error_is_not_answered() {
    let env = setup();
    let call_short = UbFunction::new(51, UbRet::Void).posix();

    //65500 as short
    let result = env.dev.call(&[NS_APP], &call_short, &[RpcValue::S16(-36)]);
    assert!(
        matches!(result, Err(RpcError::Transaction(_))),
        "{result:?}"
    );
}

#[test]
fn unknown_function_is_not_answered() {
    let env = setup();
    let result = env
        .dev
        .call(&[NS_APP], &UbFunction::new(99, UbRet::Void), &[]);
    assert!(
        matches!(result, Err(RpcError::Transaction(_))),
        "{result:?}"
    );
}

#[test]
fn retransmittable_call_is_given_up_after_the_retry_count() {
    let mut env = setup();
    env.dev.timeout = Duration::from_millis(10);
    env.dev.retry_count = 3;
    let result = env.dev.call(
        &[NS_APP],
        &UbFunction::new(99, UbRet::Void).retransmittable(),
        &[],
    );
    match result {
        Err(RpcError::Transaction(msg)) => {
            assert!(
                msg.starts_with("Device not responded after 3 attempt."),
                "{msg}"
            )
        }
        other => panic!("{other:?}"),
    }
}

#[test]
fn type_bool() {
    let env = setup();
    assert_echo(&env, 1, RpcValue::Bool(true));
    assert_echo(&env, 1, RpcValue::Bool(false));
}

#[test]
fn type_s8() {
    let env = setup();
    for v in [0, 127, -128] {
        assert_echo(&env, 2, RpcValue::S8(v));
    }
}

#[test]
fn type_u8() {
    let env = setup();
    assert_echo(&env, 3, RpcValue::UInt8(UInt8::new(0)));
    assert_echo(&env, 3, RpcValue::UInt8(UInt8::new(255)));
}

#[test]
fn type_s16() {
    let env = setup();
    for v in [0, i16::MIN, i16::MAX] {
        assert_echo(&env, 5, RpcValue::S16(v));
    }
}

#[test]
fn type_u16() {
    let env = setup();
    assert_echo(&env, 4, RpcValue::UInt16(UInt16::new(0)));
    assert_echo(&env, 4, RpcValue::UInt16(UInt16::new(65535)));
}

#[test]
fn type_byte() {
    let env = setup();
    assert_echo(&env, 6, RpcValue::Bytes(vec![]));
    assert_echo(&env, 6, RpcValue::Bytes(vec![0, 1, 0x80, 127]));
}

#[test]
fn type_string() {
    let env = setup();
    for s in ["", "a", "abc def", "áé", "Hello World"] {
        assert_echo(&env, 7, RpcValue::String(s.to_string()));
    }
}

#[test]
fn type_vs() {
    let env = setup();
    for v in [
        0i64,
        1,
        -1,
        i32::MIN as i64,
        i32::MAX as i64,
        i64::MIN,
        i64::MAX,
    ] {
        assert_echo(&env, 8, RpcValue::VSigned(VSigned::new(v)));
    }
}

#[test]
fn type_vu() {
    let env = setup();
    for v in [0i64, 1, i32::MAX as i64, i64::MAX, 8656546456345643] {
        assert_echo(&env, 9, RpcValue::VUnsigned(VUnsigned::new(v)));
    }
}

#[test]
fn type_enum() {
    let env = setup();
    for v in 0..RPC_VALS.len() {
        assert_echo(&env, 10, RpcValue::Enum(v));
    }
}

#[test]
fn type_struct1() {
    let env = setup();
    for a in [0, 15, -15] {
        assert_echo(&env, 11, RpcValue::Struct(vec![RpcValue::S16(a)]));
    }
}

#[test]
fn type_struct2() {
    let env = setup();
    for (a, b) in [(0, "Hello"), (15, "World"), (-15, "World")] {
        assert_echo(
            &env,
            12,
            RpcValue::Struct(vec![RpcValue::S16(a), RpcValue::String(b.to_string())]),
        );
    }
}

#[test]
fn type_struct3() {
    let env = setup();
    for (a, b, c) in [
        (0, "1024", "Hello"),
        (15, "23324", "World"),
        (
            -15,
            "63656345634675412366485877684356132412342341",
            "Hello World",
        ),
    ] {
        assert_echo(
            &env,
            13,
            RpcValue::Struct(vec![
                RpcValue::S16(a),
                RpcValue::VUnsigned(VUnsigned::new(b.parse::<BigInt>().unwrap())),
                RpcValue::Bytes(c.as_bytes().to_vec()),
            ]),
        );
    }
}

#[test]
fn type_strings() {
    let env = setup();
    let strings = |list: &[&str]| {
        RpcValue::Array(
            list.iter()
                .map(|s| RpcValue::String(s.to_string()))
                .collect(),
        )
    };
    assert_echo(&env, 14, strings(&[]));
    assert_echo(&env, 14, strings(&["a", "", "Hello World"]));
}

#[test]
fn type_vunsigneds() {
    let env = setup();
    for numbers in [
        vec!["0", "1", "5", "10"],
        vec!["10", "1000", "8656546456345643", "0", "1000"],
    ] {
        assert_echo(
            &env,
            15,
            RpcValue::Array(
                numbers
                    .iter()
                    .map(|n| RpcValue::VUnsigned(VUnsigned::new(n.parse::<BigInt>().unwrap())))
                    .collect(),
            ),
        );
    }
}

#[test]
fn request_for_an_other_address_is_not_served() {
    let env = setup();
    let other = env.dev.get_bus().device(12);
    let mut other = other;
    other.timeout = Duration::from_millis(30);
    let result = other.call(&[NS_APP], &UbFunction::new(16, UbRet::Void), &[]);
    assert!(
        matches!(result, Err(RpcError::Transaction(_))),
        "{result:?}"
    );
}
