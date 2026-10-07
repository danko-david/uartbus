# UARTBus: rules for agents

Read this before changing anything. It adds to the global working policy and wins where the two
disagree. `README.md` explains what the project is; the long-form documentation is under
`doc/mdsite/documentation/` (start with `index.md`, the protocol is in `osi/`).

## What this repository is

One repository, several independent parts that all speak the same wire protocol:

| Path | What | Language / toolchain |
| --- | --- | --- |
| `source/uc/bus/lib/common/` | `ub.c`/`ub.h`: the bus core (OSI 1-2: framing, collision, retransmit) | C, device agnostic |
| `source/uc/bus/lib/addressing/` | `addr16`: variable length address encoding | C |
| `source/uc/utils/lib/rpc/` | small RPC library with namespace dispatch | C |
| `source/uc/bootloader/` | the "host program" (called bootloader for historical reasons): standard RPC namespaces and application upload. `ub_atmega.c` and `ub_linux.c` are the two platform backends | C, `avr-g++` / `gcc` |
| `source/uc/utils/ub_app/` | `ub_app_wrapper`: what an uploadable application is linked against | C, `avr-gcc` |
| `source/uc/utils/apps/` | bus gateways (ATmega2560/328 through Arduino, and Linux) and a sample app | C/C++ |
| `source/uc/test/` | unit tests of the bus core and the host program, run on the PC | C, lazyctest |
| `source/java/uartbus/` | the `ub` CLI, JSON RPC server and the device object model | Java 8 source level, Maven |
| `source/rust/uartbus/` | `ub`: Rust port of the whole Java CLI in one small executable, see its `README.md` | Rust 2021, Cargo |
| `electronic/kicad/` | bus driver and prototype board schematics | KiCad |
| `doc/`, `resources/` | website sources (mdsite), doxygen config, images, thesis | Markdown |
| `deps/` | git submodules `toe` (C utilities) and `lazyctest` (C test runner) | |
| `WD/` | working directory for build output (`uartbus.jar`, generated site). Ignored | |

## Commands

The helper scripts are `scripts/ubb` (build and deploy) and `scripts/ubci` (CI and site). Both
are RH helpers: `./scripts/ubb help` lists the commands. Add a new capability as a function in
one of them, above the `RH helper functions` block.

- `git submodule update --init`: needed once, `deps/` is empty after a plain clone.
- `./scripts/ubci deb_install_deps`: install the toolchains (uses `sudo apt-get`; ask first).
- `./scripts/ubb assemble_uartbus`: build the Java CLI into `WD/uartbus.jar`.
- `./scripts/ubb ub <args>`: run the Java CLI (builds it first if missing).
- `./scripts/ubb assemble_ub_rust`, `./scripts/ubb ub_rust <args>`: build and run the Rust port of the CLI (`WD/ub_rust`).
- `./scripts/ubb compile_ub_app $mcu $clock_hz $name $source`: compile an uploadable application.
- `./scripts/ubb deploy_node $name`: compile `$name.cpp` using `$name.ub` and upload it over the bus.

CI works through hook files: every `CI_hooks_*.sh` in the tree takes a phase as `$1`
(`clean`, `prepare_build`, `build`, `test`, `deploy`, `install`) and ignores phases it does not
know. A new buildable or testable part gets its own `CI_hooks_<name>.sh` next to its sources;
it is found by `find`, there is no list to register it in.

### Running the tests

There is no single `run_tests` command yet. Until there is one:

| Part | Command | Needs |
| --- | --- | --- |
| Rust | `./scripts/ubci rust_run_tests` | crates downloaded once |
| Rust with Java | `./scripts/ubci rust_run_java_interop_tests` | Maven, Java 8 |
| Java | `source/java/CI_hooks_uartbus.sh test` | Maven, **Java 8**, the `maven.javaexperience.eu` repository on first run |
| C | `./CI_hooks_root.sh prepare_build` once (builds `lct` into `deps/lazyctest/WD/bin`), then `source/uc/test/CI_hooks_tests.sh build` and `... test` | `gcc`, initialised submodules |

None of these need hardware: the C tests drive a simulated bus (`struct testbus`), the Java tests
use `VirtualUartBus`, the Rust tests an in-process bus and a simulated device
(`source/rust/uartbus/tests/common/device.rs`). State on this machine, 2026-10-07:

