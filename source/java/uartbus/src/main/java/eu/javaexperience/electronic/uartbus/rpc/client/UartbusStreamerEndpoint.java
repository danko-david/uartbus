package eu.javaexperience.electronic.uartbus.rpc.client;

import java.io.Closeable;
import java.io.IOException;

import eu.javaexperience.electronic.uartbus.UartbusTools;
import eu.javaexperience.electronic.uartbus.rpc.UartbusConnection;
import eu.javaexperience.interfaces.simple.SimpleGet;
import eu.javaexperience.log.JavaExperienceLoggingFacility;
import eu.javaexperience.log.LogLevel;
import eu.javaexperience.log.Loggable;
import eu.javaexperience.log.Logger;
import eu.javaexperience.log.LoggingTools;
import eu.javaexperience.multithread.notify.WaitForEvents;
import eu.javaexperience.patterns.behavioral.mediator.EventMediator;
import eu.javaexperience.reflect.Mirror;

public class UartbusStreamerEndpoint implements Closeable
{
	protected static final Logger LOG = JavaExperienceLoggingFacility.getLogger(new Loggable("UartbusStreamerEndpoint"));
	
	protected SimpleGet<UartbusConnection> getConnection;
	protected volatile UartbusConnection workingConnection;
	
	public UartbusStreamerEndpoint(SimpleGet<UartbusConnection> getConnection)
	{
		this.getConnection = getConnection;
	}
	
	public UartbusConnection getApi()
	{
		if(null == workingConnection)
		{
			workingConnection = getConnection.get();
			UartbusTools.initConnection(workingConnection);
		}
		return workingConnection;
	}
	
	public void sendPacket(byte[] data)
	{
		try
		{
			getApi().sendPacket(data);
		}
		catch (IOException e)
		{
			Mirror.propagateAnyway(e);
		}
	}
	
	public void cleanupConncection()
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
			WaitForEvents w = new WaitForEvents(1);
			WaitForEvents[] ws = new WaitForEvents[] {w};
			
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
							
							if(null == conn)
							{
								break;
							}
							
							if(null != ws[0])
							{
								ws[0].call();
								ws[0] = null;
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
			w.waitForAllEvent();
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
