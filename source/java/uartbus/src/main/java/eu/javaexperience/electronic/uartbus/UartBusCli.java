package eu.javaexperience.electronic.uartbus;

import java.io.InputStream;

import eu.javaexperience.electronic.IhexCli;
import eu.javaexperience.electronic.uartbus.cli.apps.UartbusAttachProcess;
import eu.javaexperience.electronic.uartbus.cli.apps.UartbusBlink;
import eu.javaexperience.electronic.uartbus.cli.apps.UartbusCodeUploader;
import eu.javaexperience.electronic.uartbus.cli.apps.UartbusCollisionPacketloss;
import eu.javaexperience.electronic.uartbus.cli.apps.UartbusConsole;
import eu.javaexperience.electronic.uartbus.cli.apps.UartbusGrab;
import eu.javaexperience.electronic.uartbus.cli.apps.UartbusLogFile;
import eu.javaexperience.electronic.uartbus.cli.apps.UartbusLogSql;
import eu.javaexperience.electronic.uartbus.cli.apps.UartbusPacketloss;
import eu.javaexperience.electronic.uartbus.cli.apps.UartbusPing;
import eu.javaexperience.electronic.uartbus.cli.apps.UartbusReboot;
import eu.javaexperience.electronic.uartbus.rpc.UartbusRpcServer;
import eu.javaexperience.generic.annotations.Ignore;
import eu.javaexperience.io.IOTools;
import eu.javaexperience.rpc.cli.RpcCliTools;

public class UartBusCli
{
	@Ignore
	public static void main(String[] args)
	{
		RpcCliTools.tryExecuteCommandCollectorClassOrExit(new UartBusCli(), 1, args);
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
	
	public static void upload(String... args) throws Throwable
	{
		UartbusCodeUploader.main(args);
	}
	
	public static void ihex(String... args) throws Throwable
	{
		IhexCli.main(args);
	}
	
	public static void blink(String... args) throws Throwable
	{
		UartbusBlink.main(args);
	}
	
	public static void logSql(String... args) throws Throwable
	{
		UartbusLogSql.main(args);
	}
	
	public static void logFile(String... args) throws Throwable
	{
		UartbusLogFile.main(args);
	}
	
	public static void attachProcess(String... args) throws Throwable
	{
		UartbusAttachProcess.main(args);
	}
	
	public static void grab(String... args) throws Throwable
	{
		UartbusGrab.main(args);
	}
	
	public static void reboot(String... args) throws Throwable
	{
		UartbusReboot.main(args);
	}
	
	public static void version(String... args) throws Throwable
	{
		System.out.println("Uartbus Cli version: ");
		try(InputStream is = ClassLoader.getSystemResourceAsStream("eu/javaexperience/electronic/uartbus/version"))
		{
			System.out.write(IOTools.loadAllFromInputStream(is));
		}
	}
	
	//TODO discover, restart (--soft), appdump, crosslink
}
