# ub (Rust)

Rust port of the Java `ub` command (`source/java/uartbus`): one small executable with every
subcommand, for machines where a JVM is too heavy. It speaks the same RPC protocol, so Rust
and Java tools can be mixed freely (a Java client with the Rust server and the reverse).

|   | Rust | Java |
| --- | --- | --- |
| executable | ~0.85 MB, needs only libc | 9.4 MB jar + JVM (Java 8) |
| idle `rpcServer` memory (x86_64) | ~2.5 MB, 1 thread | ~50 MB, 40 threads |

## Build and run

	./scripts/ubb assemble_ub_rust      # builds WD/ub_rust
	./scripts/ubb ub_rust <command>     # runs it (builds first if missing)

or directly: `cargo build --release` here, the executable is `target/release/ub`.

## Commands

Names and options are the ones of the Java tool; `ub <command> -?` lists the options.

| Command | What it does |
| --- | --- |
| `rpcServer` | connects the bus gateway (`-s` device, `-b` baud; `-m dummy` for no gateway) and serves the bus on TCP (`-p`, default 2112) to the other commands |
| `console` | shows the packets of the bus, sends the ones typed in (`-d 1:2:1 -e` sends one and exits) |
| `ping`, `blink` | send ping / led toggle requests periodically |
| `packetloss` | pings a device and counts the lost answers |
| `collisionPacketloss` | broadcasts and counts the answers per device, to measure collision handling |
| `upload` | uploads an Intel HEX application into a device (`-c` file, `-t` device, `-n` no verify) |
| `grab`, `reboot` | restart a device (grab: the application is not started) |
| `ihex` | `validate`, `size`, `replace_interrupts` on Intel HEX files |
| `logFile`, `logSql` | save the packets of the bus into rotated files or an SQL table |
| `attachProcess` | connects the stdin/stdout of a program to the bus (colon separated bytes per line) |
| `service ftp` | serves a directory of this machine on the bus |
| `version` | prints the version |

Environment variables:

- `UB_LOG_LEVEL`: trace, debug, info (default), notice, warning, error, fatal
- `UB_SQL_PASSWORD`: password for `logSql`, if not given with `-P`

External programs, started when the command needs them:

- `socat`: serial access of `rpcServer` (or any program given with `-i`, started as `program device baud`)
- `sqlite3` or `mysql`: the database client `logSql` feeds the statements to

## Layout

- `src/tools.rs`, `packet.rs`, `framing.rs`, `ihex.rs`: encodings (crc8, variable length integers, packets, gateway framing, Intel HEX)
- `src/connector.rs`: the gateway side of the server (child process, reconnect)
- `src/rpc/`: the TCP protocol (`server.rs`, `client.rs`), bus and device access (`bus.rs`, `device.rs`), services (`service.rs`, `ftp.rs`), in-process bus for tests (`virtual_bus.rs`)
- `src/apps/`: one file per subcommand
- `tests/`: `*_java_port.rs` are the ported Java tests, `rpc_server_client.rs` the TCP protocol, `cli_end_to_end.rs` runs the real executable against a device simulated in `tests/common/device.rs`

## Tests

	./scripts/ubci rust_run_tests                 # needs nothing external
	./scripts/ubci rust_run_java_interop_tests    # needs maven and Java 8

## Differences from the Java tool

Deliberate, everything else that differs is a bug of the port:

- `-` followed by digits is a value, not an option: `-t -1` works (Java can not take negative addresses).
- `upload` exits with 1 when the upload or the verification fails (Java exits with 0).
- `-x` (show sent packets) is really applied by `console`, `logFile`, `logSql`, `attachProcess` (Java ignores it).
- `logFile`, `logSql` exit with an error when the server goes away, `attachProcess` exits when its program ends (Java keeps running without doing anything).
- `logSql` uses the command line client of the database instead of JDBC. `date` is written as UTC text. On sqlite the indexes are really created (the statements of the Java tool fail there).
- The RPC function `close` ends the receiving of the calling client only (in Java it closes the gateway for every client). The `DiscoverRpc` namespace of the Java server is not provided.
- `service ftp`: file names can not point out of the served directory, directory listings are sorted by name, the parent of a file in the served directory is answered.
- Two decoding errors of the Java library are not reproduced: a negative one byte variable length value that is not at the start of the buffer (eg. a negative *from* address) is decoded right, and reading a string consumes its terminator.
- Log lines have `<pid>` where Java has `<pid/thread>`, and the lines about the logger itself are missing.
