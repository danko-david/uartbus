package eu.javaexperience.electronic.uartbus.rpc.client;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import eu.javaexperience.asserts.AssertArgument;
import eu.javaexperience.datastorage.TransactionException;
import eu.javaexperience.electronic.uartbus.UartbusTools;
import eu.javaexperience.electronic.uartbus.rpc.UartbusConnection;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UartBusDevice;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbDevStdNsRoot;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbRpcTools;
import eu.javaexperience.exceptions.IllegalOperationException;
import eu.javaexperience.interfaces.simple.SimpleGet;
import eu.javaexperience.interfaces.simple.publish.SimplePublish1;
import eu.javaexperience.io.IOTools;
import eu.javaexperience.log.JavaExperienceLoggingFacility;
import eu.javaexperience.log.LogLevel;
import eu.javaexperience.log.Loggable;
import eu.javaexperience.log.Logger;
import eu.javaexperience.log.LoggingTools;
import eu.javaexperience.measurement.MeasurementSerie;
import eu.javaexperience.multithread.notify.WaitForSingleEvent;
import eu.javaexperience.patterns.behavioral.mediator.EventMediator;
import eu.javaexperience.semantic.references.MayNull;

public class UartBus implements Closeable
{
	protected static final Logger LOG = JavaExperienceLoggingFacility.getLogger(new Loggable("UartBus"));
	
	//TODO abstract stream pair to make capable to connect directly to the
		//bus through ttyUSBX
	
	protected Closeable connResource;
	protected int fromAddress;
	
	protected SimplePublish1<byte[]> sendPacket;
	
	protected final EventMediator<ParsedUartBusPacket> onNewValidPackageReceived = new EventMediator<>();
	
	protected final LinkedList<UartbusTransaction> pendingRequests = new LinkedList<>();
	
	protected final EventMediator<byte[]> invalidPackets = new EventMediator<>();
	
	protected final EventMediator<byte[]> unrelatedPackets = new EventMediator<>();
	
	public UartBus
	(
		@MayNull Closeable resource,
		SimplePublish1<byte[]> sendPacket,
		int fromAddress
	)
	{
		this.connResource = resource;
		this.sendPacket = sendPacket;
		this.fromAddress = fromAddress;
		
		onNewValidPackageReceived.addEventListener
		(
			(a)->
			{
				synchronized(pendingRequests)
				{
					for(UartbusTransaction pr:pendingRequests)
					{
						if(pr.tryAcceptResponse(a))
						{
							pendingRequests.remove(pr);
							return;
						}
					}
				}
				
				unrelatedPackets.dispatchEvent(a.rawPacket);
			}
		);
	}
	
	public EventMediator<byte[]> getInvalidPacketListener()
	{
		return invalidPackets;
	}
	
	public EventMediator<byte[]> getUnrelatedPacketListener()
	{
		return unrelatedPackets;
	}
	
	public void processPacket(byte[] packet)
	{
		if(null == packet)
		{
			return;
		}
		
		byte[] data = UartbusTools.getValidPacket(packet);
		
		if(null != data)
		{
			ParsedUartBusPacket p = null;
			
			try
			{
				p = new ParsedUartBusPacket(data);
			}
			catch(Exception e)
			{
				LoggingTools.tryLogFormatException(LOG, LogLevel.NOTICE, e, "Exception while parsing packet ");
			}
			
			if(null != p)
			{
				onNewValidPackageReceived.dispatchEvent(p);
			}
		}
		else
		{ 
			invalidPackets.dispatchEvent(packet);
		}
	}
	
	public void addPendingRequest(UartbusTransaction req)
	{
		synchronized(pendingRequests)
		{
			if(pendingRequests.contains(req))
			{
				throw new IllegalOperationException("Transaction already added to pending requests.");
			}
			pendingRequests.add(req);
		}
	}
	
	public boolean revokePendingRequest(UartbusTransaction req)
	{
		synchronized(pendingRequests)
		{
			return pendingRequests.remove(req);
		}
	}
	
	public UartbusTransaction newTransaction
	(
		int to,
		byte[] path,
		byte[] payloadData,
		boolean zeroNamespaceAnswer
	)
		throws IOException
	{
		UartbusTransaction req = new UartbusTransaction(this);
		req.from = fromAddress;
		req.to = to;
		req.path = path;
		req.payload = payloadData;
		req.zeroNamespaceAnswer = zeroNamespaceAnswer;
		req.send();
		return req;
	}
	
	public UartbusTransaction subscribeResponse(int from, int to, byte[] path, boolean zeroNamespace)
	{
		UartbusTransaction ret = new UartbusTransaction(this);
		ret.from = from;
		ret.to = to;
		ret.path = path;
		ret.zeroNamespaceAnswer = zeroNamespace;
		addPendingRequest(ret);
		return ret;
	}
	
	public static UartBus fromTcp(String ip, int port, int fromAddress) throws IOException
	{
		UartbusStreamerEndpoint conn = UartbusRpcClientTools.openIpEndpoint(ip, port, null, true);
		UartBus ret = new UartBus(conn, conn::sendPacket, fromAddress);
		conn.getPacketStreamer().addEventListener(ret::processPacket);
		conn.startStreaming();
		return ret;
	}

	@Override
	public void close() throws IOException
	{
		IOTools.silentClose(connResource);
	}

	public int getFromAddress()
	{
		return fromAddress;
	}
	
	public UartBusDevice device(int addr)
	{
		return new UartBusDevice(this, addr);
	}
	
	public void sendRawPacket(byte[] data)
	{
		sendPacket.publish(data);
	}
	
	public static UartBus fromConnection(UartbusConnection srv, boolean closeConnOnClose, int busFromAddress)
	{
		return fromConnection
		(
			()->srv, 
			()->
			{
				IOTools.silentClose(srv);
				if(closeConnOnClose)
				{
					IOTools.silentClose(srv);
				}
			},
			busFromAddress
		);
	}
	
	public static UartBus fromConnection(SimpleGet<UartbusConnection> gSrv, Closeable onClose, int busFromAddress)
	{
		UartbusStreamerEndpoint stream = new UartbusStreamerEndpoint(gSrv);
		UartBus bus = new UartBus
		(
			onClose,
			stream::sendPacket,
			busFromAddress
		);
		
		stream.getPacketStreamer().addEventListener(bus::processPacket);
		stream.startStreaming();
		return bus;
	}
}
