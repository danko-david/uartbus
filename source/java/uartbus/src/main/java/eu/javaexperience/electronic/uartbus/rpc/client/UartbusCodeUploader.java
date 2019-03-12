package eu.javaexperience.electronic.uartbus.rpc.client;

import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.FROM;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.RPC_HOST;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.RPC_PORT;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.TO;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import eu.javaexperience.cli.CliEntry;
import eu.javaexperience.cli.CliTools;
import eu.javaexperience.log.JavaExperienceLoggingFacility;
import eu.javaexperience.log.Loggable;
import eu.javaexperience.log.Logger;

public class UartbusCodeUploader
{
	protected static final Logger LOG = JavaExperienceLoggingFacility.getLogger(new Loggable("UartbusCodeUploader"));

	public static final CliEntry<String> CODE = CliEntry.createFirstArgParserEntry
	(
		(e)->e,
		"Code to upload in Intel Hex format",
		"c", "-code"
	);
	
	protected static final CliEntry[] PROG_CLI_ENTRIES =
	{
		RPC_HOST,
		RPC_PORT,
		FROM,
		TO,
		CODE
	};
	
	public static void printHelpAndExit(int exit)
	{
		System.err.println("Usage of UartbusCodeUploader:\n");
		System.err.println(CliTools.renderListAllOption(PROG_CLI_ENTRIES));
		System.exit(1);
	}
	
	protected static UartBus connect(String... args) throws IOException
	{
		Map<String, List<String>> pa = CliTools.parseCliOpts(args);
		return UartBus.fromTcp
		(
			RPC_HOST.tryParseOrDefault(pa, "127.0.0.1"),
			RPC_PORT.tryParseOrDefault(pa, 2112),
			FROM.tryParseOrDefault(pa, 64)
		);
	}
	
	
	public static void main(String[] args)
	{
		
		
		
		//load intel hex file, verify the content

		//connect to the UART bus
		
		//check destination is alive
		
		//enter progamming mode
		
		//get the start address
		
		//fail if it's different from the IHEX start address
		
		//send code piece
		//check the next address pointer, discard and report fail on error.
		
		//commit
		//verify code space (if verification is not disabled)
		
		//restart the target.
		//as it's appeared, clear the recovery mode and start app.
		
		
		//TODO later: update app variables before commit
		
	}
}
