package eu.javaexperience.electronic.uartbus;

import eu.javaexperience.electronic.build.cli.GccBuildCli;
import eu.javaexperience.electronic.uartbus.rpc.UartbusRpcServer;
import eu.javaexperience.electronic.uartbus.rpc.client.UartbusConsole;
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
		System.out.println(RpcCliTools.cliExecute(null, rpc, args).getImpl());
	}
	
	public static void rpcServer(String... args) throws Throwable
	{
		UartbusRpcServer.main(args);
	}
	
	public static void console(String... args) throws Throwable
	{
		UartbusConsole.main(args);
	}
	
	public static void build(String... args) throws Throwable
	{
		GccBuildCli.main(args);
	}
	
	
	//utilities to do:
	//TODO code uploader
	
	
}
