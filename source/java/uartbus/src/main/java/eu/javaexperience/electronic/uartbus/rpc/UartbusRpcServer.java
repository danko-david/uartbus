package eu.javaexperience.electronic.uartbus.rpc;

import java.io.File;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;

import eu.javaexperience.cli.CliEntry;
import eu.javaexperience.cli.CliTools;
import eu.javaexperience.datareprez.DataObject;
import eu.javaexperience.electronic.SerialTools;
import eu.javaexperience.electronic.uartbus.UartbusPacketConnector;
import eu.javaexperience.electronic.uartbus.rpc.client.UartbusRpcClientTools;
import eu.javaexperience.interfaces.simple.SimpleGet;
import eu.javaexperience.interfaces.simple.getBy.GetBy1;
import eu.javaexperience.interfaces.simple.getBy.GetBy2;
import eu.javaexperience.io.IOStream;
import eu.javaexperience.io.IOTools;
import eu.javaexperience.io.fd.IOStreamFactory;
import eu.javaexperience.log.JavaExperienceLoggingFacility;
import eu.javaexperience.log.LogLevel;
import eu.javaexperience.log.Loggable;
import eu.javaexperience.log.Logger;
import eu.javaexperience.log.LoggingTools;
import eu.javaexperience.reflect.Mirror;
import eu.javaexperience.rpc.JavaClassRpcUnboundFunctionsInstance;
import eu.javaexperience.rpc.RpcTools;
import eu.javaexperience.rpc.SimpleRpcRequest;
import eu.javaexperience.rpc.SimpleRpcSession;
import eu.javaexperience.rpc.SocketRpcServer;
import eu.javaexperience.rpc.bidirectional.BidirectionalRpcDefaultProtocol;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.*;

public class UartbusRpcServer
{
	protected static final Logger LOG = JavaExperienceLoggingFacility.getLogger(new Loggable("UarbusRpcServer"));
	
	protected static final CliEntry<Integer> RPC_PORT = CliEntry.createFirstArgParserEntry
	(
		(e)->Integer.parseInt(e),
		"Rpc listen port",
		"p", "-port"
	);

	protected static final CliEntry<String> SERIAL_DEV = CliEntry.createFirstArgParserEntry
	(
		(e)->e,
		"Serial device path",
		"s", "-serial-device"
	);
	
	protected static final CliEntry<String> DIRECT_SERIAL_DEVICE = CliEntry.createFirstArgParserEntry
	(
		(e)->e,
		"Direct serial driver",
		"i", "-direct-serial-device"
	);

	protected static final CliEntry[] PROG_CLI_ENTRIES =
	{
		WORK_DIR,
		RPC_PORT,
		SERIAL_DEV,
		SERIAL_BAUD,
		RECONNECT,
		DIRECT_SERIAL_DEVICE
	};
	
	public static void printHelpAndExit(int exit)
	{
		System.err.println("Usage of UarbusRpcServer:\n");
		System.err.println(CliTools.renderListAllOption(PROG_CLI_ENTRIES));
		System.exit(1);
	}
	
	public static void main(String[] args) throws Throwable
	{
		JavaExperienceLoggingFacility.addStdOut();
		Map<String, List<String>> pa = CliTools.parseCliOpts(args);
		String un = CliTools.getFirstUnknownParam(pa, PROG_CLI_ENTRIES);
		if(null != un)
		{
			printHelpAndExit(1);
		}
		
		String wd = WORK_DIR.tryParseOrDefault(pa, ".");
		int port = RPC_PORT.tryParseOrDefault(pa, 2112);
		String serial = SERIAL_DEV.tryParseOrDefault(pa, null);
		int baud = SERIAL_BAUD.tryParseOrDefault(pa, -1);
		String directCommand = DIRECT_SERIAL_DEVICE.tryParseOrDefault(pa, null);
		
		boolean reconnect = false;
		
		if(RECONNECT.hasOption(pa))
		{
			reconnect = true;
		}
		
		
		boolean error = false;
		if(null == serial)
		{
			System.err.println("No Serial port specified.");
			error = true;
		}
		
		if(-1 == baud)
		{
			System.err.println("No Serial baud rate specified.");
			error = true;
		}
		
		if(error)
		{
			printHelpAndExit(2);
		}
		
		JavaExperienceLoggingFacility.startLoggingIntoDirectory(new File(wd+"/log/"), "uartbus-rpc-server-");
		
		SimpleGet<IOStream> socketFactory = UartbusRpcClientTools.waitReconnect
		(
			()->
			{
				try
				{
					IOStream  ret;
					if(null != directCommand)
					{
						ret = SerialTools.openDirectSerial(directCommand, serial, baud);
					}
					else
					{
						ret = SerialTools.openSerial(serial, baud);
					}
					Thread.sleep(100);
					ret.getInputStream();
					ret.getOutputStream();
					return ret;
				}
				catch (Exception e)
				{
					Mirror.propagateAnyway(e);
					return null;
				}
			},
			"serial connection to the bus gateway");
		UartbusPacketConnector conn = new UartbusPacketConnector(socketFactory.get(), (byte)0xff);
		if(reconnect)
		{
			conn.setSocketCloseListener(()->
			{
				LoggingTools.tryLogFormat(LOG, LogLevel.ERROR, "Socket error, reconnecting");
				conn.setIoStream(socketFactory.get());
			});
		}
		
		IOTools.closeOnExit(conn);
		
		UartbusRpcEndpoint bus = new UartbusRpcEndpoint(conn);
		
		GetBy1<DataObject, SimpleRpcRequest> dispatcher = RpcTools.createSimpleNamespaceDispatcherWithDiscoverApi
		(
			new JavaClassRpcUnboundFunctionsInstance<>("uartbus", bus, UartbusConnection.class)
		);
		
		SocketRpcServer<IOStream, SimpleRpcSession> srv = RpcTools.newServer
		(
			IOStreamFactory.fromServerSocket(new ServerSocket(port)),
			5,
			BidirectionalRpcDefaultProtocol.DEFAULT_PROTOCOL_HANDLER_WITH_CLASS,
			RpcTools.getSimpleSessionCreator(),
			new GetBy2<DataObject, SimpleRpcSession, DataObject>()
			{
				@Override
				public DataObject getBy(SimpleRpcSession a, DataObject b)
				{
					return dispatcher.getBy(new SimpleRpcRequest(a, b));
				}
			}
		);
		
		srv.start();
	}
}
