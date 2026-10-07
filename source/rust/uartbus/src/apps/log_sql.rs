//! Saves the packets into an SQL table through the command line client of the
//! database (`sqlite3` or `mysql`), so no database library is linked.

use std::io::Write;
use std::process::{Child, Command, Stdio};

use crate::apps::{loopback_initializer, open_cli_endpoint, to_hex, AppError, AppResult};
use crate::cli::{self, CliEntry, LOOPBACK, RPC_HOST, RPC_PORT};
use crate::log::{log, LogLevel};
use crate::time::{now_ms, utc_sql_timestamp_ms};

const LOG: &str = "UartbusLogSql";

pub const SQL_CONNECTION: CliEntry = CliEntry {
    names: &["s", "-sql"],
    description: "Sql server connect string like: jdbc:mysql://127.0.0.1:3306/database or jdbc:sqlite:/path/to/file.db",
};

pub const SQL_TABLE: CliEntry = CliEntry {
    names: &["t", "-table"],
    description: "Sql server table",
};

pub const SQL_USER: CliEntry = CliEntry {
    names: &["u", "-user"],
    description: "Sql server username",
};

pub const SQL_PASSWORD: CliEntry = CliEntry {
    names: &["P", "-password"],
    description: "Sql server password. Alternatively you can specify the password as UB_SQL_PASSWORD environment variable if you wan to keep it safe",
};

const ENTRIES: &[&CliEntry] = &[
    &RPC_HOST,
    &RPC_PORT,
    &LOOPBACK,
    &SQL_CONNECTION,
    &SQL_TABLE,
    &SQL_USER,
    &SQL_PASSWORD,
];

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum SqlTarget {
    Sqlite {
        file: String,
    },
    Mysql {
        host: String,
        port: u16,
        database: String,
    },
}

impl SqlTarget {
    /// Understands the JDBC urls of the Java tool: `jdbc:sqlite:<file>`, `jdbc:mysql://host[:port]/database`
    pub fn parse(url: &str) -> Result<Self, String> {
        let rest = url
            .strip_prefix("jdbc:")
            .ok_or_else(|| format!("Sql connect string must start with `jdbc:`: {url}"))?;
        let (kind, location) = rest
            .split_once(':')
            .ok_or_else(|| format!("No database type in the connect string: {url}"))?;

        match kind.to_ascii_lowercase().as_str() {
            "sqlite" => Ok(SqlTarget::Sqlite {
                file: location.to_string(),
            }),
            "mysql" => {
                let location = location.strip_prefix("//").unwrap_or(location);
                let (address, database) = location
                    .split_once('/')
                    .ok_or_else(|| format!("No database name in the connect string: {url}"))?;
                let database = database.split('?').next().unwrap_or("").to_string();
                let (host, port) = match address.split_once(':') {
                    Some((host, port)) => (
                        host,
                        port.parse()
                            .map_err(|_| format!("Invalid port in the connect string: {url}"))?,
                    ),
                    None => (address, 3306),
                };
                Ok(SqlTarget::Mysql {
                    host: host.to_string(),
                    port,
                    database,
                })
            }
            other => Err(format!("Unknown database type: {other}")),
        }
    }

    /// Creates the table (id, date, ms, data) with its indexes, if it does not exist.
    pub fn create_table_sql(&self, table: &str) -> String {
        match self {
            SqlTarget::Sqlite { .. } => format!(
                "CREATE TABLE IF NOT EXISTS `{table}` (`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, `date` TIMESTAMP, `ms` BIGINT NOT NULL, `data` BLOB);\n\
                 CREATE INDEX IF NOT EXISTS `{table}_index_date` ON `{table}` (`date`);\n\
                 CREATE INDEX IF NOT EXISTS `{table}_index_ms` ON `{table}` (`ms`);\n\
                 CREATE INDEX IF NOT EXISTS `{table}_index_data` ON `{table}` (`data`);\n"
            ),
            SqlTarget::Mysql { .. } => format!(
                "CREATE TABLE IF NOT EXISTS `{table}` (`id` INT NOT NULL PRIMARY KEY AUTO_INCREMENT, `date` TIMESTAMP(3) NULL, `ms` BIGINT NOT NULL, `data` BLOB, INDEX `index_date` (`date`), INDEX `index_ms` (`ms`), INDEX `index_data` (`data`(128)));\n"
            ),
        }
    }

    fn client(&self, user: Option<&str>, password: Option<&str>) -> Command {
        match self {
            SqlTarget::Sqlite { file } => {
                let mut cmd = Command::new("sqlite3");
                cmd.arg("-batch").arg(file);
                cmd
            }
            SqlTarget::Mysql {
                host,
                port,
                database,
            } => {
                let mut cmd = Command::new("mysql");
                cmd.arg("--batch")
                    .arg("--unbuffered")
                    .arg("-h")
                    .arg(host)
                    .arg("-P")
                    .arg(port.to_string());
                if let Some(user) = user {
                    cmd.arg("-u").arg(user);
                }
                if let Some(password) = password {
                    //not in the arguments, those are visible for every user of the machine
                    cmd.env("MYSQL_PWD", password);
                }
                cmd.arg(database);
                cmd
            }
        }
    }
}

