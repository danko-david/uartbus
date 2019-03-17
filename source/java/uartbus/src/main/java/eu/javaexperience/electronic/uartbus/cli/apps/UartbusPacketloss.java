package eu.javaexperience.electronic.uartbus.cli.apps;

import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.FROM;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.RPC_HOST;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.RPC_PORT;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.TO;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import eu.javaexperience.cli.CliEntry;
import eu.javaexperience.cli.CliTools;
import eu.javaexperience.datastorage.TransactionException;
import eu.javaexperience.electronic.uartbus.rpc.client.UartBus;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UartBusDevice;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbDevStdNsRoot;
import eu.javaexperience.log.JavaExperienceLoggingFacility;
import eu.javaexperience.text.Format;

public class UartbusPacketloss
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
	
	protected static final CliEntry<Integer> TIMEOUT = CliEntry.createFirstArgParserEntry
	(
		(e)->Integer.parseInt(e),
		"Packet response timeout in milisec",
		"l", "-loss-time"
	);
	
	protected static final CliEntry[] PROG_CLI_ENTRIES =
	{
		RPC_HOST,
		RPC_PORT,
		FROM,
		TO,
		INTERVAL,
		COUNT_OF_PACKETS,
		TIMEOUT
		
	};
	
	public static void printHelpAndExit(int exit)
	{
		System.err.println("Usage of UartbusPacketloss:\n");
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
		
		int to = TO.tryParseOrDefault(pa, -1);
		int interval = INTERVAL.tryParseOrDefault(pa, 1000);
		int count = COUNT_OF_PACKETS.tryParseOrDefault(pa, 0);
		int timeout = TIMEOUT.tryParseOrDefault(pa, 3000);
		
		UartBus bus = UartBus.fromTcp
		(
			RPC_HOST.tryParseOrDefault(pa, "127.0.0.1"),
			RPC_PORT.tryParseOrDefault(pa, 2112),
			FROM.tryParseOrDefault(pa, -1)
		);
		UartBusDevice dev = bus.device(to);
		dev.setTimeout(timeout, TimeUnit.MILLISECONDS);
		UbDevStdNsRoot root = dev.getRpcRoot();

		int loss = 0;
		int pass = 0;
		
		for(int i=0;i<count || count <= 0;++i)
		{
			//on ^C
			if(Thread.interrupted())
			{
				break;
			}
			
			try
			{
				root.getBusFunctions().ping();
				++pass;
				System.out.println(i+". pong");
				if(interval > 0)
				{
					Thread.sleep(interval);
				}
			}
			catch(TransactionException e)
			{
				System.out.println(i+". -- loss --");
				++loss;
			}
			catch(Exception e)
			{
				break;
			}
		}
		
		if(0 != pass && 0 != loss)
		{
			System.out.println("Statistic:");
			System.out.println("\tSent packets: "+(pass+loss));
			System.out.println("\tReceived packets: "+pass);
			System.out.println("\tLost packets: "+loss);
			System.out.println("\tPacket loss ratio: "+Format.formatDouble(((100.0*loss)/ (loss+pass)))+" %");
		}
		
		
		System.exit(0);
	}
}
