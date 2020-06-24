package eu.javaexperience.electronic.uartbus.cli.apps;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
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
import eu.javaexperience.log.Loggable;
import eu.javaexperience.log.Logger;
import eu.javaexperience.text.Format;
import eu.javaexperience.text.StringTools;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.*;

public class UartbusConsole
{
	protected static final Logger LOG = JavaExperienceLoggingFacility.getLogger(new Loggable("UartbusConsole"));
	
	protected static final CliEntry<byte[]> DATA = CliEntry.createFirstArgParserEntry
	(
		(e)->UartbusTools.parseColonData(e),
		"Data to send (colon separated uint8_t)",
		"d", "-data"
	);
	
	protected static final CliEntry<Boolean> EXIT = CliEntry.createFirstArgParserEntry
	(
		(e)->true,
		"Exit after packet sent",
		"e", "-exit"
	);
	
	protected static final CliEntry[] PROG_CLI_ENTRIES =
	{
		RPC_HOST,
		RPC_PORT,
		FROM,
		TO,
		DATA,
		EXIT,
		LOG_TIME,
		LOOPBACK
	};
	
	public static void main(String[] args) throws IOException
	{
		JavaExperienceLoggingFacility.addStdOut();
		Map<String, List<String>> pa = CliTools.parseCliOpts(args);
		String un = CliTools.getFirstUnknownParam(pa, PROG_CLI_ENTRIES);
		if(null != un)
		{
			CliTools.printHelpAndExit("UartbusConsole", 1, PROG_CLI_ENTRIES);
		}
		
		int from = UartbusCliTools.parseFrom(pa);
		int to = TO.tryParseOrDefault(pa, -1);
		
		boolean logTimes = LOG_TIME.hasOption(pa);
		
		UartbusStreamerEndpoint rpc = UartbusRpcClientTools.openIpEndpoint
		(
			RPC_HOST.tryParseOrDefault(pa, "127.0.0.1"),
			RPC_PORT.tryParseOrDefault(pa, 2112),
			(connection)->
			{
				if(LOOPBACK.hasOption(pa))
				{
					try
					{
						connection.setAttribute("loopback_send_packets", "true");
					}
					catch (IOException e1)
					{
						e1.printStackTrace();
					}
				}
			},
			false
		);
		
		rpc.getPacketStreamer().addEventListener
		(
			e ->
			{
				try
				{
					if(0 == e.length)
					{
						return;
					}
					boolean valid = UartbusTools.crc8(e, e.length-1) == e[e.length-1];
					StringBuilder sb = new StringBuilder();
					if(logTimes)
					{
						sb.append(">[");
						sb.append(getTime());
						sb.append("] ");
					}
					if(!valid)
					{
						sb.append("!");
					}
					sb.append(UartbusTools.formatColonData(e));
					System.out.println(sb.toString());
				}
				catch(Exception ex)
				{
					ex.printStackTrace();
				}
			}
		);
		
		rpc.startStreaming();
		
		UartbusConnection conn = rpc.getApi();
		
		PacketAssembler asm = new PacketAssembler();
		
		if(null != DATA.getSimple(pa))
		{
			asm.writeAddressing(from, to);
			asm.write(DATA.tryParse(pa));
			asm.appendCrc8();
			byte[] data = asm.done();
			conn.sendPacket(data);
			if(null != EXIT.getAll(pa))
			{
				System.exit(0);
			}
		}
		
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String line = null;
		
		while(null != (line = br.readLine()))
		{
			try
			{
				line = line.trim();
				if(0 == line.length())
				{
					continue;
				}
				
				if(line.startsWith(">"))
				{
					from = Integer.parseInt(StringTools.getSubstringAfterFirstString(line, ">"));
					line = "?>";
				}
				else if(line.startsWith("<"))
				{
					to = Integer.parseInt(StringTools.getSubstringAfterFirstString(line, "<"));
					line = "?<";
				}
				
				if(line.startsWith("?"))
				{
					if(line.length() == 1)
					{
						printHelp();
					}
					else if(line.length() == 2)
					{
						switch(line.charAt(1))
						{
						case '>':
							System.out.println("From address: "+from);
							break;
		
						case '<':
							System.out.println("To address: "+to);
							break;
						default: 
							unrecognisedCmd(line);
						}
					}
					else
					{
						unrecognisedCmd(line);
					}
				}
				else
				{
					byte[] data = null;
					
					data = UartbusTools.parseColonData(line);
					
					asm.writeAddressing(from, to);
					asm.write(data);
					asm.appendCrc8();
					conn.sendPacket(asm.done());
					if(logTimes)
					{
						System.out.println("<["+getTime()+"]");
					}
				}
			}
			catch(Exception e)
			{
				System.out.println(e.getMessage());
				unrecognisedCmd(line);
			}
		}
	}
	
	protected static String getTime()
	{
		return Format.UTC_SQL_TIMESTAMP_MS.format(new Date());
	}
	
	protected static void unrecognisedCmd(String line)
	{
		System.out.println("Unrecognised command or packet sequence: `"+line+"` enter `?` to display help");
	}
	
	protected static void printHelp()
	{
		System.out.println("help:");
		System.out.println("	? : prints this help");
		System.out.println("	> : sets the destination address like: >3");
		System.out.println("	?> : prints the current destination address");
		System.out.println("	< : sets the source address like: >12");
		System.out.println("	?< : prints the current source address");
		System.out.println("	lines started with numbers and separated with colon is recognised as packet to send");
	}
}
