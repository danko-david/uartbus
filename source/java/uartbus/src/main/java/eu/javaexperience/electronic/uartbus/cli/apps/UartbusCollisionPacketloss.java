package eu.javaexperience.electronic.uartbus.cli.apps;

import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.FROM;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.RPC_HOST;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.RPC_PORT;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.TO;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import eu.javaexperience.arrays.ArrayTools;
import eu.javaexperience.cli.CliEntry;
import eu.javaexperience.cli.CliTools;
import eu.javaexperience.collection.map.MapTools;
import eu.javaexperience.datastorage.TransactionException;
import eu.javaexperience.electronic.uartbus.PacketAssembler;
import eu.javaexperience.electronic.uartbus.UartbusTools;
import eu.javaexperience.electronic.uartbus.rpc.UartbusConnection;
import eu.javaexperience.electronic.uartbus.rpc.client.ParsedUartBusPacket;
import eu.javaexperience.electronic.uartbus.rpc.client.UartBus;
import eu.javaexperience.electronic.uartbus.rpc.client.UartbusRpcClientTools;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UartBusDevice;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbDevStdNsRoot;
import eu.javaexperience.log.JavaExperienceLoggingFacility;
import eu.javaexperience.text.Format;

public class UartbusCollisionPacketloss
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
		INTERVAL,
		COUNT_OF_PACKETS
	};
	
	public static void printHelpAndExit(int exit)
	{
		System.err.println("Usage of UartbusCollisionPacketloss:\n");
		System.err.println(CliTools.renderListAllOption(PROG_CLI_ENTRIES));
		System.exit(1);
	}
	
	protected static void printStatistic(int device, int sent, int received)
	{
		if(0 != sent)
		{
			int loss = sent - received;
			
			System.out.print("Statistic for bus device "+device+": ");
			System.out.print("\tSent packets: "+sent);
			System.out.print("\tReceived packets: "+received);
			System.out.print("\tLost packets: "+loss);
			System.out.println("\tPacket loss ratio: "+Format.formatDouble(((100.0*loss)/ (sent)))+" %");
		}
	}
	
	public static void regPacketLostStatPrintOnExit(int[] sent, Map<Integer, Integer> rec)
	{
		Runtime.getRuntime().addShutdownHook(new Thread()
		{
			@Override
			public void run()
			{
				if(0 == sent[0])
				{
					System.out.println("No broadcast packet sent.");
				}
				else if(rec.isEmpty())
				{
					System.out.println("No broadcast response packet received.");
				}
				else
				{
					for(Entry<Integer, Integer> ent:rec.entrySet())
					{
						printStatistic(ent.getKey(), sent[0], ent.getValue());
					}
				}
			}
		});
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
		int interval = INTERVAL.tryParseOrDefault(pa, 1000);
		int count = COUNT_OF_PACKETS.tryParseOrDefault(pa, 0);
		
		Map<Integer, Integer> stat = new TreeMap<>(); 
		
		UartbusRpcClientTools.streamPackets
		(
			RPC_HOST.tryParseOrDefault(pa, "127.0.0.1"),
			RPC_PORT.tryParseOrDefault(pa, 2112),
			(e) ->
			{
				try
				{
					if(e.length > 1 && UartbusTools.crc8(e, e.length-1) == e[e.length-1])
					{
						ParsedUartBusPacket packet = new ParsedUartBusPacket(e, true);
						if(packet.to == from)
						{
							MapTools.incrementCount(stat, packet.from);
						}
					}
				}
				catch(Exception ex)
				{
					ex.printStackTrace();
				}
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
			asm.writeAddressing(from, 0);
			asm.write(new byte[]{1,0});
			asm.appendCrc8();
			packet = asm.done();
		}
		
		int[] sent = new int[1];
		
		regPacketLostStatPrintOnExit(sent, stat);
		
		for(int i=0;i<count || count <= 0;++i)
		{
			conn.sendPacket(packet);
			++sent[0];
			System.out.println(i+". broadcast ping ");
			Thread.sleep(interval);
		}
		
		System.out.println("Last turn, waiting a bit for the last packets.");
		Thread.sleep(2000);
		
		System.exit(0);
	}
}
