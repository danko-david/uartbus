package eu.javaexperience.electronic.uartbus.rpc.virtual;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;

import eu.javaexperience.electronic.uartbus.UartbusTools;
import eu.javaexperience.electronic.uartbus.rpc.UartbusConnection;
import eu.javaexperience.electronic.uartbus.rpc.UartbusRpcServer;
import eu.javaexperience.electronic.uartbus.rpc.client.UartBus;
import eu.javaexperience.electronic.uartbus.rpc.client.UartbusStreamerEndpoint;
import eu.javaexperience.io.IOTools;
import eu.javaexperience.log.JavaExperienceLoggingFacility;
import eu.javaexperience.log.LogLevel;
import eu.javaexperience.log.Loggable;
import eu.javaexperience.log.Logger;
import eu.javaexperience.log.LoggingTools;
import eu.javaexperience.reflect.Mirror;
import eu.javaexperience.text.StringTools;

public class VirtualUartBus implements Closeable
{
	protected static final Logger LOG = JavaExperienceLoggingFacility.getLogger(new Loggable("VirtualUartBus"));
	
	protected String name = StringTools.randomString(12);
	
	protected UartbusConnection srv;
	//protected UartbusConnectionDistributorUnit dist;
	
	protected UartBus bus;
	
	protected boolean closed = false;
	
	/*public UartbusConnectionDistributorUnit getConnectionDistributor()
	{
		return dist;
	}*/
	
	public String getBusName()
	{
		return name;
	}

	public void setBusName(String name)
	{
		this.name = name;
	}
	
	protected UartbusStreamerEndpoint logStream;
	
	public void addPacketLogging()
	{
		if(null != logStream)
		{
			return;
		}
		logStream = new UartbusStreamerEndpoint(()->
		{
			try
			{
				return createNewConnection();
			}
			catch (EOFException e1)
			{
				Mirror.propagateAnyway(e1);
				return null;
			}
		});
		logStream.getPacketStreamer().addEventListener(e->
		{
			LoggingTools.tryLogFormat(LOG, LogLevel.DEBUG, "Packet captured on bus `%s`: %s", name, UartbusTools.formatColonDataWithValidation(e));
		});
		logStream.startStreaming();
	}
	
	public UartBus getBus()
	{
		return bus;
	}
	
	public UartbusInstanceDisributorUnit createNewConnection() throws EOFException
	{
		if(closed)
		{
			throw new EOFException("Distributor closed");
		}
		return new UartbusInstanceDisributorUnit(srv);
	}
	
	public static VirtualUartBus createEnv() throws IOException
	{
		return createEnv(63);
	}
	
	public static VirtualUartBus createEnv(int busFrom) throws IOException
	{
		VirtualUartBus ret = new VirtualUartBus();
		ret.srv = UartbusRpcServer.createInProcessDummyServer();
		ret.bus = ret.newBus(busFrom);
		return ret;
	}
	
	@Override
	public void close() throws IOException
	{
		IOTools.silentClose(bus);
		//IOTools.silentClose(dist);
		IOTools.silentClose(srv);
		closed = true;
	}

	public UartBus newBus(int busFrom)
	{
		return UartBus.fromConnection(()->{
			try
			{
				return createNewConnection();
			}
			catch (EOFException e)
			{
				Mirror.propagateAnyway(e);
				return null;
			}
		}, null, busFrom);
	}
}
