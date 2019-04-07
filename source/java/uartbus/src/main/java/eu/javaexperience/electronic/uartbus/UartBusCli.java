package eu.javaexperience.electronic.uartbus;

import eu.javaexperience.electronic.IntelHexFile;
import eu.javaexperience.electronic.build.GccBuilderContext;
import eu.javaexperience.electronic.uartbus.cli.apps.UartbusCollisionPacketloss;
import eu.javaexperience.electronic.uartbus.cli.apps.UartbusConsole;
import eu.javaexperience.electronic.uartbus.cli.apps.UartbusPacketloss;
import eu.javaexperience.electronic.uartbus.cli.apps.UartbusPing;
import eu.javaexperience.electronic.uartbus.rpc.UartbusRpcServer;
import eu.javaexperience.electronic.uartbus.rpc.client.UartbusCodeUploader;
import eu.javaexperience.generic.annotations.Ignore;
import eu.javaexperience.rpc.JavaClassRpcUnboundFunctionsInstance;
import eu.javaexperience.rpc.RpcFacility;
import eu.javaexperience.rpc.cli.RpcCliTools;

public class UartBusCli
{
	@Ignore
	public static void main(String[] args)
	{
		RpcFacility rpc = new JavaClassRpcUnboundFunctionsInstance<>(new UartBusCli(), UartBusCli.class);
		if(0 == args.length)
		{
			System.err.println(RpcCliTools.generateCliHelp(rpc));
			System.exit(1);
		}
		RpcCliTools.cliExecute(null, rpc, args).getImpl();
	}
	
	public static void rpcServer(String... args) throws Throwable
	{
		UartbusRpcServer.main(args);
	}
	
	public static void console(String... args) throws Throwable
	{
		UartbusConsole.main(args);
	}
	
	public static void ping(String... args) throws Throwable
	{
		UartbusPing.main(args);
	}
	
	public static void packetloss(String... args) throws Throwable
	{
		UartbusPacketloss.main(args);
	}
	
	public static void collisionPacketloss(String... args) throws Throwable
	{
		UartbusCollisionPacketloss.main(args);
	}
	
	public static void compile(String... args) throws Throwable
	{
		GccBuilderContext.main(args);
	}
	
	public static void upload(String... args) throws Throwable
	{
		UartbusCodeUploader.main(args);
	}
	
	public static void ihex(String... args) throws Throwable
	{
		IntelHexFile.main(args);
	}
	
	//TODO discover, restart (--soft), appdump,
	//grab (wait device appears in the bus and disable application run.
}
