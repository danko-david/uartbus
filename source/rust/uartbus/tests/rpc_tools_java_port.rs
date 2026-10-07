use num_bigint::BigInt;
use uartbus::{
    append_elements, read_type, to_packet, PacketAssembler, PacketReader, RpcType, RpcValue,
    UInt16, UInt8, VSigned, VUnsigned,
};

fn payload(values: &[RpcValue]) -> Vec<u8> {
    let mut assembler = PacketAssembler::new();
    append_elements(&mut assembler, values).unwrap();
    assembler.done()
}

fn read_payload(values: &[RpcValue], types: &[RpcType]) -> Vec<RpcValue> {
    let data = payload(values);
    let mut reader = PacketReader::new(data);
    types
        .iter()
        .map(|ty| read_type(&mut reader, ty).unwrap())
        .collect()
}

#[test]
fn rpc_scalar_types_match_java_ubrpc_test() {
    let values = vec![
        RpcValue::Bool(true),
        RpcValue::S8(-128),
        RpcValue::UInt8(UInt8::new(255)),
        RpcValue::UInt16(UInt16::new(65535)),
        RpcValue::S16(i16::MIN),
        RpcValue::String("áé Hello World".to_string()),
        RpcValue::VSigned(VSigned::new(BigInt::from(i64::MIN))),
        RpcValue::VUnsigned(VUnsigned::new(
            BigInt::parse_bytes(b"8656546456345643", 10).unwrap(),
        )),
        RpcValue::Enum(2),
        RpcValue::Bytes(vec![0, 1, 0x80, 0x7f]),
    ];
    let types = vec![
        RpcType::Bool,
        RpcType::S8,
        RpcType::UInt8,
        RpcType::UInt16,
        RpcType::S16,
        RpcType::String,
        RpcType::VSigned,
        RpcType::VUnsigned,
        RpcType::Enum(vec!["VAL_0".into(), "VAL_1".into(), "VAL_2".into()]),
        RpcType::Bytes,
    ];

    assert_eq!(read_payload(&values, &types), values);
}

#[test]
fn rpc_structs_match_java_ubrpc_test() {
    let struct1 = vec![RpcValue::Struct(vec![RpcValue::S16(-15)])];
    assert_eq!(
        read_payload(&struct1, &[RpcType::Struct(vec![RpcType::S16])]),
        struct1
    );

    let struct2 = vec![RpcValue::Struct(vec![
        RpcValue::S16(15),
        RpcValue::String("World".to_string()),
    ])];
    assert_eq!(
        read_payload(
            &struct2,
            &[RpcType::Struct(vec![RpcType::S16, RpcType::String])]
        ),
        struct2
    );

    let struct3 = vec![RpcValue::Struct(vec![
        RpcValue::S16(-15),
        RpcValue::VUnsigned(VUnsigned::new(
            BigInt::parse_bytes(b"63656345634675412366485877684356132412342341", 10).unwrap(),
        )),
        RpcValue::Bytes(b"Hello World".to_vec()),
    ])];
    assert_eq!(
        read_payload(
            &struct3,
            &[RpcType::Struct(vec![
                RpcType::S16,
                RpcType::VUnsigned,
                RpcType::Bytes
            ])]
        ),
        struct3
    );
}

#[test]
fn rpc_arrays_match_java_ubrpc_test() {
    let values = vec![RpcValue::Array(vec![
        RpcValue::VUnsigned(VUnsigned::new(10)),
        RpcValue::VUnsigned(VUnsigned::new(1000)),
        RpcValue::VUnsigned(VUnsigned::new(
            BigInt::parse_bytes(b"8656546456345643", 10).unwrap(),
        )),
        RpcValue::VUnsigned(VUnsigned::new(0)),
        RpcValue::VUnsigned(VUnsigned::new(1000)),
    ])];
    let types = vec![RpcType::Array(Box::new(RpcType::VUnsigned))];

    assert_eq!(read_payload(&values, &types), values);
}

#[test]
fn to_packet_matches_ubrpc_to_packet_layout() {
    let packet = to_packet(
        10,
        63,
        &[RpcValue::String("Hello".to_string()), RpcValue::U8(7)],
    )
    .unwrap();
    let mut reader = PacketReader::new(packet.clone());

    assert_eq!(reader.read_vsint().unwrap(), 10.into());
    assert_eq!(reader.read_vsint().unwrap(), 63.into());
    assert_eq!(reader.read_string().unwrap(), "Hello");
    assert_eq!(reader.read_ubyte().unwrap(), 7);
    assert!(uartbus::is_packet_crc8_valid(&packet));
}
