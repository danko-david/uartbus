use std::fs::{self, File, OpenOptions};
use std::io::{self, Write};
use std::path::Path;

use crate::apps::{loopback_initializer, open_cli_endpoint, AppResult};
use crate::cli::{self, CliEntry, LOOPBACK, RPC_HOST, RPC_PORT};
use crate::framing::frame_bytes;
use crate::log::{log, LogLevel};
use crate::time::{
    local_file_timestamp, local_unit_start, now_ms, utc_sql_timestamp_ms, CalendarUnit,
};
use crate::tools::format_colon_data;

const LOG: &str = "UartbusLogFile";

pub const LOG_PREFIX: CliEntry = CliEntry {
    names: &["b", "-prefix"],
    description: "Log file prefix, it may contain path information",
};

pub const LOG_POSTFIX: CliEntry = CliEntry {
    names: &["a", "-postfix"],
    description: "Log file postfix, it may contain path information. Default: `.log`",
};

pub const LOG_ROTA_UNIT: CliEntry = CliEntry {
    names: &["r", "-rotate-interval"],
    description: "Log rotation interval, valid units are: year, month, day (default), hour, minute, secound or number as secounds.",
};

pub const LOG_FORMAT: CliEntry = CliEntry {
    names: &["f", "-log-format"],
    description: "Logging file format: binary, plain (default)",
};

const ENTRIES: &[&CliEntry] = &[
    &RPC_HOST,
    &RPC_PORT,
    &LOOPBACK,
    &LOG_PREFIX,
    &LOG_POSTFIX,
    &LOG_ROTA_UNIT,
    &LOG_FORMAT,
];

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LogFormat {
    /// One line per packet: `UTC time|colon separated bytes`
    Plain,
    /// Per packet: 8 byte big endian unix time in milliseconds then the packet, framed with the escape byte 0
    Binary,
}

impl LogFormat {
    pub fn parse(name: &str) -> Option<Self> {
        match name {
            "plain" => Some(LogFormat::Plain),
            "binary" => Some(LogFormat::Binary),
            _ => None,
        }
    }

