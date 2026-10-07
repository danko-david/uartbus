use num_bigint::BigInt;
use uartbus::{
    frame_bytes, is_packet_crc8_valid, pack_i128, pack_value, unpack_value, FramedPacketCutter,
    PacketAssembler, PacketReader, ParsedUartBusPacket,
};

#[test]
fn pack_unsigned_one_byte_matches_java() {
    assert_eq!(pack_i128(false, 0).unwrap(), vec![0x00]);
    assert_eq!(pack_i128(false, 63).unwrap(), vec![0x3f]);
    assert_eq!(pack_i128(false, 64).unwrap(), vec![0x40]);
    assert_eq!(pack_i128(false, 127).unwrap(), vec![0x7f]);
}

#[test]
fn pack_unsigned_multi_byte_matches_java() {
    assert_eq!(pack_i128(false, 128).unwrap(), vec![0x81, 0x00]);
    assert_eq!(pack_i128(false, 255).unwrap(), vec![0x81, 0x7f]);
    assert_eq!(pack_i128(false, 256).unwrap(), vec![0x82, 0x00]);
    assert_eq!(pack_i128(false, 16_383).unwrap(), vec![0xff, 0x7f]);
    assert_eq!(pack_i128(false, 16_384).unwrap(), vec![0x81, 0x80, 0x00]);
}

#[test]
fn pack_signed_matches_java() {
    assert_eq!(pack_i128(true, 0).unwrap(), vec![0x00]);
    assert_eq!(pack_i128(true, -1).unwrap(), vec![0x40]);
    assert_eq!(pack_i128(true, -64).unwrap(), vec![0x7f]);
    assert_eq!(pack_i128(true, 63).unwrap(), vec![0x3f]);
    assert_eq!(pack_i128(true, 128).unwrap(), vec![0x81, 0x00]);
    assert_eq!(pack_i128(true, -129).unwrap(), vec![0xc1, 0x00]);
    assert_eq!(pack_i128(true, 8191).unwrap(), vec![0xbf, 0x7f]);
    assert_eq!(pack_i128(true, -8192).unwrap(), vec![0xff, 0x7f]);
}

#[test]
fn unpack_matches_java_addresses_after_offset() {
    let packet = [0x40, 0x40];
    let (to, used) = unpack_value(true, &packet, 0).unwrap();
    assert_eq!(to, BigInt::from(-1));
    let (from, used_from) = unpack_value(true, &packet, used).unwrap();
    assert_eq!(from, BigInt::from(-1));
    assert_eq!(used_from, 1);
}

#[test]
fn pack_unpack_round_trip_large_values() {
    for value in [
        BigInt::from(1024_i64),
        BigInt::parse_bytes(b"95349643593278962348975692345932456", 10).unwrap(),
        BigInt::parse_bytes(b"-95349643593278962348975692345932456", 10).unwrap(),
    ] {
        let encoded = pack_value(true, value.clone()).unwrap();
        let (decoded, _) = unpack_value(true, &encoded, 0).unwrap();
        assert_eq!(decoded, value);
    }
}

#[test]
fn long_min_max_encoding_matches_java() {
    assert_eq!(
        pack_i128(true, i64::MIN as i128).unwrap(),
        vec![0xc0, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x7f]
    );
    assert_eq!(
        pack_i128(true, i64::MAX as i128).unwrap(),
        vec![0x80, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x7f]
    );
}

#[test]
fn packet_assembler_writes_addresses_payload_and_crc() {
    let mut assembler = PacketAssembler::new();
    assembler.write_addressing(1, 2).unwrap();
    assembler.write_blob(&[3, 4]);
    assembler.append_crc8();
    let packet = assembler.done();

    assert!(is_packet_crc8_valid(&packet));
    let parsed = ParsedUartBusPacket::from_raw_packet_with_crc(&packet).unwrap();
    assert_eq!(parsed.from, 1);
    assert_eq!(parsed.to, 2);
    assert_eq!(parsed.payload, vec![3, 4]);
}

#[test]
fn packet_reader_reads_fixed_width_values() {
    let mut reader = PacketReader::from_slice(&[0x12, 0x34, 0x12, 0x34, 0x56, 0x78]);
    assert_eq!(reader.read_u16_be().unwrap(), 0x1234);
    assert_eq!(reader.read_u32_be().unwrap(), 0x12345678);
}

#[test]
fn escaped_stream_frames_java_test_cases() {
    assert_eq!(frame_bytes(&[], 0xff), vec![0xff, 0x00]);
    assert_eq!(
        frame_bytes(&[0xff, 0xff], 0xff),
        vec![0xff, 0xff, 0xff, 0xff, 0xff, 0x00]
    );
    assert_eq!(
        frame_bytes(&[0xff, 0x00, 0x00, 0xff], 0xff),
        vec![0xff, 0xff, 0x00, 0x00, 0xff, 0xff, 0xff, 0x00]
    );
    assert_eq!(
        frame_bytes(&[0x0c, 0x05, 0xc0], 0xff),
        vec![0x0c, 0x05, 0xc0, 0xff, 0x00]
    );

    let mut received = Vec::new();
    FramedPacketCutter::new(0xff).feed_bytes(
        &[
            0x14, 0x1e, 0x01, 0x00, 0x01, 0xff, 0x00, 0x14, 0x1e, 0x1e, 0x1e, 0x1e, 0xff, 0x00,
        ],
        |packet| received.extend_from_slice(&packet),
    );
    assert_eq!(
        received,
        vec![0x14, 0x1e, 0x01, 0x00, 0x01, 0x14, 0x1e, 0x1e, 0x1e, 0x1e]
    );
}

#[test]
fn value_that_ends_before_its_last_byte_is_incomplete() {
    assert!(unpack_value(false, &[0x81], 0).is_err());
    assert!(unpack_value(true, &[5, 0x81, 0x80], 1).is_err());
    assert!(unpack_value(false, &[], 0).is_err());
    assert!(unpack_value(false, &[5], 1).is_err());
}

#[test]
fn packet_text_shows_the_expected_crc_when_it_is_wrong() {
    let good = ParsedUartBusPacket::from_raw_packet_with_crc(&[5, 63, 1, 0, 0xc4]).unwrap();
    assert_eq!(
        good.to_short_user_text(),
        "From: 63, To: 5, payload: 1:0, crc8: 196"
    );

    let bad = ParsedUartBusPacket::from_raw_packet_with_crc(&[5, 63, 1, 0, 7]).unwrap();
    assert_eq!(
        bad.to_short_user_text(),
        "From: 63, To: 5, payload: 1:0, crc8: 7 != 196"
    );
}
