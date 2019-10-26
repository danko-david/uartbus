package eu.javaexperience.electronic.uartbus.rpc.client;

import java.io.IOException;

import eu.javaexperience.electronic.uartbus.rpc.UartbusConnection;
import eu.javaexperience.interfaces.simple.publish.SimplePublish1;
import eu.javaexperience.log.JavaExperienceLoggingFacility;
import eu.javaexperience.log.LogLevel;
import eu.javaexperience.log.Loggable;
import eu.javaexperience.log.Logger;
import eu.javaexperience.log.LoggingTools;
import eu.javaexperience.rpc.bidirectional.BidirectionalRpcDefaultProtocol;
import eu.javaexperience.rpc.javaclient.JavaRpcClientTools;

public class UartbusRpcClientTools
{
	protected static final Logger LOG = JavaExperienceLoggingFacility.getLogger(new Loggable("UartbusRpcClientTools"));
	
	public static UartbusConnection connectTcp(String ip, int port) throws IOException
	{
		return JavaRpcClientTools.createApiWithIpPort
		(
			UartbusConnection.class,
			ip,
			port,
			"uartbus",
			BidirectionalRpcDefaultProtocol.DEFAULT_PROTOCOL_HANDLER_WITH_CLASS
		);
	}
	
	public static class PacketStreamThread
	{
		public PacketStreamThread(Thread thread, UartbusConnection conn)
		{
			this.thread = thread;
			this.conn = conn;
		}
		
		public final Thread thread;
		public final UartbusConnection conn;
	}
	
	public static PacketStreamThread streamPackets(String ip, int port, SimplePublish1<byte[]> onNewPacket) throws IOException
	{
		UartbusConnection _conn = connectTcp(ip, port);
		Thread t = new Thread()
		{
			@Override
			public void run()
			{
				try(UartbusConnection conn = _conn)
				{
					while(true)
					{
						try
						{
							onNewPacket.publish(conn.getNextPacket());
						}
						catch(Exception e)
						{
							LoggingTools.tryLogFormatException(LOG, LogLevel.WARNING, e, "Exception while receiving and dispatching packet ");
						}
					}
				}
				catch(Exception ex)
				{
					ex.printStackTrace();
				}
			}
		};
		
		t.start();
		return new PacketStreamThread(t, _conn);
	}
}
