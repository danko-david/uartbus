use std::error::Error;
use std::fmt;
use std::fs::File;
use std::io::{self, BufRead, BufReader, Write};
use std::path::Path;

#[derive(Debug)]
pub enum IntelHexError {
    InvalidLine(String),
    ChecksumMismatch {
        line: String,
        actual: u8,
        expected: u8,
    },
    IntersectingData(String),
    Io(io::Error),
    Utf8(std::string::FromUtf8Error),
}

impl fmt::Display for IntelHexError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            IntelHexError::InvalidLine(line) => write!(f, "invalid Intel HEX line: {line}"),
            IntelHexError::ChecksumMismatch {
                line,
                actual,
                expected,
            } => write!(
                f,
                "checksum mismatch for {line}: actual {actual:#04x}, expected {expected:#04x}"
            ),
            IntelHexError::IntersectingData(line) => write!(f, "intersecting data: {line}"),
            IntelHexError::Io(err) => write!(f, "I/O error: {err}"),
            IntelHexError::Utf8(err) => write!(f, "UTF-8 error: {err}"),
        }
    }
}

impl Error for IntelHexError {}

impl From<io::Error> for IntelHexError {
    fn from(value: io::Error) -> Self {
        IntelHexError::Io(value)
    }
}

impl From<std::string::FromUtf8Error> for IntelHexError {
    fn from(value: std::string::FromUtf8Error) -> Self {
        IntelHexError::Utf8(value)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RecordType {
    Data,
    EndOfFile,
    ExtendedSegmentAddress,
    ExtendedLinearAddress,
    StartLinearAddress,
    Unknown(u8),
}

impl RecordType {
    pub fn from_u8(value: u8) -> Self {
        match value {
            0 => RecordType::Data,
            1 => RecordType::EndOfFile,
            2 => RecordType::ExtendedSegmentAddress,
            3 => RecordType::ExtendedLinearAddress,
            4 => RecordType::StartLinearAddress,
            other => RecordType::Unknown(other),
        }
    }

    pub fn as_u8(self) -> u8 {
        match self {
            RecordType::Data => 0,
            RecordType::EndOfFile => 1,
            RecordType::ExtendedSegmentAddress => 2,
            RecordType::ExtendedLinearAddress => 3,
            RecordType::StartLinearAddress => 4,
            RecordType::Unknown(value) => value,
        }
    }
}

#[derive(Debug, Clone)]
pub struct IntelHexLine {
    raw: Vec<u8>,
    length: usize,
    address: u32,
    record_type: RecordType,
    data: Vec<u8>,
    checksum: u8,
}

impl IntelHexLine {
    pub fn new(line: &str) -> Result<Self, IntelHexError> {
        let trimmed = line.trim();

        if !trimmed.starts_with(':') {
            return Err(IntelHexError::InvalidLine(trimmed.to_string()));
        }

        let hex = &trimmed[1..];
        if hex.len() < 10 || hex.chars().any(|ch| ch.to_digit(16).is_none()) {
            return Err(IntelHexError::InvalidLine(trimmed.to_string()));
        }

        let raw = from_hex(hex)?;
        let length = raw[0] as usize;
        let address = ((raw[1] as u32) << 8) | raw[2] as u32;
        let record_type = RecordType::from_u8(raw[3]);
        let data = raw[4..raw.len() - 1].to_vec();
        let checksum = *raw.last().unwrap();

        Ok(Self {
            raw,
            length,
            address,
            record_type,
            data,
            checksum,
        })
    }

    pub fn calc_checksum(&self) -> u8 {
        checksum(&self.raw, 0, self.raw.len() - 1)
    }

    pub fn to_line_string(&self) -> String {
        format!(":{}", to_hex(&self.raw).to_uppercase())
    }

    pub fn raw(&self) -> &[u8] {
        &self.raw
    }

    pub fn length(&self) -> usize {
        self.length
    }

    pub fn address(&self) -> u32 {
        self.address
    }

    pub fn record_type(&self) -> RecordType {
        self.record_type
    }

    pub fn data(&self) -> &[u8] {
        &self.data
    }

    pub fn checksum(&self) -> u8 {
        self.checksum
    }

    fn to_raw_by_data(&self) -> Vec<u8> {
        let mut raw = Vec::with_capacity(self.data.len() + 5);
        raw.push(self.data.len() as u8);
        raw.push(((self.address >> 8) & 0xff) as u8);
        raw.push((self.address & 0xff) as u8);
        raw.push(self.record_type.as_u8());
        raw.extend_from_slice(&self.data);
        raw.push(self.checksum);
        raw
    }
}

#[derive(Debug, Clone, Default)]
pub struct IntelHexFile {
    lines: Vec<IntelHexLine>,
}

impl IntelHexFile {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn from_lines(lines: &[&str]) -> Result<Self, IntelHexError> {
        let mut file = Self::new();

        for line in lines {
            file.lines.push(IntelHexLine::new(line)?);
        }

        Ok(file)
    }

    pub fn load_file(path: impl AsRef<Path>) -> Result<Self, IntelHexError> {
        let file = File::open(path)?;
        let reader = BufReader::new(file);
        let mut lines = Vec::new();

        for line in reader.lines() {
            let line = line?;
            if !line.trim().is_empty() {
                lines.push(line);
            }
        }

        let file = Self::from_lines(&lines.iter().map(|line| line.as_str()).collect::<Vec<_>>())?;
        file.assert_valid()?;
        Ok(file)
    }

    pub fn assert_valid(&self) -> Result<(), IntelHexError> {
        for line in &self.lines {
            let actual = line.checksum;
            let expected = line.calc_checksum();

            if actual != expected {
                return Err(IntelHexError::ChecksumMismatch {
                    line: line.to_line_string(),
                    actual,
                    expected,
                });
            }
        }

        Ok(())
    }

    pub fn create_eof_line(&self) -> IntelHexLine {
        IntelHexLine::new(":00000001FF").unwrap()
    }

    pub fn create_data_line(&self, address: u32, data: &[u8]) -> IntelHexLine {
        let mut line = IntelHexLine {
            raw: Vec::new(),
            length: data.len(),
            address,
            record_type: RecordType::Data,
            data: data.to_vec(),
            checksum: 0,
        };

        line.raw = line.to_raw_by_data();
        line.checksum = checksum(&line.raw, 0, line.raw.len() - 1);
        line.raw = line.to_raw_by_data();
        line
    }

    pub fn lines(&self) -> &[IntelHexLine] {
        &self.lines
    }

    pub fn push_line(&mut self, line: IntelHexLine) {
        self.lines.push(line);
    }

    pub fn get_data_size(&self) -> usize {
        self.lines.iter().map(|line| line.data().len()).sum()
    }

    pub fn get_code(&self) -> Result<Vec<CodeSegment>, IntelHexError> {
        let mut segments: Vec<CodeSegment> = Vec::new();

        'lines: for line in &self.lines {
            if line.record_type() == RecordType::EndOfFile {
                break;
            }

            if line.record_type() != RecordType::Data {
                continue;
            }

            let start = line.address() as u64;

            for segment in &segments {
                if segment.is_middle_of(start) {
                    return Err(IntelHexError::IntersectingData(line.to_line_string()));
                }
            }

            if let Some(segment) = segments
                .iter_mut()
                .find(|segment| start == segment.end_address)
            {
                segment.append_code(line.data())?;
                continue 'lines;
            }

            let mut segment = CodeSegment::new(start);
            segment.append_code(line.data())?;
            segments.push(segment);
        }

        for segment in &mut segments {
            segment.end_code_write();
        }

        Ok(segments)
    }

    pub fn write_to_file(&self, path: impl AsRef<Path>) -> Result<(), IntelHexError> {
        let mut file = File::create(path)?;

        for line in &self.lines {
            writeln!(file, "{}", line.to_line_string())?;
        }

        file.flush()?;
        Ok(())
    }
}

#[derive(Debug, Clone)]
pub struct CodeSegment {
    start_address: u64,
    end_address: u64,
    data: Vec<u8>,
    building: bool,
}

impl CodeSegment {
    pub fn new(start_address: u64) -> Self {
        Self {
            start_address,
            end_address: start_address,
            data: Vec::new(),
            building: true,
        }
    }

