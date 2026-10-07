//! `ub`: every UARTBus PC side tool in one executable, see `ub` without arguments.

use uartbus::apps::{render_command_list, COMMANDS};

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();

    let Some((name, rest)) = args.split_first() else {
        eprint!("{}", render_command_list());
        std::process::exit(1)
    };

    let Some((_, command, _)) = COMMANDS.iter().find(|(command, _, _)| command == name) else {
        eprintln!("Method not found: {name}\n");
        eprint!("{}", render_command_list());
        std::process::exit(1)
    };

    if let Err(err) = command(rest) {
        eprintln!("{err}");
        std::process::exit(1);
    }
}
