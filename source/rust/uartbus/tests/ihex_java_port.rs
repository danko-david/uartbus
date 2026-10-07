mod common;

use std::fs;

use common::TempDir;
use uartbus::ihex::{concat_segments, from_segments, IntelHexFile};

#[test]
fn intel_hex_get_code_matches_java() {
    let file = IntelHexFile::from_lines(&[":020000001122CB", ":00000001FF"]).unwrap();
    file.assert_valid().unwrap();

    let code = file.get_code().unwrap();
    assert_eq!(code.len(), 1);
    assert_eq!(code[0].start_address(), 0);
    assert_eq!(code[0].data(), &[0x11, 0x22]);
}

#[test]
fn intel_hex_concat_segments_matches_java_gap_padding() {
    let file =
        IntelHexFile::from_lines(&[":020000001122CB", ":02000500334482", ":00000001FF"]).unwrap();
    let segments = concat_segments(file.get_code().unwrap(), 10, 0xff).unwrap();

    assert_eq!(segments.len(), 1);
    assert_eq!(segments[0].start_address(), 0);
    assert_eq!(segments[0].end_address(), 4);
    assert_eq!(
        segments[0].data(),
        &[0x11, 0x22, 0xff, 0xff, 0xff, 0x33, 0x44]
    );
}

#[test]
fn intel_hex_from_segments_matches_java() {
    let file = IntelHexFile::from_lines(&[":0400000001020304F4", ":00000001FF"]).unwrap();
    let segments = file.get_code().unwrap();
    let output = from_segments(&segments).unwrap();

    assert!(output.get_data_size() >= 4);
    output.assert_valid().unwrap();
}

#[test]
fn cli_ihex_replace_interrupts_rewrites_vector() {
    let dir = TempDir::new("ihex");
    let input = dir.path().join("input.hex");
    let output = dir.path().join("output.hex");

    let mut file = IntelHexFile::new();
    file.push_line(file.create_data_line(0, &[0x0c, 0x94, 0x00, 0xc0]));
    file.push_line(file.create_data_line(4, &[0x0c, 0x94, 0x04, 0x00]));
    file.push_line(file.create_eof_line());
    file.write_to_file(&input).unwrap();

    let status = std::process::Command::new(env!("CARGO_BIN_EXE_ub"))
        .args([
            "ihex",
            "replace_interrupts",
            "-i",
            input.to_str().unwrap(),
            "-o",
            output.to_str().unwrap(),
            "-p",
            "100",
            "-a",
            "8",
        ])
        .status()
        .unwrap();
    assert!(status.success());

    let rewritten = fs::read_to_string(output).unwrap();
    assert!(rewritten.contains("0C943400"));
}