    pub fn append_code(&mut self, data: &[u8]) -> Result<(), IntelHexError> {
        self.data.extend_from_slice(data);
        self.end_address += data.len() as u64;
        Ok(())
    }

    pub fn is_middle_of(&self, address: u64) -> bool {
        self.start_address < address && address < self.end_address
    }

    pub fn end_code_write(&mut self) {
        self.building = false;
    }

    pub fn get_code_piece(&self, offset: usize, length: usize) -> Vec<u8> {
        let end = self.data.len().min(offset + length);
        self.data[offset..end].to_vec()
    }

    pub fn start_address(&self) -> u64 {
        self.start_address
    }

    pub fn end_address(&self) -> u64 {
        self.end_address
    }

    pub fn data(&self) -> &[u8] {
        &self.data
    }

    pub fn data_mut(&mut self) -> &mut [u8] {
        &mut self.data
    }

    pub fn is_building(&self) -> bool {
        self.building
    }
}

pub fn concat_segments(
    mut codes: Vec<CodeSegment>,
    max_gap: u64,
    padding_byte: u8,
) -> Result<Vec<CodeSegment>, IntelHexError> {
    if codes.len() < 2 {
        return Ok(codes);
    }

    codes.sort_by_key(|segment| segment.start_address);
    let mut ret: Vec<CodeSegment> = Vec::new();

    for code in codes {
        for segment in &ret {
            if segment.is_middle_of(code.start_address) {
                return Err(IntelHexError::IntersectingData(format!(
                    "{:#x}",
                    segment.start_address
                )));
            }
        }

        let target = ret
            .iter_mut()
            .find(|segment| code.start_address.saturating_add(max_gap) > segment.end_address);

        let segment = if let Some(segment) = target {
            segment
        } else {
            let segment = CodeSegment::new(code.start_address);
            ret.push(segment);
            ret.last_mut().unwrap()
        };

        let diff = code.start_address.saturating_sub(segment.end_address);
        for _ in 0..diff {
            segment.data.push(padding_byte);
        }

        segment.append_code(code.data())?;
    }

    for segment in &mut ret {
        segment.end_code_write();
    }

    Ok(ret)
}

pub fn from_segments(codes: &[CodeSegment]) -> Result<IntelHexFile, IntelHexError> {
    let mut file = IntelHexFile::new();

    for code in codes {
        let mut addr = code.start_address();
        let mut offset = 0usize;

        while offset < code.data().len() {
            let to = (offset + 16).min(code.data().len());
            file.push_line(file.create_data_line(addr as u32, &code.data()[offset..to]));
            addr += (to - offset) as u64;
            offset = to;
        }
    }

    file.push_line(file.create_eof_line());
    file.assert_valid()?;
    Ok(file)
}

pub fn checksum(data: &[u8], from: usize, to: usize) -> u8 {
    let sum: u16 = data[from..to].iter().map(|byte| *byte as u16).sum();
    ((!sum & 0xff) + 1) as u8
}

fn from_hex(hex: &str) -> Result<Vec<u8>, IntelHexError> {
    if !hex.len().is_multiple_of(2) {
        return Err(IntelHexError::InvalidLine(hex.to_string()));
    }

    let mut bytes = Vec::with_capacity(hex.len() / 2);
    let chars = hex.chars().collect::<Vec<_>>();

    for pair in chars.chunks(2) {
        let high = pair[0]
            .to_digit(16)
            .ok_or_else(|| IntelHexError::InvalidLine(hex.to_string()))? as u8;
        let low = pair[1]
            .to_digit(16)
            .ok_or_else(|| IntelHexError::InvalidLine(hex.to_string()))? as u8;
        bytes.push((high << 4) | low);
    }

    Ok(bytes)
}

fn to_hex(data: &[u8]) -> String {
    let mut out = String::with_capacity(data.len() * 2);

    for byte in data {
        out.push_str(&format!("{byte:02X}"));
    }

    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn checksum_eof_line() {
        let line = IntelHexLine::new(":00000001FF").unwrap();
        assert_eq!(line.calc_checksum(), 0xff);
    }

    #[test]
    fn create_data_line_round_trip() {
        let file = IntelHexFile::new();
        let line = file.create_data_line(0x10, &[1, 2, 3]);
        assert_eq!(line.to_line_string(), ":03001000010203E7");
        assert_eq!(line.data(), &[1, 2, 3]);
    }
}