/// The table name is written into the statements, so only harmless characters are accepted.
pub fn check_table_name(table: &str) -> Result<(), String> {
    if !table.is_empty()
        && table
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b == b'_')
    {
        Ok(())
    } else {
        Err(format!(
            "Invalid table name `{table}`: only letters, digits and _ are accepted"
        ))
    }
}

/// `date` is the UTC time, `ms` is the unix time in milliseconds.
pub fn insert_sql(table: &str, time_ms: i64, packet: &[u8]) -> String {
    let date = utc_sql_timestamp_ms(time_ms);
    format!(
        "INSERT INTO `{table}` (date, ms, data) VALUES ('{}', {time_ms}, X'{}');\n",
        date.trim_end_matches('U'),
        to_hex(packet)
    )
}

fn start_client(
    target: &SqlTarget,
    user: Option<&str>,
    password: Option<&str>,
) -> Result<Child, AppError> {
    let mut cmd = target.client(user, password);
    let program = cmd.get_program().to_string_lossy().into_owned();
    cmd.stdin(Stdio::piped())
        .spawn()
        .map_err(|err| format!("Can't start the database client `{program}`: {err}").into())
}

pub fn main(args: &[String]) -> AppResult {
    let pa = cli::parse_or_help("UartbusLogSql", args, ENTRIES);

    let (Some(url), Some(table)) = (SQL_CONNECTION.get_simple(&pa), SQL_TABLE.get_simple(&pa))
    else {
        eprintln!("The sql connect string (-s) and the table (-t) must be specified.");
        cli::print_help_and_exit("UartbusLogSql", 1, ENTRIES)
    };

    let target = SqlTarget::parse(url)?;
    check_table_name(table)?;
    let table = table.to_string();

    let password = SQL_PASSWORD
        .get_simple(&pa)
        .map(str::to_string)
        .or_else(|| std::env::var("UB_SQL_PASSWORD").ok());

    let mut client = start_client(&target, SQL_USER.get_simple(&pa), password.as_deref())?;
    let mut sql = client.stdin.take().expect("piped stdin");
    sql.write_all(target.create_table_sql(&table).as_bytes())?;
    sql.flush()?;

    let rpc = open_cli_endpoint(&pa, loopback_initializer(&pa))?;
    rpc.start_streaming(move |packet| {
        let saved = sql
            .write_all(insert_sql(&table, now_ms(), packet).as_bytes())
            .and_then(|_| sql.flush());
        if let Err(err) = saved {
            log(
                LOG,
                LogLevel::Error,
                &format!("Exception ocurred while saving packet: {err}"),
            );
            //the database client is gone, nothing can be saved anymore
            std::process::exit(1);
        }
    })?;

    rpc.join();
    Err("The connection to the RPC server is lost".into())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn jdbc_urls_of_the_java_tool_are_understood() {
        assert_eq!(
            SqlTarget::parse("jdbc:sqlite:/tmp/bus.db"),
            Ok(SqlTarget::Sqlite {
                file: "/tmp/bus.db".to_string()
            })
        );
        assert_eq!(
            SqlTarget::parse("jdbc:mysql://127.0.0.1:3306/database"),
            Ok(SqlTarget::Mysql {
                host: "127.0.0.1".to_string(),
                port: 3306,
                database: "database".to_string()
            })
        );
        assert_eq!(
            SqlTarget::parse("jdbc:mysql://dbhost/bus?useSSL=false"),
            Ok(SqlTarget::Mysql {
                host: "dbhost".to_string(),
                port: 3306,
                database: "bus".to_string()
            })
        );
        assert!(SqlTarget::parse("jdbc:oracle:thin").is_err());
        assert!(SqlTarget::parse("/tmp/bus.db").is_err());
    }

    #[test]
    fn packet_is_inserted_with_time_and_hex_blob() {
        assert_eq!(
            insert_sql("packets", 1_791_335_768_592, &[5, 63, 1, 0, 196]),
            "INSERT INTO `packets` (date, ms, data) VALUES ('2026-10-07 01:16:08.592', 1791335768592, X'053f0100c4');\n"
        );
    }

    #[test]
    fn table_name_with_sql_in_it_is_refused() {
        assert!(check_table_name("bus_packets2").is_ok());
        assert!(check_table_name("x`; DROP TABLE y; --").is_err());
        assert!(check_table_name("").is_err());
    }
}
