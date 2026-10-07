use num_bigint::BigInt;
use uartbus::{
    format_colon_data, format_colon_data_with_validation, get_valid_packet, is_packet_crc8_valid,
    pack_value, parse_colon_data, unpack_value,
};

#[test]
fn first_unsigned_of_series_matches_java() {
    for n in [
        2u32, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 31, 32, 58, 59, 63, 64, 1023, 1024, 1025, 4095, 4096,
    ] {
        let value = BigInt::from(2u8).pow(7 * (n - 1));
        let encoded = pack_value(false, value).unwrap();
        assert_eq!(encoded.len(), n as usize);
        assert_eq!(encoded[0], 0x81);
        assert_eq!(encoded[n as usize - 1], 0x00);
        for byte in &encoded[1..n as usize - 1] {
            assert_eq!(*byte, 0x80);
        }
    }
}

#[test]
fn last_unsigned_of_series_matches_java() {
    for n in [
        2u32, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 63, 64, 1023, 1024, 1025, 4095, 4096,
    ] {
        let value = BigInt::from(2u8).pow(7 * n) - BigInt::from(1);
        let encoded = pack_value(false, value).unwrap();
        assert_eq!(encoded.len(), n as usize);
        assert!(encoded[..n as usize - 1].iter().all(|byte| *byte == 0xff));
        assert_eq!(encoded[n as usize - 1], !0x80);
    }
}

#[test]
fn colon_formatting_matches_java() {
    assert_eq!(parse_colon_data("1:2:255").unwrap(), vec![1, 2, 255]);
    assert_eq!(format_colon_data(&[1, 2, 255]), "1:2:255");

    let invalid = [1, 2, 3];
    assert!(format_colon_data_with_validation(&invalid).starts_with('!'));

    let mut valid = vec![1, 2];
    valid.push(uartbus::tools::crc8(&valid));
    assert!(is_packet_crc8_valid(&valid));
    assert_eq!(get_valid_packet(&valid).unwrap(), vec![1, 2]);
}

#[test]
fn unsigned_values_round_trip_matches_java() {
    for value in [
        BigInt::from(1024_i64),
        BigInt::parse_bytes(b"95349643593278962348975692345932456", 10).unwrap(),
        BigInt::parse_bytes(b"875423123432230121024332421223", 10).unwrap(),
    ] {
        let encoded = pack_value(true, value.clone()).unwrap();
        let (decoded, _) = unpack_value(true, &encoded, 0).unwrap();
        assert_eq!(decoded, value);
    }
}

#[test]
fn signed_values_round_trip_matches_java() {
    for value in [
        BigInt::parse_bytes(b"95349643593278962348975692345932456", 10).unwrap(),
        BigInt::parse_bytes(b"875423123432230121024332421223", 10).unwrap(),
        BigInt::parse_bytes(b"-95349643593278962348975692345932456", 10).unwrap(),
        BigInt::parse_bytes(b"-875423123432230121024332421223", 10).unwrap(),
        BigInt::from(1024_i64),
    ] {
        let encoded = pack_value(true, value.clone()).unwrap();
        let (decoded, _) = unpack_value(true, &encoded, 0).unwrap();
        assert_eq!(decoded, value);
    }
}

#[test]
fn colon_data_takes_the_low_byte_of_every_number() {
    assert_eq!(
        parse_colon_data("-1:256:-128:300").unwrap(),
        vec![255, 0, 128, 44]
    );
    assert!(parse_colon_data("1::2").is_err());
    assert!(parse_colon_data("1: 2").is_err());
    assert!(parse_colon_data("").is_err());
}
