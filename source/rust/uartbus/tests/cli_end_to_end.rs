//! The `ub` executable as a user starts it: a real RPC server process, real
//! commands, and a device simulated in the test.

mod common;

use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::path::Path;
use std::time::Duration;

use common::device::{SimulatedDevice, APP_START_ADDRESS};
use common::{free_port, run_ub, start_dummy_server, start_server, wait_until, TempDir, Ub};
use uartbus::ihex::IntelHexFile;
use uartbus::rpc::connection::UartbusConnection;
use uartbus::rpc::device::{UbFunction, UbRet};
use uartbus::rpc::{RpcType, RpcValue, TcpRpcClient, UartBus};
use uartbus::VUnsigned;

/// Some bytes that look like code, every position different from its neighbours.
fn sample_code(length: usize) -> Vec<u8> {
    (0..length).map(|i| (i * 7 + 13) as u8).collect()
}

fn write_hex(path: &Path, start: u16, code: &[u8]) {
    let mut file = IntelHexFile::new();
    for (i, chunk) in code.chunks(16).enumerate() {
        file.push_line(file.create_data_line(u32::from(start) + (i * 16) as u32, chunk));
    }
    file.push_line(file.create_eof_line());
    file.write_to_file(path).unwrap();
}

fn make_executable(path: &Path, content: &str) {
    fs::write(path, content).unwrap();
    fs::set_permissions(path, fs::Permissions::from_mode(0o755)).unwrap();
}

/**************************************** upload ****************************************/

#[test]
fn upload_writes_the_code_into_the_device_verifies_it_and_starts_the_application() {
    let (_server, port) = start_dummy_server();
    let device = SimulatedDevice::attach(port, 4);

    let dir = TempDir::new("upload");
    let hex = dir.path().join("app.hex");
    let code = sample_code(100);
    write_hex(&hex, APP_START_ADDRESS, &code);

    let (exit, stdout, stderr) = run_ub(&[
        "upload",
        "-p",
        &port.to_string(),
        "-t",
        "4",
        "-c",
        hex.to_str().unwrap(),
    ]);

    assert_eq!(exit, 0, "{stdout}\n{stderr}");
    assert_eq!(device.application_code(code.len()), code);
    assert!(stdout.contains("Target device: 4"), "{stdout}");
    assert!(stdout.contains("Uploading: 2000: 0d141b"), "{stdout}");
    assert!(stdout.contains("Code at 2060 is OK"), "{stdout}");

    let state = device.state.lock().unwrap();
    //restarted before (grab) and after the upload, then the application is started
    assert_eq!(state.resets, 2);
    assert!(state.app_run);
    assert_eq!(state.flash_stage, 0);
}

#[test]
fn upload_gets_through_when_answers_are_lost_on_the_bus() {
    let (_server, port) = start_dummy_server();
    let device = SimulatedDevice::attach(port, 4);
    device.state.lock().unwrap().lose_every = 4;

    let dir = TempDir::new("upload_lossy");
    let hex = dir.path().join("app.hex");
    let code = sample_code(200);
    write_hex(&hex, APP_START_ADDRESS, &code);

    let (exit, stdout, stderr) = run_ub(&[
        "upload",
        "-p",
        &port.to_string(),
        "-t",
        "4",
        "-c",
        hex.to_str().unwrap(),
    ]);

    assert_eq!(exit, 0, "{stdout}\n{stderr}");
    assert_eq!(device.application_code(code.len()), code);
    assert!(device.state.lock().unwrap().app_run);
}

#[test]
fn upload_fails_when_the_code_read_back_differs() {
    let (_server, port) = start_dummy_server();
    let device = SimulatedDevice::attach(port, 4);
    device.state.lock().unwrap().broken_cell = Some(APP_START_ADDRESS + 40);

    let dir = TempDir::new("upload_broken");
    let hex = dir.path().join("app.hex");
    write_hex(&hex, APP_START_ADDRESS, &sample_code(100));

    let (exit, stdout, stderr) = run_ub(&[
        "upload",
        "-p",
        &port.to_string(),
        "-t",
        "4",
        "-c",
        hex.to_str().unwrap(),
    ]);

    assert_eq!(exit, 1, "{stdout}\n{stderr}");
    assert!(stdout.contains("Code at 2020 is mismatches"), "{stdout}");
    assert!(
        stderr.contains("Uploaded code verification failed."),
        "{stderr}"
    );
    //the device is restarted, but the broken application is not started
    assert!(!device.state.lock().unwrap().app_run);
}

