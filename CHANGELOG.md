1.8.0:
	Rust port of the `ub` command.
	- `ub` exists as one small executable without JVM (`source/rust/uartbus`, about 0.85 MB):
		every command of the Java tool with the same names and options, working together with
		the Java tools on the same RPC server. Build and run: `./scripts/ubb ub_rust`.
	- Its differences from the Java tool are listed in `source/rust/uartbus/README.md`, among
		them: negative addresses can be given (`-t -1`), `upload` exits with 1 on failure.
	- New helper commands: `ubb assemble_ub_rust`, `ubb ub_rust`, `ubci rust_run_tests`,
		`ubci rust_run_java_interop_tests`; the CI phases build and test the port too.
	- Rules for agents working on the repository: `AGENTS.md`; open work: `TODO.md`.
	- This changelog; the entries below were written afterwards from the git history, using
		the version of `source/java/uartbus/pom.xml` at each commit.
	- The separate build, compile and deploy scripts are merged into two helper scripts,
		`scripts/ubb` and `scripts/ubci`.
	- The dependencies `toe` and `lazyctest` are git submodules under `deps/`.
	- Fix: the host program and the application wrapper work when compiled with newer GCC
		versions (optimisations, indirect calls in the application space).

1.7.2:
	- Upgraded `jvx-core`, retries use its `RetryTools`.
	- Maven repositories are reached through https, tests use JUnit 4.13.1.

1.7.1:
	- Fix: loopback of sent packets in the RPC server.
	- The Maven build produces a pure library jar and an executable jar with dependencies.
	- More RPC functions on the device model.
	- Documentation extended, contribution guide, generated site improved, project logo.

1.7.0:
	- `VirtualUartBus`: a bus server inside the process without RPC binding, a connection
		source for several `UartBus` instances.
	- `UartbusServiceBridge`: Java objects with annotated methods become services that other
		nodes of the bus can call.
	- `ub console -D`: prints packets parsed (from, to, payload, crc8) instead of the colon
		separated form.
	- `ub services`: new group of commands for built in bus services; the first one is an FTP
		server on a chosen directory.
	- The RPC reflection namespace (microcontroller and Java side) is merged, with the new
		types `uint8_t` and `uint16_t`.
	- `UartbusConnection` has an `init()` function; the RPC endpoint terminates gracefully
		when it is closed.
	- Fix: unpacking of variable length signed and unsigned integers.
	- Fix: `startStreaming()` returns only after the connection is initialised, so the first
		packets are not missed.
	- Tests for the available RPC types through device models and services.

1.6.6:
	- Bus services (work in progress).

1.6.5:
	- Fix: less important error messages during code upload.
	- Proper clearing of bss and copying of data in the application wrapper.
	- Arduino source compilation has a debugging option; `ub_ardulib_upload` compiles and
		uploads in one call.

1.6.4:
	- Direct mode: the host program can be uploaded with the gateway in place of the bus, so
		`ub rpcServer` manages the microcontroller as a bus with a single node.
	- `UartBusPacket` always contains the crc8.
	- Fix: gateway compilation for both ATmega328 and ATmega2560, checked by a CI script.
	- Design mistakes of the prototype board are written down.

1.6.3:
	- `UartBus` can report the end of a raw packet.

1.6.2:
	- The `ub console` code is more flexible and reusable from other programs.

1.6.1:
	- `UartBus` has listeners for invalid and for unrelated packets.

1.6.0:
	- RPC requests can run in parallel.

1.5.2:
	- `ub` has commands to grab and to reboot a device.
	- The application compile scripts accept an alternative Arduino source library.
	- The host program has a `manage_bus` function; static constructors work in the
		application wrapper.

1.5.1:
	- The code uploader reconnects properly.
	- First UARTBus prototype board (sent for production).

1.5.0:
	- The bus gateways use static memory instead of `malloc`/`free`.
	- The clock source of the host program is configurable through environment variables.
	- `compile_ub_arduino_app.sh` takes `ARDUINO_DIR` from the environment.
	- An unrelated function is removed from the `UartbusConnection` interface.
	- Documentation: RJ11 pin assignment.

1.4.7:
	- `ub` can print its version.

1.4.6:
	- Fix: `ub rpcServer` exits when the connection breaks and no reconnection was asked.

1.4.5:
	- Dependency library versions updated.
	- Fix: argument count check of `bootloader/compile_and_upload.sh`.

1.4.4:
	- Better looking timestamps in the traffic log file.
	- Dockerfile to build the project in a container.

1.4.3:
	- New `ub` command: log the bus traffic into a file.

1.4.2:
	- RPC namespaces of a device can be walked (reflection), with the types `uint8_t`,
		`uint16_t`, `NoReturn` and the variable length integers in the RPC layer.
	- Code duplicated from the `javaexperience` libraries is replaced with the library one.
	- The website is generated with mdsite from the repository.
	- Fix: `micros()` of the Linux host program is monotonic.