- Rust: all 169 tests pass, and the 3 Java interoperability tests.
- Java: needs Java 8 (`/usr/lib/jvm/java-8-openjdk-amd64`). With the default JDK 17 the CLI does
  not start (`Method not found: rpcServer`) and 18 of the 42 tests fail; that is the reflection
  of the `javaexperience` libraries, not something to fix in a change about something else.
- C: not run, the submodules are not initialised here.

Do not run `./scripts/ubci ci_run_phase` or `ci_all` for now, see `TODO.md`.

## Rules specific to this project

- **Never flash or touch hardware on your own.** The `*_and_upload.sh` scripts and `deploy_node`
  call `avrdude` or upload over the bus. Compile only: `UBH_COMPILE_ONLY=true` for the host
  program, `-1` as the ttyUSB number for the gateway scripts. Uploading needs my explicit request.
- **The wire format is a contract** between the C code, the Java code, the Rust port and devices
  already deployed: packet layout (`svint` destination, `svint` source, payload, `crc8`), the
  variable length integer encoding, the escaped stream framing of the gateway, and the RPC
  namespace numbers (`RPC_NS_FUNCTIONS` in `ub_bootloader.c`; 0 is the response namespace).
  Changing any of it is a breaking change: ask first, then change every implementation and its
  tests together.
- **The flash layout constants are repeated by hand** in several scripts and sources:
  host table `0x1fe0`, application start `0x2000`, application `.text` `0x2020` (also as decimal
  `8224`), plus the stack/data/bss addresses of the host program. If one changes, grep for all of
  them. Nothing detects a collision of stack, data and bss; read the comment in
  `source/uc/bootloader/compile_and_upload.sh` before touching those.
- **The microcontroller target is an ATmega328p**: 2 KB RAM, the host program has no heap. In
  `source/uc` no `malloc`, no floating point, no new library dependency, and check the `size`
  output after a change. `ub.c` must stay free of anything AVR or Linux specific; platform code
  belongs to the backends.
- **The Rust port follows the Java implementation.** Behaviour that differs from Java is a bug
  in the port, except what is listed under "Differences from the Java tool" in
  `source/rust/uartbus/README.md`. A new deliberate difference goes into that list in the same
  change. The two must keep working with each other: run `rust_run_java_interop_tests` after
  touching the RPC protocol or the upload on either side.
- **The Rust port has to stay small**: it exists because the JVM is too heavy for embedded
  machines. No new crate without a reason I accepted; external needs (serial, databases) go
  through a child process, like `socat`.
- **Java dependencies** `javaexperience:core`, `rpc`, `linux` are my own libraries from
  `maven.javaexperience.eu`. Their sources are not here; do not guess their API, look into the
  jar in `~/.m2` or ask.
- **Generated files stay out of git**: `*.hex`, `*.o`, `*.asm`, `*.sizes`, `target/`, `WD/`.
  `doc/mdsite/documentation/graphs/features/features.svg` is rendered from `features.dot` and is
  committed; edit the `.dot` and rerun `render.sh`.
- **KiCad files and the documents in `resources/` are edited by hand in their tools.** Do not
  rewrite them.

## Code style

Match the file you are in.

- C and Java: tabs, opening brace on its own line, `snake_case` in C, existing naming in Java
  (including lower case type classes such as `uint8_t`).
- Rust: `rustfmt` defaults.
- `scripts/ubb` is indented with two spaces, `scripts/ubci` and the hook files with tabs.
- Comments and identifiers are in English.

## Open work

Known problems and things not done yet are in `TODO.md`. Add what you find there (reported, not
silently fixed) and remove an item in the change that solves it.

## Proposals (pending review, not rules yet)

Where this repository differs from the global policy. The repository is right until decided.

- **Versions.** The versions up to `1.7.2` are the ones of `source/java/uartbus/pom.xml` in the
  git history (`1.0`, `1.3`, `1.4` written as `1.0.0`, `1.3.0`, `1.4.0`); none of them is a git
  tag. `1.8.0` is my choice for the work after `1.7.2`, and the pom still says `1.7.2`.
  Proposal: confirm `1.8.0`, and decide whether the pom (and the Rust crate, now `0.2.0`) follow
  the changelog version from here on.
- **Helper script.** The global policy expects one helper named after the project in the root;
  here there are two under `scripts/`. Proposal: keep `ubb` and `ubci`, add a `run_tests`
  function to `ubci` that runs the C, Java and Rust tests, and fix `ci_run_phase`.
- **Which `ub`.** `scripts/ubb ub` and `deploy_node` still run the Java jar. Proposal: once the
  Rust port has run on real hardware, make `ub` run it and keep the Java one as `ub_java`.
