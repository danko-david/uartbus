//! Services this machine provides on the bus: `ub service ftp`

use std::sync::Arc;

use crate::apps::{cli_bus_connect, AppResult};
use crate::cli::{self, CliEntry, FROM, RPC_HOST, RPC_PORT};
use crate::rpc::ftp::FtpBusService;
use crate::rpc::service::UartbusServiceBridge;
use crate::tools::parse_colon_data;

pub const RPC_PATH: CliEntry = CliEntry {
    names: &["P", "-path"],
    description: "Rpc prefix path to service",
};

pub const FTP_DIRECTORY: CliEntry = CliEntry {
    names: &["d", "-directory"],
    description: "FTP directory root in the local filesystem",
};

const FTP_ENTRIES: &[&CliEntry] = &[&RPC_HOST, &RPC_PORT, &FROM, &RPC_PATH, &FTP_DIRECTORY];

const USAGE: &str = "Usage: ub service <service>\n\nServices:\n\tftp\t\tServes the files of a directory on the bus\n";

fn ftp(args: &[String]) -> AppResult {
    let pa = cli::parse_or_help("UartbusFtpServer", args, FTP_ENTRIES);

    let Some(dir) = FTP_DIRECTORY.get_simple(&pa) else {
        eprintln!("FTP directory is not specified.");
        cli::print_help_and_exit("UartbusFtpServer", 1, FTP_ENTRIES)
    };

    let path = match RPC_PATH.get_simple(&pa) {
        Some(path) => parse_colon_data(path).map_err(|e| format!("Invalid path `{path}`: {e}"))?,
        None => Vec::new(),
    };

    let service = FtpBusService::new(dir)
        .map_err(|err| format!("Can't serve the directory `{dir}`: {err}"))?;

    //the service answers on the address the bus is connected with
    let bus = cli_bus_connect(&pa)?;
    let _bridge = UartbusServiceBridge::attach_service_to(
        Arc::new(service),
        &bus,
        Some(bus.get_from_address()),
        &path,
    );

    //requests are served on the packet stream thread
    loop {
        std::thread::park();
    }
}

pub fn main(args: &[String]) -> AppResult {
    match args.split_first() {
        Some((service, rest)) if service == "ftp" => ftp(rest),
        _ => {
            eprint!("{USAGE}");
            std::process::exit(1)
        }
    }
}
