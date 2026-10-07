//! Wall clock helpers: the timestamp formats of the logs and the log rotation boundaries.

use std::time::{SystemTime, UNIX_EPOCH};

pub fn now_ms() -> i64 {
    match SystemTime::now().duration_since(UNIX_EPOCH) {
        Ok(d) => d.as_millis() as i64,
        Err(_) => 0,
    }
}

fn broken_down(ms: i64, local: bool) -> libc::tm {
    let secs = ms.div_euclid(1000) as libc::time_t;
    // SAFETY: both functions only write the tm we hand them.
    unsafe {
        let mut tm: libc::tm = std::mem::zeroed();
        if local {
            libc::localtime_r(&secs, &mut tm);
        } else {
            libc::gmtime_r(&secs, &mut tm);
        }
        tm
    }
}

/// `yyyy-MM-dd HH:mm:ss.SSSU` in UTC, eg: `2026-10-07 01:16:08.592U`
pub fn utc_sql_timestamp_ms(ms: i64) -> String {
    let tm = broken_down(ms, false);
    format!(
        "{:04}-{:02}-{:02} {:02}:{:02}:{:02}.{:03}U",
        tm.tm_year + 1900,
        tm.tm_mon + 1,
        tm.tm_mday,
        tm.tm_hour,
        tm.tm_min,
        tm.tm_sec,
        ms.rem_euclid(1000)
    )
}

/// `yyyy-MM-dd_HH-mm-ss_SSS` in local time, used in log file names.
pub fn local_file_timestamp(ms: i64) -> String {
    let tm = broken_down(ms, true);
    format!(
        "{:04}-{:02}-{:02}_{:02}-{:02}-{:02}_{:03}",
        tm.tm_year + 1900,
        tm.tm_mon + 1,
        tm.tm_mday,
        tm.tm_hour,
        tm.tm_min,
        tm.tm_sec,
        ms.rem_euclid(1000)
    )
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CalendarUnit {
    Year,
    Month,
    Day,
    Hour,
    Minute,
    Second,
}

/// Start of the calendar unit (in local time) that contains `ms`, moved by `offset` units.
pub fn local_unit_start(ms: i64, unit: CalendarUnit, offset: i32) -> i64 {
    let mut tm = broken_down(ms, true);

    if unit <= CalendarUnit::Minute {
        tm.tm_sec = 0;
    }
    if unit <= CalendarUnit::Hour {
        tm.tm_min = 0;
    }
    if unit <= CalendarUnit::Day {
        tm.tm_hour = 0;
    }
    if unit <= CalendarUnit::Month {
        tm.tm_mday = 1;
    }
    if unit <= CalendarUnit::Year {
        tm.tm_mon = 0;
    }

    match unit {
        CalendarUnit::Year => tm.tm_year += offset,
        CalendarUnit::Month => tm.tm_mon += offset,
        CalendarUnit::Day => tm.tm_mday += offset,
        CalendarUnit::Hour => tm.tm_hour += offset,
        CalendarUnit::Minute => tm.tm_min += offset,
        CalendarUnit::Second => tm.tm_sec += offset,
    }

    // let mktime find out whether daylight saving applies at the result
    tm.tm_isdst = -1;

    // SAFETY: mktime normalises and reads the tm we own.
    let secs = unsafe { libc::mktime(&mut tm) };
    secs as i64 * 1000
}

impl PartialOrd for CalendarUnit {
    fn partial_cmp(&self, other: &Self) -> Option<std::cmp::Ordering> {
        Some(self.cmp(other))
    }
}

impl Ord for CalendarUnit {
    fn cmp(&self, other: &Self) -> std::cmp::Ordering {
        (*self as u8).cmp(&(*other as u8))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn utc_timestamp_has_the_java_format() {
        assert_eq!(utc_sql_timestamp_ms(0), "1970-01-01 00:00:00.000U");
        assert_eq!(
            utc_sql_timestamp_ms(1_791_335_768_592),
            "2026-10-07 01:16:08.592U"
        );
    }

    #[test]
    fn file_timestamp_has_no_characters_that_need_escaping_in_a_file_name() {
        let ts = local_file_timestamp(1_791_335_768_592);
        assert_eq!(ts.len(), "2026-10-07_01-16-08_592".len());
        assert!(ts.ends_with("_592"));
        assert!(ts
            .chars()
            .all(|c| c.is_ascii_digit() || c == '-' || c == '_'));
    }

    #[test]
    fn next_unit_start_is_after_now_and_at_most_one_unit_away() {
        let now = 1_791_335_768_592;
        for (unit, max_ms) in [
            (CalendarUnit::Second, 1000i64),
            (CalendarUnit::Minute, 60_000),
            (CalendarUnit::Hour, 3_600_000),
            (CalendarUnit::Day, 25 * 3_600_000),
            (CalendarUnit::Month, 31 * 25 * 3_600_000),
            (CalendarUnit::Year, 366 * 25 * 3_600_000),
        ] {
            let start = local_unit_start(now, unit, 0);
            let next = local_unit_start(now, unit, 1);
            assert!(start <= now, "{unit:?}");
            assert!(next > now, "{unit:?}");
            assert!(next - now <= max_ms, "{unit:?}");
        }
    }
}
