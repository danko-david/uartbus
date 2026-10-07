//! Code upload into a device, and the two restart commands (grab, reboot).

use std::time::Duration;

use crate::apps::{cli_bus_connect, to_hex, AppError, AppResult};
use crate::cli::{self, CliEntry, FROM, RPC_HOST, RPC_PORT, TO};
use crate::ihex::{concat_segments, CodeSegment, IntelHexFile};
use crate::log::{log, LogLevel};
use crate::rpc::device::{errno, RpcError, UartBusDevice, UbBootloaderVariable};

const LOG: &str = "UartbusCodeUploader";

pub const CODE: CliEntry = CliEntry {
    names: &["c", "-code"],
    description: "Code to upload in Intel Hex format",
};

pub const NO_VERIFY: CliEntry = CliEntry {
    names: &["n", "-no-verify"],
    description: "If option is present, skip the verification",
};

pub const SOFT: CliEntry = CliEntry {
    names: &["s", "-soft-reset"],
    description: "Soft reset",
};

const UPLOAD_ENTRIES: &[&CliEntry] = &[&RPC_HOST, &RPC_PORT, &FROM, &TO, &CODE, &NO_VERIFY];
const GRAB_ENTRIES: &[&CliEntry] = &[&RPC_HOST, &RPC_PORT, &FROM, &TO];
const REBOOT_ENTRIES: &[&CliEntry] = &[&RPC_HOST, &RPC_PORT, &FROM, &TO, &SOFT];

/// Bytes pushed (and verified) with one request.
pub const BLOCK_SIZE: usize = 32;

fn info(message: &str) {
    log(LOG, LogLevel::Info, message);
}

fn debug(message: &str) {
    log(LOG, LogLevel::Debug, message);
}

/// Loads the one code segment of the file; exits if there is none or more.
fn get_code_from_file(file: &str) -> Result<CodeSegment, AppError> {
    let file = IntelHexFile::load_file(file)?;
    let mut segments = concat_segments(file.get_code()?, 100, 0xff)?;

    match segments.len() {
        0 => {
            eprintln!("Ihex file doesn't contains any code.");
            std::process::exit(3)
        }
        1 => Ok(segments.remove(0)),
        _ => {
            eprintln!("Ihex file contains multiple code segments.");
            for segment in &segments {
                info(&format!(
                    "{:x}: {}",
                    segment.start_address(),
                    to_hex(segment.data())
                ));
            }
            std::process::exit(4)
        }
    }
}

/// Repeats the step while the device does not answer, at most `retry` times.
pub fn transaction<R>(
    retry: u32,
    mut step: impl FnMut() -> Result<R, RpcError>,
) -> Result<R, RpcError> {
    let mut last = RpcError::Transaction("No attempt was made".to_string());
    for _ in 0..retry {
        match step() {
            Err(RpcError::Transaction(msg)) => last = RpcError::Transaction(msg),
            other => return other,
        }
    }
    Err(last)
}

/// Resets the device and waits for the broadcast it sends after start.
fn reset_and_wait_start(dev: &UartBusDevice, done_message: &str) -> Result<(), RpcError> {
    let reboot = dev
        .get_bus()
        .subscribe_response(-1, dev.get_address(), &[], true);
    let result = dev.hardware_reset().and_then(|_| {
        //this waits until reboot complete
        reboot
            .ensure_response(Duration::from_secs(3), None)
            .map_err(RpcError::from)
    });

    match result {
        Ok(_) => {
            info(done_message);
            Ok(())
        }
        Err(err) => {
            eprintln!("Can't restart device or we just missed the restart signal.");
            Err(err)
        }
    }
}

/// Restarts the device and keeps it in the host program: the application is not started.
pub fn restart_grab_device(dev: &UartBusDevice) -> Result<(), RpcError> {
    info("Doing grab reboot");
    let mut dev = dev.clone();
    dev.timeout = Duration::from_millis(1000);

    reset_and_wait_start(&dev, "grab reboot done")?;

    transaction(dev.retry_count, || {
        dev.set_var(UbBootloaderVariable::IsSignalingSos, 0)
    })?;
    transaction(dev.retry_count, || {
        dev.set_var(UbBootloaderVariable::IsApplicationRunning, 0)
    })?;
    let app_run = transaction(dev.retry_count, || {
        dev.get_var(UbBootloaderVariable::IsApplicationRunning)
    })?;

    if app_run != 0 {
        let error =
            format!("After doing grab reboot application is still running. appRun:{app_run}");
        log(LOG, LogLevel::Error, &error);
        return Err(RpcError::Other(error));
    }
    Ok(())
}

