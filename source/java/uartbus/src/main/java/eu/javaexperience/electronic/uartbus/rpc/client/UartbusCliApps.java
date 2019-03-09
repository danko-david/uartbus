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
import eu.javaexperience.electronic.uartbus.PacketAssembler;
import eu.javaexperience.electronic.uartbus.UartbusTools;
import eu.javaexperience.electronic.uartbus.rpc.UartbusConnection;
import eu.javaexperience.log.JavaExperienceLoggingFacility;
import eu.javaexperience.log.Loggable;
import eu.javaexperience.log.Logger;

public class UartbusCliApps
{
	public static class UartbusPing
	{
		protected static final Logger LOG = JavaExperienceLoggingFacility.getLogger(new Loggable("UartbusConsole"));
		
		protected static final CliEntry<Integer> INTERVAL = CliEntry.createFirstArgParserEntry
		(
			(e)->Integer.parseInt(e),
			"Ping repeat delay",
			"i", "-interval"
		);
		
		/*
		protected static final CliEntry<Boolean> EXIT = CliEntry.createFirstArgParserEntry
		(
			(e)->true,
			"Exit after packet sent",
			"e", "-exit"
		);*/
		
		protected static final CliEntry[] PROG_CLI_ENTRIES =
		{
			RPC_HOST,
			RPC_PORT,
			FROM,
			TO,
			INTERVAL
		};
		
		public static void printHelpAndExit(int exit)
		{
			System.err.println("Usage of UartbusPing:\n");
			System.err.println(CliTools.renderListAllOption(PROG_CLI_ENTRIES));
			System.exit(1);
		}
		
		public static void main(String[] args) throws InterruptedException, Throwable
		{
			JavaExperienceLoggingFacility.addStdOut();
			Map<String, List<String>> pa = CliTools.parseCliOpts(args);
			String un = CliTools.getFirstUnknownParam(pa, PROG_CLI_ENTRIES);
			if(null != un)
			{
				printHelpAndExit(1);
			}
			
			int from = FROM.tryParseOrDefault(pa, -1);
			int to = TO.tryParseOrDefault(pa, -1);
			int interval = INTERVAL.tryParseOrDefault(pa, 1000);
			
			UartbusRpcClientTools.streamPackets
			(
				RPC_HOST.tryParseOrDefault(pa, "127.0.0.1"),
				RPC_PORT.tryParseOrDefault(pa, 2112),
				(e) ->
				{
					boolean valid = UartbusTools.crc8(e, e.length-1) == e[e.length-1];
					System.out.println((valid?"":"!")+UartbusTools.formatColonData(e));
				}
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
				asm.write(new byte[]{1,0});
				asm.appendCrc8();
				packet = asm.done();
			}
			while(true)
			{
				conn.sendPacket(packet);
				System.out.println("ping "+from+" > "+to);
				Thread.sleep(interval);
			}
			
		}
	}
}