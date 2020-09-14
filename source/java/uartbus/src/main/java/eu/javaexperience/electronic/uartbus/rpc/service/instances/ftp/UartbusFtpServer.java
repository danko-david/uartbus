package eu.javaexperience.electronic.uartbus.rpc.service.instances.ftp;

import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.FROM;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.RPC_HOST;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.RPC_PORT;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import eu.javaexperience.asserts.AssertArgument;
import eu.javaexperience.cli.CliEntry;
import eu.javaexperience.cli.CliTools;
import eu.javaexperience.electronic.uartbus.UartbusTools;
import eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools;
import eu.javaexperience.electronic.uartbus.rpc.client.UartBus;
import eu.javaexperience.electronic.uartbus.rpc.datatype.VSigned;
import eu.javaexperience.electronic.uartbus.rpc.datatype.VUnsigned;
import eu.javaexperience.electronic.uartbus.rpc.service.UartbusServiceBridge;
import eu.javaexperience.file.FileSystemTools;
import eu.javaexperience.log.JavaExperienceLoggingFacility;
import eu.javaexperience.nativ.posix.PosixErrnoException;
import eu.javaexperience.reflect.Mirror;

public class UartbusFtpServer
{
	public static final CliEntry<byte[]> RPC_PATH = CliEntry.createFirstArgParserEntry
	(
		e->UartbusTools.parseColonData(e),
		"Rpc prefix path to service",
		"P", "-path"
	);
	
	public static final CliEntry<String> FTP_DIRECTORY = CliEntry.createFirstArgParserEntry
	(
		e->e,
		"FTP directory root in the local filesystem",
		"d", "-directory"
	);

	
	protected static final CliEntry[] PROG_CLI_ENTRIES =
	{
		RPC_HOST,
		RPC_PORT,
		FROM,
		RPC_PATH,
		FTP_DIRECTORY
	};

	
	public static void main(String[] args) throws IOException, PosixErrnoException
	{
		JavaExperienceLoggingFacility.addStdOut();
		Map<String, List<String>> pa = CliTools.parseCliOpts(args);
		String un = CliTools.getFirstUnknownParam(pa, PROG_CLI_ENTRIES);
		
		if(null != un)
		{
			CliTools.printHelpAndExit("UartbusFtpServer", 1, PROG_CLI_ENTRIES);
		}
		
		UartBus bus = UartbusCliTools.cliBusConnect(pa);
		
		String dir = FTP_DIRECTORY.getSimple(pa);
		AssertArgument.assertNotNull(dir, "FTP directory");
		
		FtpBusService fbs = new FtpBusService(FileSystemTools.DEFAULT_FILESYSTEM.fromUri(dir));
		
		byte[] path = RPC_PATH.tryParse(pa);
		if(null == path)
		{
			path = Mirror.emptyByteArray;
		}
		
		UartbusServiceBridge bridge = UartbusServiceBridge.attachServiceTo
		(
			fbs,
			bus,
			UartbusCliTools.parseFrom(pa),
			path
		);
	}
	
	public static VUnsigned fid(int id)
	{
		return new VUnsigned(BigInteger.valueOf(id));
	}
	
	public static VSigned neg(int id)
	{
		return new VSigned(BigInteger.valueOf(id));
	}
}
