//! Command line option handling of the `ub` subcommands.
//!
//! Same rules as the Java tools: an argument starting with `-` opens an option
//! (`-t` and `--to` are the two names of the same option), the arguments after
//! it are its values. Unlike the Java parser, `-` followed by digits only is a
//! value, so negative (group) addresses can be given: `-t -1`.

use std::collections::BTreeMap;
use std::str::FromStr;

pub type CliArgs = BTreeMap<String, Vec<String>>;

pub struct CliEntry {
    /// Names without the first `-`: `["t", "-to"]` for `-t` and `--to`
    pub names: &'static [&'static str],
    pub description: &'static str,
}

fn is_option(arg: &str) -> bool {
    match arg.strip_prefix('-') {
        Some(rest) => rest.is_empty() || !rest.bytes().all(|b| b.is_ascii_digit()),
        None => false,
    }
}

pub fn parse_cli_opts(args: &[String]) -> Result<CliArgs, String> {
    let mut params = CliArgs::new();
    let mut current: Option<String> = None;

    for arg in args {
        if is_option(arg) {
            if arg.len() < 2 {
                return Err(format!("Error at argument {arg}"));
            }
            let name = arg[1..].to_string();
            params.insert(name.clone(), Vec::new());
            current = Some(name);
        } else if let Some(name) = &current {
            params.get_mut(name).unwrap().push(arg.clone());
        } else {
            return Err(format!("Illegal parameter usage: {arg}"));
        }
    }

    Ok(params)
}

impl CliEntry {
    pub fn has_option(&self, args: &CliArgs) -> bool {
        self.names.iter().any(|name| args.contains_key(*name))
    }

    /// First value of the option, if it was given with a value.
    pub fn get_simple<'a>(&self, args: &'a CliArgs) -> Option<&'a str> {
        self.names
            .iter()
            .filter_map(|name| args.get(*name))
            .find_map(|values| values.first())
            .map(String::as_str)
    }

    pub fn parse<T: FromStr>(&self, args: &CliArgs) -> Result<Option<T>, String> {
        match self.get_simple(args) {
            Some(value) => value.parse::<T>().map(Some).map_err(|_| {
                format!(
                    "Error on parsing CliEntry: {} (value: `{}`)",
                    self.render().trim(),
                    value
                )
            }),
            None => Ok(None),
        }
    }

    pub fn parse_or<T: FromStr>(&self, args: &CliArgs, default: T) -> Result<T, String> {
        Ok(self.parse(args)?.unwrap_or(default))
    }

    pub fn render(&self) -> String {
        let names: Vec<String> = self.names.iter().map(|name| format!("-{name}")).collect();
        format!("\t{}\t\t{}", names.join(", "), self.description)
    }
}

pub fn render_list_all_option(entries: &[&CliEntry]) -> String {
    let mut out = String::new();
    for entry in entries {
        out.push_str(&entry.render());
        out.push('\n');
    }
    out
}

pub fn first_unknown_param(args: &CliArgs, entries: &[&CliEntry]) -> Option<String> {
    args.keys()
        .find(|name| !entries.iter().any(|e| e.names.contains(&name.as_str())))
        .cloned()
}

pub fn print_help_and_exit(program: &str, exit_code: i32, entries: &[&CliEntry]) -> ! {
    eprintln!("Usage of {program}:\n");
    eprintln!("{}", render_list_all_option(entries));
    std::process::exit(exit_code)
}

/// Parses the options of a subcommand; prints the usage and exits with 1 on an unknown option.
pub fn parse_or_help(program: &str, args: &[String], entries: &[&CliEntry]) -> CliArgs {
    let parsed = match parse_cli_opts(args) {
        Ok(parsed) => parsed,
        Err(err) => {
            eprintln!("{err}");
            print_help_and_exit(program, 1, entries)
        }
    };

    if first_unknown_param(&parsed, entries).is_some() {
        print_help_and_exit(program, 1, entries);
    }

    parsed
}

/**************************** options shared by the subcommands ****************************/

pub const SERIAL_BAUD: CliEntry = CliEntry {
    names: &["b", "-baud"],
    description: "Serial baud rate",
};

pub const RPC_HOST: CliEntry = CliEntry {
    names: &["h", "-host"],
    description: "Rpc server IP",
};

pub const RPC_PORT: CliEntry = CliEntry {
    names: &["p", "-port"],
    description: "Rpc port",
};

