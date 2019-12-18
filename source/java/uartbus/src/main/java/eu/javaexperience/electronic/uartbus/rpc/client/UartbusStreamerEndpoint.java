package eu.javaexperience.electronic.uartbus.rpc.client;

import java.io.Closeable;

import eu.javaexperience.electronic.uartbus.rpc.UartbusConnection;
import eu.javaexperience.interfaces.simple.SimpleGet;
import eu.javaexperience.log.JavaExperienceLoggingFacility;
import eu.javaexperience.log.LogLevel;
import eu.javaexperience.log.Loggable;
import eu.javaexperience.log.Logger;
import eu.javaexperience.log.LoggingTools;
import eu.javaexperience.patterns.behavioral.mediator.EventMediator;

public class UartbusStreamerEndpoint implements Closeable
{
	protected static final Logger LOG = JavaExperienceLoggingFacility.getLogger(new Loggable("UartbusStreamerEndpoint"));
	
	protected SimpleGet<UartbusConnection> getConnection;
	protected UartbusConnection workingConnection;
	
	public UartbusStreamerEndpoint(SimpleGet<UartbusConnection> getConnection)
	{
		this.getConnection = getConnection;
	}
	
	public synchronized UartbusConnection getApi()
	{
		if(null == workingConnection)
		{
			workingConnection = getConnection.get();
		}
		return workingConnection;
	}
	
	public synchronized void cleanupConncection()
	{
		workingConnection = null;
	}
	
	protected EventMediator<byte[]> packetStream = new EventMediator<>();
	
	public EventMediator<byte[]> getPacketStreamer()
	{
		return packetStream;
	}
	
	protected Thread streamer;
	protected volatile boolean runStreamer;
	
	public synchronized void startStreaming()
	{
		if(null == streamer)
		{
			runStreamer = true;
			streamer = new Thread()
			{
				public void run()
				{
					try
					{
						while(runStreamer)
						{
							byte[] data = null;
							
							UartbusConnection conn = null;
							try
							{
								conn = getApi();
							}
							catch(Exception e)
							{
								LoggingTools.tryLogFormatException(LOG, LogLevel.FATAL, e, "Exception occurred while getting connection for packet streaming (stopping): ");
								break;
							}
							
							try
							{
								data = conn.getNextPacket();
							}
							catch(Exception e)
							{
								LoggingTools.tryLogFormatException(LOG, LogLevel.ERROR, e, "Exception while receiving packet (continue): ");
								cleanupConncection();
							}
							
							if(null != data)
							{
								packetStream.dispatchEvent(data);
							}
						}
					}
					catch(Exception e)
					{
						LoggingTools.tryLogFormatException(LOG, LogLevel.FATAL, e, "Fatal exception occurred while packet streaming(stopping): ");
					}
					finally
					{
						runStreamer = false;
					}
				}
			};
			
			streamer.start();
			return;
		}
		
		throw new RuntimeException("Packet streaming already running");
	}
	
	/**
	 * Shuts down the Api endpoint but doesn't close the connection 
	 * */
	public synchronized void close()
	{
		if(runStreamer)
		{
			runStreamer = false;
			streamer.interrupt();
		}
	}
}
