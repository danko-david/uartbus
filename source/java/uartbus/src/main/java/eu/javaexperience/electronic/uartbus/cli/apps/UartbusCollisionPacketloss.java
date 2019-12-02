package eu.javaexperience.electronic.uartbus.cli.apps;

import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import eu.javaexperience.cli.CliEntry;
import eu.javaexperience.cli.CliTools;
import eu.javaexperience.collection.map.MapTools;
import eu.javaexperience.electronic.uartbus.PacketAssembler;
import eu.javaexperience.electronic.uartbus.UartbusTools;
import eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools;
import eu.javaexperience.electronic.uartbus.rpc.UartbusConnection;
import eu.javaexperience.electronic.uartbus.rpc.client.ParsedUartBusPacket;
import eu.javaexperience.electronic.uartbus.rpc.client.UartbusRpcClientTools;
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
	
	public static class DevCollisionStat
	{
		public Map<Integer, Integer> lens = new TreeMap<>(); 
	}
	
	public static void regPacketLostStatPrintOnExit(int[] sent, Map<Integer, ReplyCollector> rec,  DevCollisionStat ds)
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
					for(Entry<Integer, ReplyCollector> ent:rec.entrySet())
					{
						System.out.println(ent.getValue().getStatistic(sent[0]));
					}
					
					System.out.println("Collisions: "+MapTools.toStringMultiline(ds.lens));
					
					//System.out.println("Collisions: one byte: "+ds.oneByteCollision+", two: "+ds.twoByteCollision+", multi: "+ds.multiByteCollision+", concat: "+ds.concaterationCollision);
				}
			}
		});
	}
	
	public static byte[] generatePacketSequence(int from, int seq) throws IOException
	{
		PacketAssembler asm = new PacketAssembler();
		asm.writeAddressing(from, 0);
		asm.write(new byte[]{1,1});
		asm.writePackedValue(false, seq);
		asm.appendCrc8();
		return asm.done();
	}
	
	public static class ReplyCollector
	{
		public int device;
		public Map<Integer, Integer> responses = new HashMap<>();
		
		public String getStatistic(int sent)
		{
			StringBuilder sb = new StringBuilder();
			if(0 != sent)
			{
				int received = responses.size();
				int loss = sent - received;
				int duplicates = 0;
				
				if(0 == device)
				{
					Integer rec = responses.get(0);
					if(null != rec)
					{
						received = rec;
					}
					sb.append("Statistic of collisions:");
					sb.append("\tSent packets: "+sent);
					sb.append("\tCollided packets: "+received);
					sb.append("\tCollision ratio: "+Format.formatDouble(((100.0*received)/ (sent)))+" %");
				}
				else
				{
					sb.append("Statistic for bus device "+device+": ");
					sb.append("\tSent packets: "+sent);
					sb.append("\tReceived packets: "+received);
					sb.append("\tLost packets: "+loss);
					sb.append("\tPacket loss ratio: "+Format.formatDouble(((100.0*loss)/ (sent)))+" %");
					
					for(Integer v:responses.values())
					{
						if(v > 1)
						{
							duplicates += v-1;
						}
					}
					
					if(duplicates > 0)
					{
						sb.append(" Duplication ("+duplicates+") ratio: ");
						sb.append(Format.formatDouble(((100.0*duplicates)/ (sent)))+" %");
					}
				}
			}
			
			return sb.toString();
		}
	}
	
	public static ReplyCollector getOrCreateRelayCollector(Map<Integer, ReplyCollector> map, int dev)
	{
		ReplyCollector ret = map.get(dev);
		if(null == ret)
		{
			ret = new ReplyCollector();
			ret.device = dev;
			map.put(dev, ret);
		}
		return ret;
	}
	
	public static void main(String[] args) throws InterruptedException, Throwable
	{
		JavaExperienceLoggingFacility.addStdOut();
		Map<String, List<String>> pa = CliTools.parseCliOpts(args);
		String un = CliTools.getFirstUnknownParam(pa, PROG_CLI_ENTRIES);
		if(null != un)
		{
			CliTools.printHelpAndExit("UartbusCollisionPacketloss", 1, PROG_CLI_ENTRIES);
		}
		
		int from = UartbusCliTools.parseFrom(pa);
		int interval = INTERVAL.tryParseOrDefault(pa, 1000);
		int count = COUNT_OF_PACKETS.tryParseOrDefault(pa, 0);
		
		Map<Integer, ReplyCollector> stat = new TreeMap<>();
		
		DevCollisionStat ds = new DevCollisionStat();
		
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
						try
						{
							ParsedUartBusPacket packet = new ParsedUartBusPacket(e, true);
							if(packet.to == from)
							{
								int seq = UartbusTools.unpackValue(false, packet.payload, 3).intValue();
								MapTools.incrementCount(getOrCreateRelayCollector(stat, packet.from).responses, seq);
							}
						}
						catch(Exception ex)
						{
							if(!"Incomplete value in the buffer.".equals(ex.getMessage()))
							{
								ex.printStackTrace();
							}
						}
					}
					else
					{
						MapTools.incrementCount(getOrCreateRelayCollector(stat, 0).responses, 0);
						MapTools.incrementCount(ds.lens, e.length);
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
		
		
		int[] sent = new int[1];
		
		regPacketLostStatPrintOnExit(sent, stat, ds);
		
		for(int i=0;i<count || count <= 0;++i)
		{
			conn.sendPacket(generatePacketSequence(from, i));
			++sent[0];
			System.out.println(i+". broadcast ping ");
			Thread.sleep(interval);
		}
		
		System.out.println("Last turn, waiting a bit for the last packets.");
		Thread.sleep(2000);
		
		System.exit(0);
	}
}
