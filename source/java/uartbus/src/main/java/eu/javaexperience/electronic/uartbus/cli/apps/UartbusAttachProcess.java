package eu.javaexperience.electronic.uartbus.cli.apps;

import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.LOOPBACK;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.RPC_HOST;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.RPC_PORT;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import eu.javaexperience.cli.CliEntry;
import eu.javaexperience.cli.CliTools;
import eu.javaexperience.electronic.uartbus.UartbusTools;
import eu.javaexperience.electronic.uartbus.rpc.UartbusConnection;
import eu.javaexperience.electronic.uartbus.rpc.client.UartbusRpcClientTools;
import eu.javaexperience.log.JavaExperienceLoggingFacility;

public class UartbusAttachProcess
{
	public static final CliEntry<String> PROGRAM = CliEntry.createFirstArgParserEntry
	(
		(e)->e,
		"Application with arguments",
		"a", "-program"
	);
	
	protected static final CliEntry[] PROG_CLI_ENTRIES =
	{
		RPC_HOST,
		RPC_PORT,
		LOOPBACK,
		PROGRAM
	};
	
	public static void printHelpAndExit(int exit)
	{
		System.err.println("Usage of UartbusAttachProcess:\n");
		System.err.println(CliTools.renderListAllOption(PROG_CLI_ENTRIES));
		System.exit(exit);
	}
	
	public static void main(String[] args) throws Exception
	{
		JavaExperienceLoggingFacility.addStdOut();
		Map<String, List<String>> pa = CliTools.parseCliOpts(args);
		String un = CliTools.getFirstUnknownParam(pa, PROG_CLI_ENTRIES);
		if(null != un)
		{
			printHelpAndExit(1);
		}
		
		String prog = PROGRAM.tryParse(pa);
		
		if(null == prog)
		{
			System.err.println("No program specified");
			printHelpAndExit(1);
		}
		
		ProcessBuilder pb = new ProcessBuilder(translateCommandline(prog));
		pb.redirectError(Redirect.INHERIT);
		Process proc = pb.start();
		
		UartbusConnection conn = UartbusRpcClientTools.connectTcp
		(
			RPC_HOST.tryParseOrDefault(pa, "127.0.0.1"),
			RPC_PORT.tryParseOrDefault(pa, 2112)
		);
		
		UartbusRpcClientTools.streamPackets
		(
			RPC_HOST.tryParseOrDefault(pa, "127.0.0.1"),
			RPC_PORT.tryParseOrDefault(pa, 2112),
			e ->
			{
				//write to process
				try
				{
					OutputStream os = proc.getOutputStream();
					String write = UartbusTools.formatColonData(e)+"\n";
					//System.out.print("> "+write);
					os.write(write.getBytes());
					os.flush();
				}
				catch (IOException e1)
				{
					e1.printStackTrace();
				}
			},
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
			}
		);
		
		BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
		String line = null;
		
		while(null != (line = br.readLine()))
		{
			try
			{
				//System.out.println("< "+line);
				conn.sendPacket(UartbusTools.parseColonData(line));
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
		}
		
		System.out.println("Process STDOUT closed");
		proc.waitFor();
	}
	
	/**
	 * https://stackoverflow.com/questions/3259143/split-a-string-containing-command-line-parameters-into-a-string-in-java
	 * [code borrowed from ant.jar]
	 * Crack a command line.
	 * @param toProcess the command line to process.
	 * @return the command line broken into strings.
	 * An empty or null toProcess parameter results in a zero sized array.
	 */
	public static String[] translateCommandline(String toProcess) {
		if (toProcess == null || toProcess.length() == 0) {
			//no command? no string
			return new String[0];
		}
		// parse with a simple finite state machine

		final int normal = 0;
		final int inQuote = 1;
		final int inDoubleQuote = 2;
		int state = normal;
		final StringTokenizer tok = new StringTokenizer(toProcess, "\"\' ", true);
		final ArrayList<String> result = new ArrayList<String>();
		final StringBuilder current = new StringBuilder();
		boolean lastTokenHasBeenQuoted = false;

		while (tok.hasMoreTokens()) {
			String nextTok = tok.nextToken();
			switch (state) {
			case inQuote:
				if ("\'".equals(nextTok)) {
					lastTokenHasBeenQuoted = true;
					state = normal;
				} else {
					current.append(nextTok);
				}
				break;
			case inDoubleQuote:
				if ("\"".equals(nextTok)) {
					lastTokenHasBeenQuoted = true;
					state = normal;
				} else {
					current.append(nextTok);
				}
				break;
			default:
				if ("\'".equals(nextTok)) {
					state = inQuote;
				} else if ("\"".equals(nextTok)) {
					state = inDoubleQuote;
				} else if (" ".equals(nextTok)) {
					if (lastTokenHasBeenQuoted || current.length() != 0) {
						result.add(current.toString());
						current.setLength(0);
					}
				} else {
					current.append(nextTok);
				}
				lastTokenHasBeenQuoted = false;
				break;
			}
		}
		if (lastTokenHasBeenQuoted || current.length() != 0) {
			result.add(current.toString());
		}
		if (state == inQuote || state == inDoubleQuote) {
			throw new RuntimeException("unbalanced quotes in " + toProcess);
		}
		return result.toArray(new String[result.size()]);
	}
}
