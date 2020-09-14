package eu.javaexperience.electronic.uartbus.rpc;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import eu.javaexperience.cli.CliEntry;
import eu.javaexperience.electronic.uartbus.rpc.client.UartBus;

public class UartbusCliTools
{
	private UartbusCliTools()
	{}
	
	public static final CliEntry<Integer> SERIAL_BAUD = CliEntry.createFirstArgParserEntry
	(
		(e)->Integer.parseInt(e),
		"Serial baud rate",
		"b", "-baud"
	);
	
	public static final CliEntry<String> RPC_HOST = CliEntry.createFirstArgParserEntry
	(
		(e)->e,
		"Rpc server IP",
		"h", "-host"
	);

	public static final CliEntry<Integer> RPC_PORT = CliEntry.createFirstArgParserEntry
	(
		(e)->Integer.parseInt(e),
		"Rpc port",
		"p", "-port"
	);
	
	public static final CliEntry<Integer> FROM = CliEntry.createFirstArgParserEntry
	(
		(e)->Integer.parseInt(e),
		"Uartbus from address",
		"f", "-from"
	);
	
	public static final CliEntry<Boolean> LOOPBACK = CliEntry.createFirstArgParserEntry
	(
		(e)->true,
		"Show sent packet",
		"x", "-loopback-sent"
	);
	
	public static final CliEntry<String> OPTIONS = CliEntry.createFirstArgParserEntry
	(
		(e)->e,
		"Other options",
		"o", "-options"
	);
	
	
	public static final int DEFAULT_FROM_ADDRESS = 63;
	public static int parseFrom(Map<String, List<String>> args)
	{
		return FROM.tryParseOrDefault(args, DEFAULT_FROM_ADDRESS);
	}
	
	public static final CliEntry<Integer> TO = CliEntry.createFirstArgParserEntry
	(
		(e)->Integer.parseInt(e),
		"Uartbus to address",
		"t", "-to"
	);
	
	public static final CliEntry<String> WORK_DIR = CliEntry.createFirstArgParserEntry
	(
		(e) -> e,
		"Working directory",
		"d", "-working-directory"
	);
	
	public static final CliEntry<Boolean> LOG_TIME = CliEntry.createFirstArgParserEntry
	(
		(e) -> true,
		"Log times",
		"l", "-log-times"
	);
	
	public static final CliEntry<Boolean> RECONNECT = CliEntry.createFirstArgParserEntry
	(
		(e) -> true,
		"Reconnect",
		"r", "-reconnect"
	);
	
	public static final CliEntry<String> DECODE_PACKET = CliEntry.createFirstArgParserEntry
	(
		(e)->e,
		"Decode packet",
		"D", "-decode-packet"
	);
	
	
	public static UartBus cliBusConnect(Map<String, List<String>> cliArgs) throws IOException
	{
		return UartBus.fromTcp
		(
			RPC_HOST.tryParseOrDefault(cliArgs, "127.0.0.1"),
			RPC_PORT.tryParseOrDefault(cliArgs, 2112),
			UartbusCliTools.parseFrom(cliArgs)
		);
	}
	
}
