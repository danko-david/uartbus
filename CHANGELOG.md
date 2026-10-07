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
