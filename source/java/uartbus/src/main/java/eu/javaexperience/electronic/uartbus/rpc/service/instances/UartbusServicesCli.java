package eu.javaexperience.electronic.uartbus.rpc.service.instances;

import eu.javaexperience.electronic.uartbus.rpc.service.instances.ftp.UartbusFtpServer;
import eu.javaexperience.generic.annotations.Ignore;
import eu.javaexperience.rpc.cli.RpcCliTools;

public class UartbusServicesCli
{
	@Ignore
	public static void main(String[] args)
	{
		RpcCliTools.tryExecuteCommandCollectorClassOrExit(new UartbusServicesCli(), 1, args);
	}
	
	public static void ftp(String... args) throws Throwable
	{
		UartbusFtpServer.main(args);
	}
	
	//TODO logging (log collector)
}