/// Restarts the device; the application is started if nothing went wrong before (`flawed`).
fn restart_device(dev: &UartBusDevice, flawed: bool) -> Result<(), RpcError> {
    let mut dev = dev.clone();
    dev.timeout = Duration::from_millis(1000);

    if flawed {
        eprintln!("Something flawed, restarting device.");
    }

    reset_and_wait_start(&dev, "reboot done")?;

    transaction(dev.retry_count, || {
        dev.set_var(UbBootloaderVariable::IsSignalingSos, 0)
    })?;

    if !flawed {
        transaction(dev.retry_count, || {
            dev.set_var(UbBootloaderVariable::IsApplicationRunning, 1)
        })?;
    }
    Ok(())
}

/// Puts the device into flashing stage, pushes the code and commits it.
/// Returns the start address of the application region.
fn push_code(dev: &UartBusDevice, code: &CodeSegment) -> Result<u16, RpcError> {
    let code_start = code.start_address();
    let code_end = code_start + code.data().len() as u64;

    if dev.flash_get_stage()? != 0 {
        debug("Already in flashing stage");
    } else {
        transaction(dev.retry_count, || {
            debug("Start flashing");
            dev.flash_start()?;
            debug("Flashing started");
            Ok(())
        })?;
    }

    //verify start address match
    let start = dev.flash_get_next_address()?;
    if u64::from(start) != code_start {
        return Err(RpcError::Other(format!(
            "Application section and code start address mismatch: APP_START region: {start}, upload code start address: {code_start}"
        )));
    }

    //0: the device must be asked where to continue
    let mut addr = 0u16;
    loop {
        let done = transaction(dev.retry_count, || {
            if addr == 0 {
                addr = dev.flash_get_next_address()?;
            }

            debug(&format!("Next Address: {addr}"));
            if code_end <= u64::from(addr) {
                return Ok(true);
            }

            if addr == 0 {
                let msg = "NULL nextAddress received during the code upload. Maybe the target device has been resetted during the code upload process.This might be caused by a broken host system (uart bootloader) or insufficient device power supply.";
                log(LOG, LogLevel::Error, msg);
                return Err(RpcError::Other(msg.to_string()));
            }

            //an invalid adress might come when we get an response of an earlier request
            if u64::from(addr) < code_start {
                debug(&format!(
                    "Invalid calcualted offset: {}, retrying",
                    i64::from(addr) - code_start as i64
                ));
                return Ok(false);
            }

            let piece = code.get_code_piece((u64::from(addr) - code_start) as usize, BLOCK_SIZE);
            info(&format!("Uploading: {addr:x}: {}", to_hex(&piece)));

            match dev.flash_push_code(addr, &piece) {
                Ok(next) => addr = next,
                //the device expects an other address: ask it
                Err(RpcError::Posix(errno::ENXIO)) => addr = 0,
                Err(err) => return Err(err),
            }
            Ok(false)
        })?;

        if done {
            break;
        }
    }

    transaction(dev.retry_count, || {
        debug("Committing flash.");
        dev.flash_commit()?;
        info("Committing flash modification done.");
        Ok(())
    })?;

    Ok(start)
}

