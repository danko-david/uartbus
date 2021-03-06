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
import eu.javaexperience.electronic.uartbus.rpc.client.UartbusStreamerEndpoint;
import eu.javaexperience.log.JavaExperienceLoggingFacility;

public class UartbusPing
{
	protected static final CliEntry<Integer> INTERVAL = CliEntry.createFirstArgParserEntry
	(
		(e)->Integer.parseInt(e),
		"Ping repeat delay",
		"i", "-interval"
	);
	
	protected static final CliEntry<Integer> COUNT_OF_PACKETS = CliEntry.createFirstArgParserEntry
	(
		(e)->Integer.parseInt(e),
		"Number of packets to send. (default: 0 aka no \"limit\")",
		"c", "-c"
	);
	
	protected static final CliEntry[] PROG_CLI_ENTRIES =
	{
		RPC_HOST,
		RPC_PORT,
		FROM,
		TO,
		INTERVAL,
		COUNT_OF_PACKETS
	};
	
	public static void main(String[] args) throws InterruptedException, Throwable
	{
		JavaExperienceLoggingFacility.addStdOut();
		Map<String, List<String>> pa = CliTools.parseCliOpts(args);
		String un = CliTools.getFirstUnknownParam(pa, PROG_CLI_ENTRIES);
		if(null != un)
		{
			CliTools.printHelpAndExit("UartbusPing", 1, PROG_CLI_ENTRIES);
		}
		
		int from = UartbusCliTools.parseFrom(pa);
		int to = TO.tryParseOrDefault(pa, -1);
		int interval = INTERVAL.tryParseOrDefault(pa, 1000);
		int count = COUNT_OF_PACKETS.tryParseOrDefault(pa, 0);
		
		UartbusStreamerEndpoint rpc = UartbusRpcClientTools.openIpEndpoint
		(
			RPC_HOST.tryParseOrDefault(pa, "127.0.0.1"),
			RPC_PORT.tryParseOrDefault(pa, 2112),
			null,
			false
		);
		
		rpc.getPacketStreamer().addEventListener(UartbusTools::printPacketStdout);
		
		rpc.startStreaming();
		
		UartbusConnection conn = rpc.getApi();
		
		byte[] packet = null;
		{
			PacketAssembler asm = new PacketAssembler();
			asm.writeAddressing(from, to);
			asm.write(new byte[]{1,0});
			asm.appendCrc8();
			packet = asm.done();
		}
		
		for(int i=0;i<count || count <= 0;++i)
		{
			conn.sendPacket(packet);
			System.out.println("ping "+from+" > "+to);
			Thread.sleep(interval);
		}
	}
}