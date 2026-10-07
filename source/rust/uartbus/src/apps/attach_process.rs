use std::io::{BufRead, BufReader, Write};
use std::process::{Command, Stdio};

use crate::apps::{loopback_initializer, open_cli_endpoint, AppResult};
use crate::cli::{self, CliEntry, LOOPBACK, RPC_HOST, RPC_PORT};
use crate::tools::{format_colon_data, parse_colon_data};

pub const PROGRAM: CliEntry = CliEntry {
    names: &["a", "-program"],
    description: "Application with arguments",
};

const ENTRIES: &[&CliEntry] = &[&RPC_HOST, &RPC_PORT, &LOOPBACK, &PROGRAM];

/// Splits a command line into program and arguments. Parts may be quoted with ' or ".
pub fn translate_commandline(to_process: &str) -> Result<Vec<String>, String> {
    #[derive(PartialEq)]
    enum State {
        Normal,
        InQuote,
        InDoubleQuote,
    }

    let mut state = State::Normal;
    let mut result = Vec::new();
    let mut current = String::new();
    let mut last_token_has_been_quoted = false;

    for c in to_process.chars() {
        match state {
            State::InQuote => {
                if c == '\'' {
                    last_token_has_been_quoted = true;
                    state = State::Normal;
                } else {
                    current.push(c);
                }
            }
            State::InDoubleQuote => {
                if c == '"' {
                    last_token_has_been_quoted = true;
                    state = State::Normal;
                } else {
                    current.push(c);
                }
            }
            State::Normal => match c {
                '\'' => state = State::InQuote,
                '"' => state = State::InDoubleQuote,
                ' ' => {
                    if last_token_has_been_quoted || !current.is_empty() {
                        result.push(std::mem::take(&mut current));
                    }
                    last_token_has_been_quoted = false;
                }
                other => {
                    current.push(other);
                    last_token_has_been_quoted = false;
                }
            },
        }
    }

    if last_token_has_been_quoted || !current.is_empty() {
        result.push(current);
    }

    if state != State::Normal {
        return Err(format!("unbalanced quotes in {to_process}"));
    }

    Ok(result)
}

pub fn main(args: &[String]) -> AppResult {
    let pa = cli::parse_or_help("UartbusAttachProcess", args, ENTRIES);

    let Some(prog) = PROGRAM.get_simple(&pa) else {
        eprintln!("No program specified");
        cli::print_help_and_exit("UartbusAttachProcess", 1, ENTRIES)
    };

    let command = translate_commandline(prog)?;
    let Some((program, arguments)) = command.split_first() else {
        eprintln!("No program specified");
        cli::print_help_and_exit("UartbusAttachProcess", 1, ENTRIES)
    };

    let mut proc = Command::new(program)
        .args(arguments)
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::inherit())
        .spawn()
        .map_err(|err| format!("Can't start `{program}`: {err}"))?;

    let mut to_process = proc.stdin.take().expect("piped stdin");
    let from_process = BufReader::new(proc.stdout.take().expect("piped stdout"));

    let rpc = open_cli_endpoint(&pa, loopback_initializer(&pa))?;

    //bus => process
    rpc.start_streaming(move |packet| {
        let line = format!("{}\n", format_colon_data(packet));
        if let Err(err) = to_process
            .write_all(line.as_bytes())
            .and_then(|_| to_process.flush())
        {
            eprintln!("{err}");
        }
    })?;

    //process => bus
    let conn = rpc.get_api()?;
    for line in from_process.lines() {
        let Ok(line) = line else { break };
        let sent = parse_colon_data(&line)
            .map_err(|err| format!("Not a packet from the process `{line}`: {err}"))
            .and_then(|packet| conn.send_packet(&packet).map_err(|err| err.to_string()));
        if let Err(err) = sent {
            eprintln!("{err}");
        }
    }

    println!("Process STDOUT closed");
    proc.wait()?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn command_line_is_split_at_spaces() {
        assert_eq!(
            translate_commandline("./ubh_linux  12 abc").unwrap(),
            vec!["./ubh_linux", "12", "abc"]
        );
        assert!(translate_commandline("").unwrap().is_empty());
    }

    #[test]
    fn quoted_parts_stay_together_and_may_be_empty() {
        assert_eq!(
            translate_commandline("sh -c 'echo \"1:2\"; cat' \"\" x").unwrap(),
            vec!["sh", "-c", "echo \"1:2\"; cat", "", "x"]
        );
    }

    #[test]
    fn unbalanced_quote_is_an_error() {
        assert!(translate_commandline("sh -c 'cat").is_err());
    }
}