1.4.1:
	- An incomplete, unused feature and unused sources are removed.
	- RPC library in C with a reflect namespace (in progress).

1.4.0:
	- `ub rpcServer` has a dummy mode: no serial connection, it relays the traffic between
		its clients.
	- New `ub` command `attachProcess`: runs a process and connects its standard input and
		output to the bus, so a node can be written as a plain process.
	- Linux port of the host program, with its compile script; its address comes from the
		environment.
	- `UartbusPacketConnector` is an interface, the implementation is
		`UartbusEscapedStreamPacketConnector`.

1.3.5:
	- `ub rpcServer` prints the exit status of the communication process.
	- Linux bus gateway: exits with a status instead of aborting, two missed deadline cases
		fixed, debugging output switched by an environment variable.
	- The host program compile script takes the baud rate as a parameter.
	- The bus specific part is separated from the host program, another bus handler can be
		put in its place.
	- Arduino library export script, the library with description and example.
	- Build and test of the whole repository through `CI_hooks_*.sh` scripts.

1.3.4:
	- The standard error of the communication process appears on the one of `ub`, with
		better error reports.

1.3.3:
	- `ub rpcServer`: improved reconnection.
	- Linux bus gateway: more serial options.

1.3.2:
	- Direct connection of the PC to the bus: a bus driver program for Linux, and
		`ub rpcServer -i` to name the executable used for the communication.

1.3.1:
	- The code uploader handles upload errors better.
	- `compile_and_upload.sh` of the host program shows help when arguments are missing.
	- Host program: the `UBH_DEBUG_BROADCAST_PACKET_WITH_BAD_CRC` compile option publishes
		the broken packets it received.
	- Application wrapper: `ub_app` and `main` are weak, they can be overridden.
	- `bus_connector` is renamed to `bus_gateway`; the common parts of the gateways are
		shared and the ATmega2560 one compiles again.

1.3.0:
	- Serial and TCP connections reconnect, with a reusable retry logic.

1.2.4:
	- Fix: calculation of the packet loss statistic.

1.2.3:
	- Fix: error handling of the RPC server and of the dispatch on the client side.

1.2.2:
	- Host program flasher: refuses to write outside the application area, reports an
		invalid flashing address, uses the proper block size.
	- Code uploader: variable block size, raised from 16 to 32 bytes for faster upload.
	- Fix: a code upload bug found by the new tests of the host program.
	- The application wrapper calls Arduino's `init()` when it is linked in.
	- The scripts find their directory when they are started through a symbolic link.
	- Documentation: more explanation, feature tree.

1.2.1:
	- Arduino compiler scripts (all libraries, or selected ones); unused code is dropped
		with `--gc-sections`.
	- Code uploader: reboots the device before the upload, checks more, logs instead of
		printing, faster upload.
	- Fix: retransmittable RPC calls are really sent again; the upload does not continue
		after `ENOTCONN`.

1.2.0:
	- The Java package is published to the Maven repository.
	- Arduino sources can be compiled to an uploadable hex.

1.1.0:
	- Breaking: RPC responses use namespace 0.
	- Interrupts of an application work: the vectors of the host program are redirected to
		the application's table with `ub ihex replace_interrupts`.
	- New `ub` commands: `blink` and logging into SQL.
	- The code upload has tuned timeouts and an option to skip the verification.
	- The RPC server reconnects on error and can loop sent packets back.
	- Bus gateway for the ATmega328 with software serial; "bus connector" is called "bus
		gateway" from now on.
	- Arduino/wiring compatible compilation for the ATmega328.
	- The clock frequency is a parameter of the host program compile script; fix of the
		microsecond calculation for clocks that are not a multiple of 1 MHz.
	- Fix: reading and fetching shared one buffer in `UartbusPacketConnector`, a source of
		packet loss.
	- Bus driver schematics, license.

1.0.2:
	- Variable length addressing in the microcontroller and the Java code.
	- Collision detection and handling on the bus, fair wait after sending, selectable baud
		rate (the test bus runs at 115200).
	- Code upload over the bus: the host program writes the flash, `ub` uploads an Intel
		hex file; requests of namespace 32 and above go to the application.
	- Application wrapper and compiler script for uploadable applications; the memory of
		the host program and of the application do not collide.
	- New `ub` commands: `ping`, `packetloss`, `collisionPacketloss`, Intel hex validation,
		the compiler; `console --log-times`; the default source address is 63.
	- Java: `UartBus` model with devices, RPC tree and automatic retransmission of safe
		calls.
	- Basic RPC functions on the device: user LED, current time.
	- The host program is C instead of C++, the device specific code is in `ub_atmega.c`.
	- Unit tests of the bus core.
	- README and documentation.

1.0.1:
	- Working `ub console`.
	- First build as an Arduino library.

1.0.0:
	- Initial version: bus core for the microcontroller, RPC server and client in Java,
		started through the `ub` command dispatcher from one jar.
