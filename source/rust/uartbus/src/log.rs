//! Minimal leveled logger writing the line format of the Java tools to stdout.
//!
//! The level is taken from the `UB_LOG_LEVEL` environment variable
//! (trace, debug, info (default), notice, warning, error, fatal).

use std::sync::OnceLock;

use crate::time::{now_ms, utc_sql_timestamp_ms};

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum LogLevel {
    Trace,
    Debug,
    Info,
    Notice,
    Warning,
    Error,
    Fatal,
}

impl LogLevel {
    pub fn parse(name: &str) -> Option<Self> {
        Some(match name.to_ascii_lowercase().as_str() {
            "trace" => LogLevel::Trace,
            "debug" => LogLevel::Debug,
            "info" => LogLevel::Info,
            "notice" => LogLevel::Notice,
            "warning" => LogLevel::Warning,
            "error" => LogLevel::Error,
            "fatal" => LogLevel::Fatal,
            _ => return None,
        })
    }

    pub fn label(self) -> &'static str {
        match self {
            LogLevel::Trace => "TRACE",
            LogLevel::Debug => "DEBUG",
            LogLevel::Info => "INFO",
            LogLevel::Notice => "NOTICE",
            LogLevel::Warning => "WARNING",
            LogLevel::Error => "ERROR",
            LogLevel::Fatal => "FATAL",
        }
    }
}

pub fn level() -> LogLevel {
    static LEVEL: OnceLock<LogLevel> = OnceLock::new();
    *LEVEL.get_or_init(|| {
        std::env::var("UB_LOG_LEVEL")
            .ok()
            .and_then(|name| LogLevel::parse(&name))
            .unwrap_or(LogLevel::Info)
    })
}

pub fn may_log(lvl: LogLevel) -> bool {
    lvl >= level()
}

pub fn format_line(time_ms: i64, facility: &str, lvl: LogLevel, message: &str) -> String {
    format!(
        "[{}]\t<{}>\t{{{}}}\t({})\t{}",
        utc_sql_timestamp_ms(time_ms),
        std::process::id(),
        facility,
        lvl.label(),
        message
    )
}

pub fn log(facility: &str, lvl: LogLevel, message: &str) {
    if may_log(lvl) {
        println!("{}", format_line(now_ms(), facility, lvl, message));
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn line_has_time_process_facility_level_and_message_separated_by_tabs() {
        let line = format_line(0, "UartbusCodeUploader", LogLevel::Info, "Target device: 4");
        let parts: Vec<&str> = line.split('\t').collect();
        assert_eq!(parts.len(), 5);
        assert_eq!(parts[0], "[1970-01-01 00:00:00.000U]");
        assert_eq!(parts[2], "{UartbusCodeUploader}");
        assert_eq!(parts[3], "(INFO)");
        assert_eq!(parts[4], "Target device: 4");
    }

    #[test]
    fn levels_are_ordered_from_trace_to_fatal() {
        assert!(LogLevel::Trace < LogLevel::Debug);
        assert!(LogLevel::Info < LogLevel::Warning);
        assert_eq!(LogLevel::parse("WARNING"), Some(LogLevel::Warning));
        assert_eq!(LogLevel::parse("loud"), None);
    }
}
