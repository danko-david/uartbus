//! Intel HEX utilities: `ub ihex validate|size|replace_interrupts`

use std::collections::BTreeMap;
use std::path::Path;

use crate::apps::AppResult;
use crate::cli::{self, CliEntry};
use crate::ihex::{from_segments, IntelHexFile};

const IN: CliEntry = CliEntry {
    names: &["i", "-input-file"],
    description: "Input ihex format file",
};

const OUT: CliEntry = CliEntry {
    names: &["o", "-output-file"],
    description: "Output ihex format file",
};

const OFF: CliEntry = CliEntry {
    names: &["p", "-program-offset"],
    description: "Program code offset",
};

const ADDR: CliEntry = CliEntry {
    names: &["a", "-address-to-replace"],
    description: "Address to replace (optional)",
};

const REPLACE_ENTRIES: &[&CliEntry] = &[&IN, &OUT, &OFF, &ADDR];

const USAGE: &str = "Usage: ub ihex <function>\n\nFunctions:\n\tvalidate $file\t\tChecks the format and the checksums of the file\n\tsize $file\t\tPrints the number of the data bytes in the file\n\treplace_interrupts -i $in [-o $out] -p $offset [-a $address]\t\tRedirects the interrupt vectors that jump to $address (default: the most used one) into the vector table of the application at $offset\n";

/// Start of the vector table entries after the reset vector, each one is a 4 byte `jmp`.
const FIRST_VECTOR: usize = 4;

/// The destinations (byte addresses) of the `jmp` instructions of the interrupt vector table.
fn vector_destinations(data: &[u8]) -> Vec<(usize, u32)> {
    let mut vectors = Vec::new();
    let mut addr = FIRST_VECTOR;
    while addr + 4 <= data.len() {
        //opcode of jmp, little endian
        if data[addr] != 0x0c || data[addr + 1] != 0x94 {
            break;
        }
        let dst = ((u32::from(data[addr + 3]) << 8) | u32::from(data[addr + 2])) * 2;
        vectors.push((addr, dst));
        addr += 4;
    }
    vectors
}

/// The destination most vectors jump to: the handler of the unused interrupts.
pub fn find_most_used_destination(data: &[u8]) -> Result<u32, (i32, String)> {
    let mut counts: BTreeMap<u32, u32> = BTreeMap::new();
    for (_, dst) in vector_destinations(data) {
        *counts.entry(dst).or_insert(0) += 1;
    }

    let Some(max) = counts.values().copied().max() else {
        return Err((
            3,
            format!("No interrupt function found from the address {FIRST_VECTOR}"),
        ));
    };

    let mut most_used = counts.iter().filter(|(_, count)| **count == max);
    let (dst, _) = most_used.next().unwrap();
    if most_used.next().is_some() {
        return Err((
            4,
            format!("Multiple interrupt addresses used for the same time so specify explicitly the right one: {counts:?}"),
        ));
    }
    Ok(*dst)
}

/// Redirects the vectors that jump to `rewrite` to the same vector of the table at `offset`.
/// Returns how many vectors were changed.
pub fn rewrite_vectors(data: &mut [u8], rewrite: u32, offset: u32) -> usize {
    let mut changed = 0;
    for (addr, dst) in vector_destinations(data) {
        if dst == rewrite {
            //the address is given in code words
            let jmp = (addr as u32 + offset) / 2;
            data[addr + 2] = (jmp & 0xff) as u8;
            data[addr + 3] = ((jmp >> 8) & 0xff) as u8;
            changed += 1;
        }
    }
    changed
}

fn validate(file: &str) -> ! {
    match IntelHexFile::load_file(file) {
        Ok(_) => {
            println!("Valid.");
            std::process::exit(0)
        }
        Err(_) => {
            println!("Not valid.");
            std::process::exit(1)
        }
    }
}

fn size(file: &str) -> AppResult {
    let file = IntelHexFile::load_file(file)?;
    println!("{} bytes", file.get_data_size());
    Ok(())
}