#[test]
fn upload_without_verification_does_not_read_the_code_back() {
    let (_server, port) = start_dummy_server();
    let device = SimulatedDevice::attach(port, 4);
    //would fail the verification
    device.state.lock().unwrap().broken_cell = Some(APP_START_ADDRESS);

    let dir = TempDir::new("upload_noverify");
    let hex = dir.path().join("app.hex");
    let code = sample_code(40);
    write_hex(&hex, APP_START_ADDRESS, &code);

    let (exit, stdout, stderr) = run_ub(&[
        "upload",
        "-p",
        &port.to_string(),
        "-t",
        "4",
        "-n",
        "-c",
        hex.to_str().unwrap(),
    ]);

    assert_eq!(exit, 0, "{stdout}\n{stderr}");
    assert!(!stdout.contains("Verifying"), "{stdout}");
    assert_eq!(device.application_code(code.len()), code);
}

#[test]
fn upload_refuses_code_that_does_not_start_at_the_application_region() {
    let (_server, port) = start_dummy_server();
    let device = SimulatedDevice::attach(port, 4);

    let dir = TempDir::new("upload_wrong_start");
    let hex = dir.path().join("app.hex");
    write_hex(&hex, 0x3000, &sample_code(20));

    let (exit, stdout, stderr) = run_ub(&[
        "upload",
        "-p",
        &port.to_string(),
        "-t",
        "4",
        "-c",
        hex.to_str().unwrap(),
    ]);

    assert_eq!(exit, 1, "{stdout}\n{stderr}");
    assert!(stderr.contains("start address mismatch"), "{stderr}");
    assert_eq!(device.application_code(4), vec![0xff; 4]);
}

#[test]
fn upload_to_a_device_that_is_not_on_the_bus_exits_with_2() {
    let (_server, port) = start_dummy_server();

    let dir = TempDir::new("upload_nodev");
    let hex = dir.path().join("app.hex");
    write_hex(&hex, APP_START_ADDRESS, &sample_code(20));

    let (exit, stdout, _) = run_ub(&[
        "upload",
        "-p",
        &port.to_string(),
        "-t",
        "9",
        "-c",
        hex.to_str().unwrap(),
    ]);

    assert_eq!(exit, 2);
    assert!(
        stdout.contains("Requested bus device (9) not responding on the bus, maybe not attached."),
        "{stdout}"
    );
}

#[test]
fn upload_needs_the_file_and_the_target() {
    let (exit, stdout, _) = run_ub(&["upload", "-t", "4"]);
    assert_eq!(exit, 1);
    assert!(stdout.contains("No ihex file specified."), "{stdout}");

    let (exit, stdout, _) = run_ub(&["upload", "-c", "x.hex"]);
    assert_eq!(exit, 1);
    assert!(
        stdout.contains("No target bus device specified."),
        "{stdout}"
    );
}

/*********************************** device commands ***********************************/

#[test]
fn packetloss_counts_the_answered_and_the_lost_pings() {
    let (_server, port) = start_dummy_server();
    let _device = SimulatedDevice::attach(port, 4);
    let port = port.to_string();

    let (exit, stdout, _) = run_ub(&["packetloss", "-p", &port, "-t", "4", "-c", "3", "-i", "0"]);
    assert_eq!(exit, 0);
    assert_eq!(
        stdout,
        "0. pong\n1. pong\n2. pong\nStatistic:\n\tSent packets: 3\n\tReceived packets: 3\n\tLost packets: 0\n\tPacket loss ratio: 0 %\n"
    );

    //nobody has the address 9
    let (exit, stdout, _) = run_ub(&[
        "packetloss",
        "-p",
        &port,
        "-t",
        "9",
        "-c",
        "2",
        "-i",
        "0",
        "-l",
        "30",
    ]);
    assert_eq!(exit, 0);
    assert!(
        stdout.starts_with("0. -- loss --\n1. -- loss --\n"),
        "{stdout}"
    );
    assert!(stdout.ends_with("\tPacket loss ratio: 100 %\n"), "{stdout}");
}