/// Reads back the uploaded code and compares it.
fn verify_code(dev: &UartBusDevice, code: &CodeSegment, start: u16) -> Result<(), RpcError> {
    info("Verifying uploaded code.");
    let code_start = code.start_address();
    let code_end = code_start + code.data().len() as u64;

    let mut addr = u64::from(start);
    while addr < code_end {
        transaction(dev.retry_count, || {
            info(&format!("Verifying code at {addr:x}:"));
            let piece = code.get_code_piece((addr - code_start) as usize, BLOCK_SIZE);

            let read = match dev.read_program_code(addr as u16, piece.len() as u8) {
                Ok(read) => Some(read),
                Err(err) => {
                    log(
                        LOG,
                        LogLevel::Notice,
                        &format!("Exception while getting code: readProgramCode: {err}"),
                    );
                    None
                }
            };

            let Some((read_addr, read_code)) = read.filter(|(a, _)| u64::from(*a) == addr) else {
                //retry
                return Err(RpcError::Transaction(format!(
                    "Different address returned, than the requested. Req: {addr}"
                )));
            };
            let _ = read_addr;

            if piece == read_code {
                info(&format!("Code at {addr:x} is OK"));
                Ok(())
            } else {
                info(&format!(
                    "Code at {addr:x} is mismatches. Actual:   {}, Expected: {}",
                    to_hex(&read_code),
                    to_hex(&piece)
                ));
                Err(RpcError::Other(
                    "Uploaded code verification failed.".to_string(),
                ))
            }
        })?;

        addr += BLOCK_SIZE as u64;
    }
    Ok(())
}

pub fn main(args: &[String]) -> AppResult {
    let pa = cli::parse_or_help("UartbusCodeUploader", args, UPLOAD_ENTRIES);

    let Some(file) = CODE.get_simple(&pa) else {
        info("No ihex file specified. Use -c cli switch to specify.");
        std::process::exit(1)
    };

    let Some(to) = TO.parse::<i32>(&pa)? else {
        info("No target bus device specified. Use -t cli switch to specify.");
        std::process::exit(1)
    };

    let code = get_code_from_file(file)?;
    let bus = cli_bus_connect(&pa)?;

    let mut dev = bus.device(to);
    dev.timeout = Duration::from_millis(100);
    dev.retry_count = 100;

    info(&format!("Target device: {}", dev.get_address()));

    debug("Pinging device");
    if let Err(err) = dev.ping() {
        eprintln!("{err}");
        info(&format!(
            "Requested bus device ({to}) not responding on the bus, maybe not attached."
        ));
        std::process::exit(2)
    }
    debug("Ping response response");

    //restart with hard reset and grab (ensure not enter to application mode)
    restart_grab_device(&dev)?;

    let uploaded = push_code(&dev, &code).and_then(|start| {
        if NO_VERIFY.has_option(&pa) {
            Ok(())
        } else {
            verify_code(&dev, &code, start)
        }
    });

    if let Err(err) = uploaded {
        eprintln!("{err}");
        //something flawed, reset the target device.
        let _ = restart_device(&dev, true);
        std::process::exit(1)
    }

    restart_device(&dev, false)?;
    Ok(())
}

pub fn grab_main(args: &[String]) -> AppResult {
    let pa = cli::parse_or_help("UartbusGrab", args, GRAB_ENTRIES);
    let to: i32 = TO.parse_or(&pa, -1)?;

    let bus = cli_bus_connect(&pa)?;
    restart_grab_device(&bus.device(to))?;
    Ok(())
}

pub fn reboot_main(args: &[String]) -> AppResult {
    let pa = cli::parse_or_help("UartbusReboot", args, REBOOT_ENTRIES);
    let to: i32 = TO.parse_or(&pa, -1)?;

    let bus = cli_bus_connect(&pa)?;
    let dev = bus.device(to);

    if SOFT.has_option(&pa) {
        dev.software_reset()?;
    } else {
        dev.hardware_reset()?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn step_is_repeated_while_the_device_does_not_answer() {
        let mut calls = 0;
        let result = transaction(5, || {
            calls += 1;
            if calls < 3 {
                Err(RpcError::Transaction("timeout".into()))
            } else {
                Ok(calls)
            }
        });
        assert_eq!(result, Ok(3));
    }

    #[test]
    fn step_is_given_up_after_the_retry_count() {
        let mut calls = 0;
        let result: Result<(), _> = transaction(4, || {
            calls += 1;
            Err(RpcError::Transaction("timeout".into()))
        });
        assert_eq!(result, Err(RpcError::Transaction("timeout".into())));
        assert_eq!(calls, 4);
    }

    #[test]
    fn other_errors_are_not_retried() {
        let mut calls = 0;
        let result: Result<(), _> = transaction(4, || {
            calls += 1;
            Err(RpcError::Posix(errno::EACCES))
        });
        assert_eq!(result, Err(RpcError::Posix(errno::EACCES)));
        assert_eq!(calls, 1);
    }
}