pub const FROM: CliEntry = CliEntry {
    names: &["f", "-from"],
    description: "Uartbus from address",
};

pub const TO: CliEntry = CliEntry {
    names: &["t", "-to"],
    description: "Uartbus to address",
};

pub const LOOPBACK: CliEntry = CliEntry {
    names: &["x", "-loopback-sent"],
    description: "Show sent packet",
};

pub const WORK_DIR: CliEntry = CliEntry {
    names: &["d", "-working-directory"],
    description: "Working directory",
};

pub const LOG_TIME: CliEntry = CliEntry {
    names: &["l", "-log-times"],
    description: "Log times",
};

pub const RECONNECT: CliEntry = CliEntry {
    names: &["r", "-reconnect"],
    description: "Reconnect",
};

pub const DECODE_PACKET: CliEntry = CliEntry {
    names: &["D", "-decode-packet"],
    description: "Decode packet",
};

pub const DEFAULT_FROM_ADDRESS: i32 = 63;
pub const DEFAULT_RPC_HOST: &str = "127.0.0.1";
pub const DEFAULT_RPC_PORT: u16 = 2112;

pub fn parse_from(args: &CliArgs) -> Result<i32, String> {
    FROM.parse_or(args, DEFAULT_FROM_ADDRESS)
}

pub fn parse_host(args: &CliArgs) -> String {
    RPC_HOST
        .get_simple(args)
        .unwrap_or(DEFAULT_RPC_HOST)
        .to_string()
}

pub fn parse_port(args: &CliArgs) -> Result<u16, String> {
    RPC_PORT.parse_or(args, DEFAULT_RPC_PORT)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn args(list: &[&str]) -> Vec<String> {
        list.iter().map(|s| s.to_string()).collect()
    }

    #[test]
    fn short_and_long_names_address_the_same_option() {
        let short = parse_cli_opts(&args(&["-t", "5"])).unwrap();
        let long = parse_cli_opts(&args(&["--to", "5"])).unwrap();
        assert_eq!(TO.parse::<i32>(&short).unwrap(), Some(5));
        assert_eq!(TO.parse::<i32>(&long).unwrap(), Some(5));
    }

    #[test]
    fn option_without_value_is_present_but_has_no_simple_value() {
        let parsed = parse_cli_opts(&args(&["-x", "-t", "5"])).unwrap();
        assert!(LOOPBACK.has_option(&parsed));
        assert_eq!(LOOPBACK.get_simple(&parsed), None);
        assert!(!LOG_TIME.has_option(&parsed));
    }

    #[test]
    fn negative_number_is_a_value_not_an_option() {
        let parsed = parse_cli_opts(&args(&["-t", "-1", "-f", "-12"])).unwrap();
        assert_eq!(TO.parse::<i32>(&parsed).unwrap(), Some(-1));
        assert_eq!(parse_from(&parsed).unwrap(), -12);
    }

    #[test]
    fn value_before_any_option_is_rejected() {
        assert!(parse_cli_opts(&args(&["5", "-t"])).is_err());
        assert!(parse_cli_opts(&args(&["-"])).is_err());
    }

    #[test]
    fn missing_option_falls_back_to_the_default() {
        let parsed = parse_cli_opts(&args(&[])).unwrap();
        assert_eq!(parse_from(&parsed).unwrap(), 63);
        assert_eq!(parse_host(&parsed), "127.0.0.1");
        assert_eq!(parse_port(&parsed).unwrap(), 2112);
    }

    #[test]
    fn unparsable_value_is_an_error() {
        let parsed = parse_cli_opts(&args(&["-t", "five"])).unwrap();
        assert!(TO.parse::<i32>(&parsed).is_err());
    }

    #[test]
    fn unknown_option_is_found() {
        let parsed = parse_cli_opts(&args(&["-t", "1", "-z"])).unwrap();
        assert_eq!(
            first_unknown_param(&parsed, &[&TO, &FROM]),
            Some("z".to_string())
        );
        assert_eq!(
            first_unknown_param(
                &parsed,
                &[
                    &TO,
                    &CliEntry {
                        names: &["z"],
                        description: ""
                    }
                ]
            ),
            None
        );
    }

    #[test]
    fn help_line_lists_every_name_and_the_description() {
        assert_eq!(TO.render(), "\t-t, --to\t\tUartbus to address");
    }
}