#[test]
fn grab_restarts_the_device_and_leaves_the_application_stopped() {
    let (_server, port) = start_dummy_server();
    let device = SimulatedDevice::attach(port, 4);
    assert!(device.state.lock().unwrap().app_run);

    let (exit, stdout, stderr) = run_ub(&["grab", "-p", &port.to_string(), "-t", "4"]);
    assert_eq!(exit, 0, "{stdout}\n{stderr}");
    assert!(stdout.contains("grab reboot done"), "{stdout}");

    let state = device.state.lock().unwrap();
    assert_eq!(state.resets, 1);
    assert!(!state.app_run);
}

#[test]
fn reboot_resets_the_device() {
    let (_server, port) = start_dummy_server();
    let device = SimulatedDevice::attach(port, 4);
    let port = port.to_string();

    assert_eq!(run_ub(&["reboot", "-p", &port, "-t", "4"]).0, 0);
    wait_until("hardware reset", || {
        device.state.lock().unwrap().resets == 1
    });

    assert_eq!(run_ub(&["reboot", "-p", &port, "-t", "4", "-s"]).0, 0);
    wait_until("software reset", || {
        device.state.lock().unwrap().resets == 2
    });
}

#[test]
fn ping_sends_requests_and_shows_what_arrives() {
    let (_server, port) = start_dummy_server();
    let _device = SimulatedDevice::attach(port, 4);

    let ping = Ub::start(&[
        "ping",
        "-p",
        &port.to_string(),
        "-t",
        "4",
        "-c",
        "1",
        "-i",
        "10",
    ]);
    ping.expect_stdout("ping 63 > 4\n");
    //the request (the dummy server gives it back) and the answer of the device: 0:1:0
    ping.expect_stdout("4:63:1:0:");
    ping.expect_stdout("63:4:0:1:0:");
}

#[test]
fn blink_toggles_the_user_led_periodically() {
    let (_server, port) = start_dummy_server();
    let device = SimulatedDevice::attach(port, 4);

    let blink = Ub::start(&["blink", "-p", &port.to_string(), "-t", "4", "-i", "20"]);
    blink.expect_stdout("blink 63 > 4\n");
    wait_until("led toggled a few times", || {
        device.state.lock().unwrap().requests >= 3
    });
}

#[test]
fn console_sends_the_typed_packets_and_shows_the_received_ones() {
    let (_server, port) = start_dummy_server();
    let _device = SimulatedDevice::attach(port, 4);

    let mut console = Ub::start(&["console", "-p", &port.to_string(), "-t", "4", "-f", "12"]);

    console.type_line("?");
    console.expect_stdout(
        "lines started with numbers and separated with colon is recognised as packet to send",
    );

    //ping the device
    console.type_line("1:0");
    console.expect_stdout("\n12:4:0:1:0:");

    console.type_line("?<");
    console.expect_stdout("To address: 4\n");
    console.type_line("?>");
    console.expect_stdout("From address: 12\n");

    console.type_line("hello");
    console.expect_stdout(
        "Unrecognised command or packet sequence: `hello` enter `?` to display help\n",
    );
}

#[test]
fn console_can_send_one_packet_and_exit() {
    let (_server, port) = start_dummy_server();
    let device = SimulatedDevice::attach(port, 4);

    //user led on
    let (exit, _, stderr) = run_ub(&[
        "console",
        "-p",
        &port.to_string(),
        "-t",
        "4",
        "-d",
        "1:2:1",
        "-e",
    ]);
    assert_eq!(exit, 0, "{stderr}");
    wait_until("led turned on", || device.state.lock().unwrap().led);
}

#[test]
fn console_decodes_packets_and_marks_times_when_asked() {
    let (_server, port) = start_dummy_server();
    let _device = SimulatedDevice::attach(port, 4);

    let mut console = Ub::start(&["console", "-p", &port.to_string(), "-t", "4", "-D", "-l"]);
    console.type_line("1:0");
    console.expect_stdout("] From: 4, To: 63, payload: 0:1:0, crc8: ");
    //the send time and the receive times
    console.expect_stdout("<[20");
    console.expect_stdout(">[20");
}

