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
import eu.javaexperience.electronic.uartbus.UartbusConsoleEnv;
import eu.javaexperience.electronic.uartbus.UartbusConsoleEnv.UbConsoleResponse;
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
	
	protected static UartbusConsoleEnv cmd = new UartbusConsoleEnv();
	
	public static void main(String[] args) throws IOException
	{
		JavaExperienceLoggingFacility.addStdOut();
		Map<String, List<String>> pa = CliTools.parseCliOpts(args);
		String un = CliTools.getFirstUnknownParam(pa, PROG_CLI_ENTRIES);
		if(null != un)
		{
			CliTools.printHelpAndExit("UartbusConsole", 1, PROG_CLI_ENTRIES);
		}
		
		cmd.setFromAddress(UartbusCliTools.parseFrom(pa));
		cmd.setToAddress(TO.tryParseOrDefault(pa, -1));
		
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
			asm.writeAddressing(cmd.getAddressFrom(), cmd.getAddressTo());
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
			UbConsoleResponse ret = cmd.feed(line);
			switch (ret.getType())
			{
			case NO_OP:
				continue;
			case NEED_HELP:
				printHelp();
				break;
			
			case QUERY_ADDRESS_FROM:
				System.out.println("From address: "+cmd.getAddressFrom());
				break;
			case QUERY_ADDRESS_TO:
				System.out.println("To address: "+cmd.getAddressTo());
				break;
			case SEND_PACKET:
				conn.sendPacket(ret.toPacket());
				if(logTimes)
				{
					System.out.println("<["+getTime()+"]");
				}
				break;
			case UNKNOWN_COMMAND:
				unrecognisedCmd(ret.getMessage());
				break;
			default:
				break;
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
		for(String line:UartbusConsoleEnv.HELP_LINES)
		{
			System.out.println(line);
		}
	}
}
