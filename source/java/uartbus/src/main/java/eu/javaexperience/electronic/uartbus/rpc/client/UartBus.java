package eu.javaexperience.electronic.uartbus.rpc.client;

import java.io.Closeable;
import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import eu.javaexperience.asserts.AssertArgument;
import eu.javaexperience.datastorage.TransactionException;
import eu.javaexperience.electronic.uartbus.UartbusTools;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UartBusDevice;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbDevStdNsRoot;
import eu.javaexperience.exceptions.IllegalOperationException;
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
		UartbusTransaction req = new UartbusTransaction();
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
		UartbusTransaction ret = new UartbusTransaction();
		ret.from = from;
		ret.to = to;
		ret.path = path;
		ret.zeroNamespaceAnswer = zeroNamespace;
		addPendingRequest(ret);
		return ret;
	}
	
	/**
	 * I know, this is a blob but maybe it's easier to manager request-response
	 * 	as one unit. 
	 * */
	public class UartbusTransaction implements Closeable
	{
		public boolean zeroNamespaceAnswer;
		public int to;
		public int from;
		
		//if channel is negative, we send the request directly
		
		public byte[] path;
		public byte[] payload;
		
		public boolean revoked = false;
		
		public byte[] responsePayload;
		
		public byte[] toPacket()
		{
			return UartbusTools.toPacket(to, from, payload);
		}
		
		public void send() throws IOException
		{
			if(isResponsed())
			{
				throw new IllegalOperationException("Request already sent and responded!");
			}
			
			//this also add the request to the pending list.
			addPendingRequest(this);
			
			byte[] send = toPacket();
			synchronized(this)
			{
				sendPacket.publish(send);
			}
		}
		
		@Override
		public void close() throws IOException
		{
			revoked = true;
			revokePendingRequest(this);
		}
		
		@Override
		protected void finalize() throws Throwable
		{
			//safety net
			close();
		}
		
/****************************** response section ******************************/
		protected WaitForSingleEvent wait = new WaitForSingleEvent();
		
		public boolean isResponsed()
		{
			return null != responsePayload;
		}
		
		public boolean isRevoked()
		{
			return null != responsePayload && revoked;
		}
		
		public boolean waitResponse(long timeout, TimeUnit unit) throws InterruptedException
		{
			AssertArgument.assertTrue(!isRevoked(), "Request has been revoked");
			wait.waitForEvent(timeout, unit);
			AssertArgument.assertTrue(!isRevoked(), "Request has been revoked");
			synchronized(this)
			{
				return null != responsePayload;
			}
		}
		
		public byte[] ensureResponse(long timeout, TimeUnit unit) throws InterruptedException
		{
			return ensureResponse(timeout, unit, null);
		}
		
		public byte[] ensureResponse(long timeout, TimeUnit unit, String errAppendMsg) throws InterruptedException
		{
			if(!waitResponse(timeout, unit))
			{
				throw new TransactionException("Device (`"+to+"`) not responded within "+timeout+" "+unit+" for the request"+(null == errAppendMsg?"":": "+errAppendMsg));
			}
			
			return responsePayload;
		}
		
		protected void receiveResponse(byte[] data)
		{
			AssertArgument.assertNotNull(data, "data");
			synchronized (this)
			{
				responsePayload = data;	
			}
			wait.evenOcurred();
		}
		
		public boolean tryAcceptResponse(ParsedUartBusPacket a)
		{
			//check response by address
			if(a.to == this.from && a.from == this.to && a.payload.length + (zeroNamespaceAnswer?1:0) >= path.length)
			{
				int diff = 0;
				
				if(zeroNamespaceAnswer)
				{
					if(0 != a.payload[0])
					{
						return false;
					}
					diff = 1;
				}
				
				//check response by rpc path
				for(int i=0;i<path.length;++i)
				{
					if(path[i] != a.payload[diff+i])
					{
						return false;
					}
				}
				
				//we got the response.
				try
				{
					receiveResponse(a.payload);
				}
				catch(Exception e)
				{
					e.printStackTrace();
				}
				
				return true;
			}
			return false;
		}
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
	
	public static void main(String[] args) throws Throwable
	{
		UartBus bus = fromTcp("127.0.0.1", 2112, 63);
		UartBusDevice dev = bus.device(1);
		UbDevStdNsRoot root = dev.getRpcRoot();
		
		//root.getBusFunctions().ping();
		
		//root.getBootloaderFunctions().getPowerFunctions().hardwareReset();
		
		/*if(true)
		{
			return;
		}*/
		
		MeasurementSerie ser = new MeasurementSerie();
		
		for(int m=0;m<20;++m)
		{
			try
			{
				for(int i=0;i<1000;++i)
				{
					//ensure host is online
					root.getBusFunctions().ping();
					
					System.out.println(i+". pong");
					Thread.sleep(5);
					
					/*UartbusTransaction reboot = bus.subscribeResponse(-1, 1, new byte[]{0});
					root.getBootloaderFunctions().getPowerFunctions().hardwareReset();
					//this waits until reboot complete
					reboot.ensureResponse(3, TimeUnit.SECONDS);
					System.out.println("reboot done");
					//remove SOS and start app
					System.out.println(root.getBootloaderFunctions().getVar(UbBootloaderVariable.IS_SIGNALING_SOS));
					root.getBootloaderFunctions().setVar(UbBootloaderVariable.IS_SIGNALING_SOS, (byte) 0);
					System.out.println(root.getBootloaderFunctions().getVar(UbBootloaderVariable.IS_SIGNALING_SOS));
					
					System.out.println(root.getBootloaderFunctions().getVar(UbBootloaderVariable.IS_APPLICATION_RUNNING));
					root.getBootloaderFunctions().setVar(UbBootloaderVariable.IS_APPLICATION_RUNNING, (byte) 0x1);
					System.out.println(root.getBootloaderFunctions().getVar(UbBootloaderVariable.IS_APPLICATION_RUNNING));
					
					System.out.println("external reset done");
					*/
					root.getBusFunctions().ping();
					
					System.out.println("TRANSACTION END");
				}
			}
			catch(Exception e)
			{
				continue;
			}
			break;
		}
		System.exit(0);
	}
}