#[test]
fn collision_packetloss_reports_which_devices_answered_the_broadcasts() {
    let (_server, port) = start_dummy_server();

    //two "devices" that answer the broadcast replay request like a real one does
    let answerers: Vec<UartBus> = [4, 7]
        .into_iter()
        .map(|address| {
            let bus = UartBus::from_tcp("127.0.0.1", port, address).unwrap();
            let sender = bus.clone();
            bus.add_unrelated_packet_listener(std::sync::Arc::new(move |raw: &[u8]| {
                //to: 0 (broadcast), from: 63, payload: 1:1:sequence
                if raw.len() == 5 && raw[..4] == [0, 63, 1, 1] {
                    let answer =
                        uartbus::to_packet(63, address, &[RpcValue::Bytes(vec![0, 1, 1, raw[4]])])
                            .unwrap();
                    sender.send_raw_packet(&answer).unwrap();
                }
            }));
            bus
        })
        .collect();

    let mut ub = Ub::start(&[
        "collisionPacketloss",
        "-p",
        &port.to_string(),
        "-c",
        "3",
        "-i",
        "30",
    ]);
    ub.stdin = None;
    let status = ub.wait(Duration::from_secs(10));
    let stdout = ub.stdout();

    assert_eq!(status.code(), Some(0), "{stdout}");
    assert!(stdout.contains("2. broadcast ping \n"), "{stdout}");
    assert!(stdout.contains("Statistic for bus device 4: \tSent packets: 3\tReceived packets: 3\tLost packets: 0\tPacket loss ratio: 0 %\n"), "{stdout}");
    assert!(stdout.contains("Statistic for bus device 7: \tSent packets: 3\tReceived packets: 3\tLost packets: 0\tPacket loss ratio: 0 %\n"), "{stdout}");
    assert!(stdout.ends_with("Collisions: {}\n"), "{stdout}");
    drop(answerers);
}

/************************************ bus listeners ************************************/

#[test]
fn log_file_saves_the_packets_with_time_into_a_file_named_after_the_start_time() {
    let (_server, port) = start_dummy_server();
    let dir = TempDir::new("logfile");
    let prefix = format!("{}/logs/bus-", dir.path().display());

    let _logger = Ub::start(&[
        "logFile",
        "-p",
        &port.to_string(),
        "-b",
        &prefix,
        "-a",
        ".txt",
        "-r",
        "hour",
    ]);

    let sender = TcpRpcClient::connect("127.0.0.1", port).unwrap();
    let log_dir = dir.path().join("logs");
    let logged = || -> Option<String> {
        let entry = fs::read_dir(&log_dir).ok()?.next()?.ok()?;
        let name = entry.file_name().to_string_lossy().into_owned();
        assert!(
            name.starts_with("bus-20") && name.ends_with(".txt"),
            "{name}"
        );
        fs::read_to_string(entry.path()).ok()
    };

    //the logger connects a bit after its start: send until the first packet is caught
    wait_until("packet logged", || {
        sender.send_packet(&[1, 2, 3]).unwrap();
        logged().is_some_and(|text| text.contains("|1:2:3\n"))
    });

    let text = logged().unwrap();
    let line = text.lines().next().unwrap();
    //2026-10-07 01:16:08.592U|1:2:3
    assert_eq!(line.len(), "2026-10-07 01:16:08.592U|1:2:3".len(), "{line}");
    assert_eq!(&line[23..], "U|1:2:3");
}

#[test]
fn log_file_refuses_unknown_format() {
    let (exit, _, stderr) = run_ub(&["logFile", "-f", "xml"]);
    assert_eq!(exit, 1);
    assert!(
        stderr.starts_with("Unknown log format: xml\nUsage of UartbusLogFile:"),
        "{stderr}"
    );
}