fn replace_interrupts(args: &[String]) -> AppResult {
    let pa = match cli::parse_cli_opts(args) {
        Ok(pa) => pa,
        Err(err) => {
            eprintln!("{err}");
            cli::print_help_and_exit("ihex.replace_interrupts", 1, REPLACE_ENTRIES)
        }
    };

    if let Some(unknown) = cli::first_unknown_param(&pa, REPLACE_ENTRIES) {
        eprintln!("Invalid Cli switch: {unknown}");
        cli::print_help_and_exit("ihex.replace_interrupts", 1, REPLACE_ENTRIES)
    }

    let from = IN.get_simple(&pa);
    let offset = OFF.parse::<i64>(&pa)?;
    let address = ADDR.parse::<u32>(&pa)?;

    let mut fail = false;

    match from {
        None => {
            eprintln!("Ihex file not specified, use the -i cli switch to do that.");
            fail = true;
        }
        Some(from) if !Path::new(from).exists() => {
            eprintln!("Ihex file `{from}` not exist!");
            fail = true;
        }
        _ => {}
    }

    match offset {
        None => {
            eprintln!("Program relay offset not specified, use the -p cli switch to do that.");
            fail = true;
        }
        Some(offset) if !(0..0x1 << 16).contains(&offset) => {
            eprintln!(
                "Specified program relay offset `{offset}` is outside of the memory region (0-65536)."
            );
            fail = true;
        }
        _ => {}
    }

    let (Some(from), Some(offset), false) = (from, offset, fail) else {
        std::process::exit(2)
    };
    let to = OUT.get_simple(&pa).unwrap_or(from);

    let file = IntelHexFile::load_file(from)?;
    let mut codes = file.get_code()?;

    let Some(start) = codes.iter_mut().find(|code| code.start_address() == 0) else {
        eprintln!("Ihex doesn't contains code segment starts from 0 address.");
        std::process::exit(3)
    };

    let rewrite = match address {
        Some(address) => address,
        None => match find_most_used_destination(start.data()) {
            Ok(rewrite) => rewrite,
            Err((code, message)) => {
                eprintln!("{message}");
                std::process::exit(code)
            }
        },
    };

    println!("Rewriting: {rewrite}");
    rewrite_vectors(start.data_mut(), rewrite, offset as u32);

    from_segments(&codes)?.write_to_file(to)?;
    Ok(())
}

pub fn main(args: &[String]) -> AppResult {
    match args.split_first() {
        Some((function, rest)) => match (function.as_str(), rest) {
            ("validate", [file]) => validate(file),
            ("size", [file]) => size(file),
            ("replace_interrupts", rest) => replace_interrupts(rest),
            _ => {
                eprint!("{USAGE}");
                std::process::exit(1)
            }
        },
        None => {
            eprint!("{USAGE}");
            std::process::exit(1)
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// reset vector, then vectors jumping to the byte addresses
    fn table(destinations: &[u32]) -> Vec<u8> {
        let mut data = vec![0x0c, 0x94, 0x34, 0x00];
        for dst in destinations {
            let words = dst / 2;
            data.extend_from_slice(&[0x0c, 0x94, (words & 0xff) as u8, (words >> 8) as u8]);
        }
        //first instruction that is not a jmp ends the table
        data.extend_from_slice(&[0x11, 0x24, 0x1f, 0xbe]);
        data
    }

    #[test]
    fn most_used_vector_destination_is_the_default_handler() {
        assert_eq!(
            find_most_used_destination(&table(&[0x7c, 0x7c, 0x200, 0x7c])),
            Ok(0x7c)
        );
    }

    #[test]
    fn destination_above_one_byte_is_read_unsigned() {
        assert_eq!(
            find_most_used_destination(&table(&[0x120, 0x120])),
            Ok(0x120)
        );
    }

    #[test]
    fn tie_between_destinations_needs_an_explicit_address() {
        assert_eq!(
            find_most_used_destination(&table(&[0x7c, 0x200]))
                .unwrap_err()
                .0,
            4
        );
    }

    #[test]
    fn table_without_vectors_is_an_error() {
        assert_eq!(find_most_used_destination(&table(&[])).unwrap_err().0, 3);
    }

    #[test]
    fn only_the_vectors_of_the_given_destination_are_redirected_to_the_application_table() {
        let mut data = table(&[0x7c, 0x200, 0x7c]);
        assert_eq!(rewrite_vectors(&mut data, 0x7c, 0x2020), 2);

        //vector at 4 jumps to (4 + 0x2020) / 2 = 0x1012 words, vector at 12 to 0x1016
        assert_eq!(data[4..8], [0x0c, 0x94, 0x12, 0x10]);
        assert_eq!(data[8..12], [0x0c, 0x94, 0x00, 0x01]);
        assert_eq!(data[12..16], [0x0c, 0x94, 0x16, 0x10]);
        //the reset vector is never touched
        assert_eq!(data[0..4], [0x0c, 0x94, 0x34, 0x00]);
    }
}