    pub fn encode(self, time_ms: i64, packet: &[u8]) -> Vec<u8> {
        match self {
            LogFormat::Plain => format!(
                "{}|{}\n",
                utc_sql_timestamp_ms(time_ms),
                format_colon_data(packet)
            )
            .into_bytes(),
            LogFormat::Binary => {
                let mut data = time_ms.to_be_bytes().to_vec();
                data.extend_from_slice(packet);
                frame_bytes(&data, 0)
            }
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Rotation {
    Calendar(CalendarUnit),
    Seconds(i64),
}

impl Rotation {
    pub fn parse(format: &str) -> Result<Self, String> {
        Ok(Rotation::Calendar(match format {
            "year" => CalendarUnit::Year,
            "month" => CalendarUnit::Month,
            "day" => CalendarUnit::Day,
            "hour" => CalendarUnit::Hour,
            "minute" => CalendarUnit::Minute,
            "secound" | "second" => CalendarUnit::Second,
            other => {
                let seconds: i64 = other
                    .parse()
                    .map_err(|_| format!("Can't parse rotation interval: {format}"))?;
                if seconds < 1 {
                    return Err(format!("Invalid rotation interval: {format}"));
                }
                return Ok(Rotation::Seconds(seconds));
            }
        }))
    }

    /// The time when the log file that is current at `now` must be closed.
    pub fn next_cut_time(self, now: i64) -> i64 {
        match self {
            Rotation::Calendar(unit) => local_unit_start(now, unit, 1),
            Rotation::Seconds(seconds) => {
                let v = seconds * 1000;
                (now / v) * v + v
            }
        }
    }
}

pub struct LogRotaOutput {
    prefix: String,
    postfix: String,
    rotation: Rotation,
    format: LogFormat,
    current: Option<File>,
    next_cut_time: i64,
}

impl LogRotaOutput {
    pub fn new(prefix: &str, postfix: &str, rotation: Rotation, format: LogFormat) -> Self {
        Self {
            prefix: prefix.to_string(),
            postfix: postfix.to_string(),
            rotation,
            format,
            current: None,
            next_cut_time: 0,
        }
    }

    pub fn file_name(&self, time_ms: i64) -> String {
        format!(
            "{}{}{}",
            self.prefix,
            local_file_timestamp(time_ms),
            self.postfix
        )
    }

    /// Writes the packet into the log file that belongs to the time, opening a new one if needed.
    pub fn log(&mut self, time_ms: i64, packet: &[u8]) -> io::Result<()> {
        if self.current.is_none() || time_ms >= self.next_cut_time {
            self.current = None;
            let name = self.file_name(time_ms);
            if let Some(dir) = Path::new(&name).parent() {
                if !dir.as_os_str().is_empty() {
                    fs::create_dir_all(dir)?;
                }
            }
            self.current = Some(OpenOptions::new().create(true).append(true).open(name)?);
            self.next_cut_time = self.rotation.next_cut_time(time_ms);
        }

        let file = self.current.as_mut().unwrap();
        file.write_all(&self.format.encode(time_ms, packet))?;
        file.flush()
    }
}

pub fn main(args: &[String]) -> AppResult {
    let pa = match cli::parse_cli_opts(args) {
        Ok(pa) => pa,
        Err(err) => {
            eprintln!("{err}");
            cli::print_help_and_exit("UartbusLogFile", 1, ENTRIES)
        }
    };

    let req_format = LOG_FORMAT.get_simple(&pa).unwrap_or("plain");
    let format = LogFormat::parse(req_format);

    if format.is_none() {
        eprintln!("Unknown log format: {req_format}");
    }
    let (Some(format), None) = (format, cli::first_unknown_param(&pa, ENTRIES)) else {
        cli::print_help_and_exit("UartbusLogFile", 1, ENTRIES)
    };

    let mut logger = LogRotaOutput::new(
        LOG_PREFIX.get_simple(&pa).unwrap_or(""),
        LOG_POSTFIX.get_simple(&pa).unwrap_or(".log"),
        Rotation::parse(LOG_ROTA_UNIT.get_simple(&pa).unwrap_or("day"))?,
        format,
    );

    let rpc = open_cli_endpoint(&pa, loopback_initializer(&pa))?;
    rpc.start_streaming(move |packet| {
        if let Err(err) = logger.log(now_ms(), packet) {
            log(
                LOG,
                LogLevel::Error,
                &format!("Exception occurred while saving packet: {err}"),
            );
        }
    })?;

    rpc.join();
    Err("The connection to the RPC server is lost".into())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn plain_line_is_utc_time_then_the_bytes() {
        assert_eq!(
            LogFormat::Plain.encode(1_791_335_768_592, &[5, 63, 1, 0, 196]),
            b"2026-10-07 01:16:08.592U|5:63:1:0:196\n"
        );
    }

    #[test]
    fn binary_record_is_framed_time_and_packet() {
        assert_eq!(
            LogFormat::Binary.encode(0x0102, &[5, 0, 9]),
            vec![0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 5, 0, 0, 9, 0, 0xff]
        );
    }

    #[test]
    fn rotation_in_seconds_cuts_at_whole_multiples() {
        let rotation = Rotation::parse("10").unwrap();
        assert_eq!(rotation, Rotation::Seconds(10));
        assert_eq!(rotation.next_cut_time(25_300), 30_000);
        assert_eq!(rotation.next_cut_time(30_000), 40_000);
    }

    #[test]
    fn rotation_units_are_recognised_and_garbage_is_refused() {
        assert_eq!(
            Rotation::parse("day"),
            Ok(Rotation::Calendar(CalendarUnit::Day))
        );
        assert_eq!(
            Rotation::parse("secound"),
            Ok(Rotation::Calendar(CalendarUnit::Second))
        );
        assert!(Rotation::parse("fortnight").is_err());
        assert!(Rotation::parse("0").is_err());
    }
}
