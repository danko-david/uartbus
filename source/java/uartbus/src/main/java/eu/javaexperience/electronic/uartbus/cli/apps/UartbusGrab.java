package eu.javaexperience.electronic.uartbus.cli.apps;

import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.FROM;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.RPC_HOST;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.RPC_PORT;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.TO;

import java.util.List;
import java.util.Map;

import eu.javaexperience.cli.CliEntry;
import eu.javaexperience.cli.CliTools;
import eu.javaexperience.electronic.uartbus.UartbusTools;
import eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools;
import eu.javaexperience.electronic.uartbus.rpc.UartbusConnection;
import eu.javaexperience.electronic.uartbus.rpc.client.UartBus;
import eu.javaexperience.electronic.uartbus.rpc.client.UartbusRpcClientTools;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UartBusDevice;
import eu.javaexperience.log.JavaExperienceLoggingFacility;

public class UartbusGrab
{
	protected static final CliEntry[] PROG_CLI_ENTRIES =
	{
		RPC_HOST,
		RPC_PORT,
		FROM,
		TO
	};
	
	public static void main(String[] args) throws InterruptedException, Throwable
	{
		JavaExperienceLoggingFacility.addStdOut();
		Map<String, List<String>> pa = CliTools.parseCliOpts(args);
		String un = CliTools.getFirstUnknownParam(pa, PROG_CLI_ENTRIES);
		if(null != un)
		{
			CliTools.printHelpAndExit("UartbusGrab", 1, PROG_CLI_ENTRIES);
		}
		
		int from = UartbusCliTools.parseFrom(pa);
		int to = TO.tryParseOrDefault(pa, -1);
		
		UartbusRpcClientTools.streamPackets
		(
			RPC_HOST.tryParseOrDefault(pa, "127.0.0.1"),
			RPC_PORT.tryParseOrDefault(pa, 2112),
			UartbusTools::printPacketStdout
		);
		
		UartbusConnection conn = UartbusRpcClientTools.connectTcp
		(
			RPC_HOST.tryParseOrDefault(pa, "127.0.0.1"),
			RPC_PORT.tryParseOrDefault(pa, 2112)
		);
		
		UartBus bus = UartbusCliTools.cliBusConnect(pa);
		
		UartBusDevice dev = bus.device(to);
		UartbusCodeUploader.restartGrabDevice(dev);
		System.exit(0);
	}
}
