package eu.javaexperience.electronic.uartbus.cli.apps;

import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.FROM;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.RPC_HOST;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.RPC_PORT;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.TO;

import java.util.List;
import java.util.Map;

import eu.javaexperience.cli.CliEntry;
import eu.javaexperience.cli.CliTools;
import eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools;
import eu.javaexperience.electronic.uartbus.rpc.client.UartBus;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UartBusDevice;
import eu.javaexperience.log.JavaExperienceLoggingFacility;

public class UartbusReboot
{
	public static final CliEntry<Boolean> SOFT = CliEntry.createFirstArgParserEntry
	(
		(e)->true,
		"Soft reset",
		"s", "-soft-reset"
	);
	
	protected static final CliEntry[] PROG_CLI_ENTRIES =
	{
		RPC_HOST,
		RPC_PORT,
		FROM,
		TO,
		SOFT
	};
	
	public static void main(String[] args) throws InterruptedException, Throwable
	{
		JavaExperienceLoggingFacility.addStdOut();
		Map<String, List<String>> pa = CliTools.parseCliOpts(args);
		String un = CliTools.getFirstUnknownParam(pa, PROG_CLI_ENTRIES);
		if(null != un)
		{
			CliTools.printHelpAndExit("UartbusReboot", 1, PROG_CLI_ENTRIES);
		}
		
		int to = TO.tryParseOrDefault(pa, -1);
		
		UartBus bus = UartbusCliTools.cliBusConnect(pa);
		
		UartBusDevice dev = bus.device(to);
		
		if(SOFT.hasOption(pa))
		{
			dev.getRpcRoot().getBootloaderFunctions().getPowerFunctions().softwareReset();
		}
		else
		{
			dev.getRpcRoot().getBootloaderFunctions().getPowerFunctions().hardwareReset();
		}
		
		System.exit(0);
	}
}
