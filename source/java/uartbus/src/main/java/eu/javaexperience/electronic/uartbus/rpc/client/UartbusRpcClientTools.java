package eu.javaexperience.electronic.uartbus.rpc.client;

import java.io.IOException;

import eu.javaexperience.electronic.uartbus.rpc.UartbusConnection;
import eu.javaexperience.interfaces.simple.SimpleGet;
import eu.javaexperience.interfaces.simple.publish.SimplePublish1;
import eu.javaexperience.log.JavaExperienceLoggingFacility;
import eu.javaexperience.log.LogLevel;
import eu.javaexperience.log.Loggable;
import eu.javaexperience.log.Logger;
import eu.javaexperience.log.LoggingTools;
import eu.javaexperience.reflect.Mirror;
import eu.javaexperience.rpc.bidirectional.BidirectionalRpcDefaultProtocol;
import eu.javaexperience.rpc.javaclient.JavaRpcClientTools;
import eu.javaexperience.semantic.references.MayNull;

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
		public PacketStreamThread(Thread thread)
		{
			this.thread = thread;
		}
		
		public final Thread thread;
	}
	
	public static <T> SimpleGet<T> waitReconnect(SimpleGet<T> connect, String entity)
	{
		return waitReconnect(connect, entity, 100, 200, 500, 1_000, 2_000, 5_000, 10_000);
	}
	
	public static <T> SimpleGet<T> waitReconnect(SimpleGet<T> connect, String entity, int... reconnectWaitTimes)
	{
		return ()->
		{
			for(int i=0;i<reconnectWaitTimes.length;++i)
			{
				try
				{
					return connect.get();
				}
				catch(Exception e)
				{
					if(reconnectWaitTimes[i] < 0)
					{
						throw e;
					}
					
					LoggingTools.tryLogFormatException(LOG, LogLevel.WARNING, e, "Can't do `%s`, waiting `%s` millisec before trying reconnect again. ", entity, reconnectWaitTimes[i]);
					try
					{
						Thread.sleep(reconnectWaitTimes[i]);
					}
					catch (InterruptedException e1)
					{
						return null;
					}
					
					if(i >= reconnectWaitTimes.length-1)
					{
						i = reconnectWaitTimes.length-2;
					}
				}
			}
			return null;
		};
	}
	
	public static PacketStreamThread streamPackets(String ip, int port, SimplePublish1<byte[]> onNewPacket) throws IOException
	{
		return streamPacketsReconnect(ip, port, onNewPacket, null, -1);
	}
	
	public static PacketStreamThread streamPackets(String ip, int port, SimplePublish1<byte[]> onNewPacket, @MayNull SimplePublish1<UartbusConnection> connectionInitializer) throws IOException
	{
		return streamPacketsReconnect(ip, port, onNewPacket, connectionInitializer, -1);
	}
	
	public static PacketStreamThread streamPacketsReconnect(String ip, int port, SimplePublish1<byte[]> onNewPacket, @MayNull SimplePublish1<UartbusConnection> connectionInitializer, int... reconnectRetryDelays) throws IOException
	{
		SimpleGet<UartbusConnection> tcpReconnect = waitReconnect
		(
			()->
			{
				try
				{
					return connectTcp(ip, port);
				}
				catch(IOException e)
				{
					Mirror.propagateAnyway(e);
					return null;
				}
			},
			"Uartbus TCP connection to "+ip+":"+port,
			reconnectRetryDelays
		);
		
		Thread t = new Thread()
		{
			@Override
			public void run()
			{
				while(true)
				{
					try(UartbusConnection conn = tcpReconnect.get())
					{
						if(null != connectionInitializer)
						{
							connectionInitializer.publish(conn);
						}
						while(true)
						{
							onNewPacket.publish(conn.getNextPacket());
						}
					}
					catch(Exception ex)
					{
						LoggingTools.tryLogFormatException(LOG, LogLevel.WARNING, ex, "Exception while receiving and dispatching packet ");
					}
				}
			}
		};
		
		t.setDaemon(true);
		t.start();
		return new PacketStreamThread(t);
	}
}