#[test]
fn log_sql_feeds_create_table_and_inserts_to_the_database_client() {
    let (_server, port) = start_dummy_server();
    let dir = TempDir::new("logsql");

    //stands in for the sqlite3 command: saves what it gets on stdin next to the database file
    make_executable(
        &dir.path().join("sqlite3"),
        "#!/bin/sh\nexec cat > \"$2.statements\"\n",
    );
    let database = dir.path().join("bus.db");
    let path = format!(
        "{}:{}",
        dir.path().display(),
        std::env::var("PATH").unwrap()
    );

    let _logger = Ub::start_with(
        &[
            "logSql",
            "-p",
            &port.to_string(),
            "-t",
            "packets",
            "-s",
            &format!("jdbc:sqlite:{}", database.display()),
        ],
        |command| {
            command.env("PATH", path);
        },
    );

    let sender = TcpRpcClient::connect("127.0.0.1", port).unwrap();
    let statements = dir.path().join("bus.db.statements");
    wait_until("packet inserted", || {
        sender.send_packet(&[1, 2, 255]).unwrap();
        fs::read_to_string(&statements).is_ok_and(|sql| sql.contains("X'0102ff');"))
    });

    let sql = fs::read_to_string(&statements).unwrap();
    assert!(
        sql.starts_with("CREATE TABLE IF NOT EXISTS `packets` ("),
        "{sql}"
    );
    assert!(
        sql.contains("\nINSERT INTO `packets` (date, ms, data) VALUES ('20"),
        "{sql}"
    );
}

#[test]
fn attach_process_connects_the_stdio_of_a_program_to_the_bus() {
    let (_server, port) = start_dummy_server();

    //the program answers 9:9:9 to the first packet it gets and repeats what it got
    let _attached = Ub::start(&[
        "attachProcess",
        "-p",
        &port.to_string(),
        "-a",
        "sh -c 'read packet; echo 9:9:9; echo \"$packet\"; cat > /dev/null'",
    ]);

    let client = TcpRpcClient::connect("127.0.0.1", port).unwrap();
    client.init().unwrap();

    //the program is attached a bit after the start: send until it answers
    let (tx, rx) = std::sync::mpsc::channel();
    let client = std::sync::Arc::new(client);
    let receiver = std::sync::Arc::clone(&client);
    std::thread::spawn(move || {
        while let Ok(packet) = receiver.get_next_packet() {
            if tx.send(packet).is_err() {
                break;
            }
        }
    });

    let mut seen = Vec::new();
    wait_until("answer of the program", || {
        client.send_packet(&[1, 2, 3]).unwrap();
        seen.extend(rx.try_iter());
        seen.contains(&vec![9, 9, 9])
    });
}

#[test]
fn ftp_service_serves_a_directory_on_the_bus() {
    let (_server, port) = start_dummy_server();
    let dir = TempDir::new("ftp_cli");
    fs::write(dir.path().join("readme"), b"served").unwrap();

    let _service = Ub::start(&[
        "service",
        "ftp",
        "-p",
        &port.to_string(),
        "-f",
        "10",
        "-P",
        "32",
        "-d",
        dir.path().to_str().unwrap(),
    ]);

    let bus = UartBus::from_tcp("127.0.0.1", port, 63).unwrap();
    let mut node = bus.device(10);
    node.timeout = Duration::from_millis(100);
    let vu = |n: u32| RpcValue::VUnsigned(VUnsigned::new(n));

    //the service attaches a bit after the start: ask until it answers
    let exists = UbFunction::new(1, UbRet::Value(RpcType::Bool)).posix();
    wait_until("service answers", || {
        node.call(&[32], &exists, &[vu(0)]) == Ok(Some(RpcValue::Bool(true)))
    });

    //file 1 is the only file of the directory
    let part = UbFunction::new(20, UbRet::Value(RpcType::Bytes)).posix();
    assert_eq!(
        node.call(&[32], &part, &[vu(1), vu(0), vu(100)]),
        Ok(Some(RpcValue::Bytes(b"served".to_vec())))
    );
    bus.close();
}

/************************************** rpcServer **************************************/

/// Starts the server with a program in the place of the gateway.
fn start_serial_server(gateway: &Path, extra: &[&str]) -> (Ub, u16) {
    let mut args = vec![
        "-s",
        "/dev/null",
        "-b",
        "115200",
        "-i",
        gateway.to_str().unwrap(),
    ];
    args.extend_from_slice(extra);
    start_server(&args)
}

