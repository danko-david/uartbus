# Open work

Known but not done. Remove an item in the change that solves it.

## Rust port of `ub` (`source/rust/uartbus`)

- Not run on real hardware yet: only against a simulated device, the Linux build of the host
  program, and a pty in the place of the serial gateway.
- Not built for ARM (or any target other than x86_64) yet; no cross toolchain was at hand.
- `logSql` is tested with a stand-in for the database client only, not against a real sqlite or
  mysql database (`sqlite3` was not installed where it was written).
- `scripts/ubb ub` and `deploy_node` still run the Java jar; switching them waits for the first
  item.

## Found, not fixed

- `scripts/ubci ci_run_phase` does `cd ../..` from `scripts/`, which is the **parent of the
  repository**, so it would run every `CI_hooks_*.sh` of the neighbouring repositories too.
- `source/uc/bootloader/compile_and_upload.sh` contains a leftover merge marker line (`=======`).
- `source/uc/utils/apps/ub/CI_hooks_app_test.sh` calls `scripts/compile_ub_app.sh`, which was
  merged into `scripts/ubb compile_ub_app`.
- `scripts/ubci get_c_deps` clones the dependencies into `WD/`, while the sources include them
  from the `deps/` submodules.
- `scripts/ubb export_arduino_lib` creates `arduino_ub_lib` in the repository root but copies
  into `WD/arduino_ub_lib`.
- `README.md` links to `./doc/index.md`; the index is `doc/mdsite/documentation/index.md`.
- `source/uc/bootloader/linux_compile.sh` does not compile with GCC 14: implicit function
  declarations are errors now (it builds with `-Wno-error=implicit-function-declaration
  -Wno-error=int-conversion -Wno-error=incompatible-pointer-types`).
- Java `UartbusTools.unpackValue` decodes a negative one byte value wrong when it is not at index
  0 (`-1` at index 1 gives `-65`), so a negative *from* address is misread by the Java tools.
- Java `PacketReader.readString` does not step over the terminating 0, so nothing can be read
  after a string (a `String[]` return value never finishes).
- Java `UartbusRpcEndpoint.sendPacket` compares a queue with a session (`sess != csess`, always
  true): with a gateway every client gets its own sent packets back. The Rust server does the same.
- Java `openIpEndpoint(..., reconnect=false)` ignores the connection initializer, so `-x` does
  nothing in `console`, `logFile`, `logSql`, `attachProcess`.
- Java `upload` exits with 0 when the upload failed.
