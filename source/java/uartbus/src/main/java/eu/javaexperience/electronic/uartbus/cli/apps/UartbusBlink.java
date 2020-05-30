package eu.javaexperience.electronic.uartbus.cli.apps;

import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.FROM;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.RPC_HOST;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.RPC_PORT;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.TO;

import java.util.List;
import java.util.Map;

import eu.javaexperience.cli.CliEntry;
import eu.javaexperience.cli.CliTools;
import eu.javaexperience.electronic.uartbus.PacketAssembler;
import eu.javaexperience.electronic.uartbus.UartbusTools;
import eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools;
import eu.javaexperience.electronic.uartbus.rpc.UartbusConnection;
import eu.javaexperience.electronic.uartbus.rpc.client.UartbusRpcClientTools;
import eu.javaexperience.log.JavaExperienceLoggingFacility;

public class UartbusBlink
{
	protected static final CliEntry<Integer> INTERVAL = CliEntry.createFirstArgParserEntry
	(
		(e)->Integer.parseInt(e),
		"Blink interval",
		"i", "-interval"
	);
	
	protected static final CliEntry[] PROG_CLI_ENTRIES =
	{
		RPC_HOST,
		RPC_PORT,
		FROM,
		TO,
		INTERVAL
	};
	
	public static void main(String[] args) throws InterruptedException, Throwable
	{
		JavaExperienceLoggingFacility.addStdOut();
		Map<String, List<String>> pa = CliTools.parseCliOpts(args);
		String un = CliTools.getFirstUnknownParam(pa, PROG_CLI_ENTRIES);
		if(null != un)
		{
			CliTools.printHelpAndExit("UartbusBlink", 1, PROG_CLI_ENTRIES);
		}
		
		int from = UartbusCliTools.parseFrom(pa);
		int to = TO.tryParseOrDefault(pa, -1);
		int interval = INTERVAL.tryParseOrDefault(pa, 500);
		
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
		
		byte[] packet = null;
		{
			PacketAssembler asm = new PacketAssembler();
			asm.writeAddressing(from, to);
			asm.write(new byte[]{1, 2, 2});
			asm.appendCrc8();
			packet = asm.done();
		}
		
		while(true)
		{
			conn.sendPacket(packet);
			System.out.println("blink "+from+" > "+to);
			Thread.sleep(interval);
		}
	}
}