#[test]
fn server_passes_packets_between_the_clients_and_the_gateway() {
    let dir = TempDir::new("gateway");
    //a gateway on a bus where nothing answers: gives back what is sent to it,
    //and tells how it was started
    let gateway = dir.path().join("gateway");
    make_executable(
        &gateway,
        &format!(
            "#!/bin/sh\necho \"$@\" > {}/args\nexec cat\n",
            dir.path().display()
        ),
    );

    let (_server, port) = start_serial_server(&gateway, &[]);

    let client = TcpRpcClient::connect("127.0.0.1", port).unwrap();
    client.init().unwrap();
    client.send_packet(&[1, 0xff, 2, 0]).unwrap();

    //once from the server (packets of the clients are shown to every client), once from the gateway
    assert_eq!(client.get_next_packet().unwrap(), vec![1, 0xff, 2, 0]);
    assert_eq!(client.get_next_packet().unwrap(), vec![1, 0xff, 2, 0]);

    //the gateway program gets the device and the baud rate
    assert_eq!(
        fs::read_to_string(dir.path().join("args")).unwrap(),
        "/dev/null 115200\n"
    );
}

#[test]
fn server_exits_with_5_when_the_gateway_is_lost() {
    let dir = TempDir::new("gateway_lost");
    let gateway = dir.path().join("gateway");
    //lives until the first byte arrives
    make_executable(&gateway, "#!/bin/sh\nhead -c 1 > /dev/null\n");

    let (mut server, port) = start_serial_server(&gateway, &[]);

    let client = TcpRpcClient::connect("127.0.0.1", port).unwrap();
    let _ = client.send_packet(&[1, 2, 3]);

    assert_eq!(server.wait(Duration::from_secs(5)).code(), Some(5));
}

#[test]
fn server_reopens_the_lost_gateway_when_reconnect_is_asked() {
    let dir = TempDir::new("gateway_reconnect");
    let gateway = dir.path().join("gateway");
    //counts its starts; the first one ends after the first packet, the later ones echo
    make_executable(
        &gateway,
        &format!(
            "#!/bin/sh\necho start >> {dir}/starts\nif [ \"$(wc -l < {dir}/starts)\" = 1 ]; then head -c 1 > /dev/null; else exec cat; fi\n",
            dir = dir.path().display()
        ),
    );

    let (_server, port) = start_serial_server(&gateway, &["-r"]);

    let client = TcpRpcClient::connect("127.0.0.1", port).unwrap();
    client.send_packet(&[1, 2, 3]).unwrap();

    wait_until("gateway started again", || {
        fs::read_to_string(dir.path().join("starts")).is_ok_and(|s| s.lines().count() >= 2)
    });

    //this client gets nothing from the server itself, only what the (new) gateway gives back
    client.set_attribute("echo_loopback", "false").unwrap();
    client.send_packet(&[7, 7]).unwrap();
    assert_eq!(client.get_next_packet().unwrap(), vec![7, 7]);
}

#[test]
fn server_in_serial_mode_needs_the_device_and_the_baud_rate() {
    let (exit, _, stderr) = run_ub(&["rpcServer", "-p", "1"]);
    assert_eq!(exit, 2);
    assert!(
        stderr.contains("No Serial port specified.\nNo Serial baud rate specified.\n"),
        "{stderr}"
    );
    assert!(stderr.contains("Usage of UarbusRpcServer:"), "{stderr}");
}

/*************************************** the rest ***************************************/

#[test]
fn without_command_the_commands_are_listed() {
    let (exit, _, stderr) = run_ub(&[]);
    assert_eq!(exit, 1);
    for command in [
        "rpcServer",
        "console",
        "upload",
        "collisionPacketloss",
        "service",
    ] {
        assert!(stderr.contains(&format!("\t{command}\t")), "{stderr}");
    }

    let (exit, _, stderr) = run_ub(&["teleport"]);
    assert_eq!(exit, 1);
    assert!(
        stderr.starts_with("Method not found: teleport\n"),
        "{stderr}"
    );
}

#[test]
fn unknown_option_prints_the_options_of_the_command() {
    let (exit, stdout, stderr) = run_ub(&["ping", "-z"]);
    assert_eq!(exit, 1);
    assert_eq!(stdout, "");
    assert_eq!(
        stderr,
        "Usage of UartbusPing:\n\n\t-h, --host\t\tRpc server IP\n\t-p, --port\t\tRpc port\n\t-f, --from\t\tUartbus from address\n\t-t, --to\t\tUartbus to address\n\t-i, --interval\t\tPing repeat delay\n\t-c, --c\t\tNumber of packets to send. (default: 0 aka no \"limit\")\n\n"
    );
}

#[test]
fn version_is_printed() {
    let (exit, stdout, _) = run_ub(&["version"]);
    assert_eq!(exit, 0);
    assert_eq!(
        stdout,
        format!("Uartbus Cli version: \n{}\n", env!("CARGO_PKG_VERSION"))
    );
}

#[test]
fn command_fails_when_there_is_no_server() {
    let port = free_port().to_string();
    let (exit, _, stderr) = run_ub(&["ping", "-p", &port, "-t", "4", "-c", "1"]);
    assert_eq!(exit, 1);
    assert!(stderr.contains("Connection refused"), "{stderr}");
}

#[test]
fn ihex_validate_tells_whether_the_file_is_intact() {
    let dir = TempDir::new("ihex_validate");
    let good = dir.path().join("good.hex");
    let bad = dir.path().join("bad.hex");
    write_hex(&good, 0, &sample_code(20));
    //same file with a wrong checksum in the first line
    let text = fs::read_to_string(&good).unwrap();
    let first_line_end = text.find('\n').unwrap();
    let mut broken = text.clone();
    let last = if &text[first_line_end - 1..first_line_end] == "0" {
        "1"
    } else {
        "0"
    };
    broken.replace_range(first_line_end - 1..first_line_end, last);
    fs::write(&bad, broken).unwrap();

    let (exit, stdout, _) = run_ub(&["ihex", "validate", good.to_str().unwrap()]);
    assert_eq!((exit, stdout.as_str()), (0, "Valid.\n"));

    let (exit, stdout, _) = run_ub(&["ihex", "validate", bad.to_str().unwrap()]);
    assert_eq!((exit, stdout.as_str()), (1, "Not valid.\n"));

    let (exit, stdout, _) = run_ub(&["ihex", "size", good.to_str().unwrap()]);
    assert_eq!((exit, stdout.as_str()), (0, "20 bytes\n"));
}

#[test]
fn ihex_replace_interrupts_finds_the_default_handler_itself() {
    let dir = TempDir::new("ihex_replace");
    let hex = dir.path().join("host.hex");

    //reset vector, three vectors to the default handler at 0x7c, one to an own handler, then code
    let jmp = |address: u16| [0x0c, 0x94, (address / 2) as u8, ((address / 2) >> 8) as u8];
    let mut code = Vec::new();
    for address in [0x68, 0x7c, 0x7c, 0x200, 0x7c] {
        code.extend_from_slice(&jmp(address));
    }
    code.extend_from_slice(&[0x11, 0x24, 0x1f, 0xbe]);
    write_hex(&hex, 0, &code);

    //8224 = 0x2020: the vector table of the application
    let (exit, stdout, stderr) = run_ub(&[
        "ihex",
        "replace_interrupts",
        "-i",
        hex.to_str().unwrap(),
        "-p",
        "8224",
    ]);
    assert_eq!(exit, 0, "{stderr}");
    assert_eq!(stdout, "Rewriting: 124\n");

    //the file is rewritten in place
    let rewritten = IntelHexFile::load_file(&hex).unwrap().get_code().unwrap();
    let data = rewritten[0].data();
    assert_eq!(data[0..4], jmp(0x68));
    assert_eq!(data[4..8], jmp(0x2020 + 4));
    assert_eq!(data[8..12], jmp(0x2020 + 8));
    assert_eq!(data[12..16], jmp(0x200));
    assert_eq!(data[16..20], jmp(0x2020 + 16));
    assert_eq!(data[20..24], [0x11, 0x24, 0x1f, 0xbe]);
}

#[test]
fn ihex_replace_interrupts_checks_its_arguments() {
    let (exit, _, stderr) = run_ub(&["ihex", "replace_interrupts", "-i", "/nonexistent.hex"]);
    assert_eq!(exit, 2);
    assert!(
        stderr.contains("Ihex file `/nonexistent.hex` not exist!"),
        "{stderr}"
    );
    assert!(
        stderr.contains("Program relay offset not specified"),
        "{stderr}"
    );

    let (exit, _, stderr) = run_ub(&["ihex"]);
    assert_eq!(exit, 1);
    assert!(stderr.contains("replace_interrupts"), "{stderr}");
}
