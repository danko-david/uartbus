package eu.javaexperience.electronic.uartbus.rpc.client;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import eu.javaexperience.asserts.AssertArgument;
import eu.javaexperience.datastorage.TransactionException;
import eu.javaexperience.electronic.uartbus.PacketReader;
import eu.javaexperience.electronic.uartbus.UartbusTools;
import eu.javaexperience.electronic.uartbus.rpc.UartbusConnection;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UartBusDevice;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbDevStdNsRoot;
import eu.javaexperience.exceptions.IllegalOperationException;
import eu.javaexperience.interfaces.simple.publish.SimplePublish1;
import eu.javaexperience.io.IOTools;
import eu.javaexperience.multithread.notify.WaitForSingleEvent;
import eu.javaexperience.patterns.behavioral.mediator.EventMediator;

public class UartBus implements Closeable
{
	//TODO abstract stream pair to make capable to connect directly to the
		//bus through ttyUSBX
	
	protected UartbusConnection conn;
	protected Thread receiverThread;
	protected int fromAddress;
	
	protected final EventMediator<ReceivedBusPacket> onNewValidPackageReceived = new EventMediator<>();
	
	protected final LinkedList<UartbusTransaction> pendingRequests = new LinkedList<>();
	
	protected SimplePublish1<byte[]> packetReceived = (e)->
	{
		byte[] data = UartbusTools.getValidPacket(e);
		if(null != data)
		{
			System.out.println("receive: "+UartbusTools.formatColonData(data));
			onNewValidPackageReceived.dispatchEvent(new ReceivedBusPacket(data));
		}
		else
		{ 
			System.out.println("wrongPacket: "+UartbusTools.formatColonData(e));
		}
	};
	
	{
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
			}
		);
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
	
	protected class ReceivedBusPacket
	{
		public final byte[] original;
		public final int from;
		public final int to;
		public final byte[] payload;
		
		/**
		 * payload without the crc8
		 * */
		public ReceivedBusPacket(byte[] data)
		{
			this.original = data;
			
			int[] read = new int[1];
			int ep = 0;
			this.to = UartbusTools.unpackValue(true, data, ep, read).intValue();
			ep += read[0];
			this.from = UartbusTools.unpackValue(true, data, ep, read).intValue();
			ep += read[0];
			
			payload = Arrays.copyOfRange(data, ep, data.length);
		}
		
		public PacketReader readPayload()
		{
			return new PacketReader(payload);
		}
	}
	
	public UartbusTransaction newTransaction
	(
		int to,
		byte[] path,
		byte[] payloadData
	)
		throws IOException
	{
		UartbusTransaction req = new UartbusTransaction();
		req.from = fromAddress;
		req.to = to;
		req.path = path;
		req.payload = payloadData;
		req.send();
		return req;
	}
	
	public UartbusTransaction subscribeResponse(int from, int to, byte[] path)
	{
		UartbusTransaction ret = new UartbusTransaction();
		ret.from = from;
		ret.to = to;
		ret.path = path;
		addPendingRequest(ret);
		return ret;
	}
	
	/**
	 * I know, this is a blob but maybe it's easier to manager request-response
	 * 	as one unit. 
	 * */
	public class UartbusTransaction implements Closeable
	{
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
			System.out.println("send: "+UartbusTools.formatColonData(send));
			//synchronized (conn)
			{
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				conn.sendPacket(send);
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
		
		public boolean waitResponse(long timeoute, TimeUnit unit) throws InterruptedException
		{
			AssertArgument.assertTrue(!isRevoked(), "Request has been revoked");
			wait.waitForEvent(timeoute, unit);
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
				throw new TransactionException("Device (`"+to+"`) not responded in time "+timeout+" "+unit+" for the request"+(null == errAppendMsg?"":": "+errAppendMsg));
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
		
		public boolean tryAcceptResponse(ReceivedBusPacket a)
		{
			//check response by address
			if(a.to == this.from && a.from == this.to && a.payload.length >= path.length)
			{
				//check response by rpc path
				for(int i=0;i<path.length;++i)
				{
					if(path[i] != a.payload[i])
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
		UartBus ret = new UartBus();
		ret.conn = UartbusRpcClientTools.connectTcp(ip, port);
		ret.fromAddress = fromAddress;
		
		ret.receiverThread = UartbusRpcClientTools.streamPackets
		(
			ip,
			port,
			ret.packetReceived
		);
		
		return ret;
	}

	@Override
	public void close() throws IOException
	{
		IOTools.silentClose(conn);
		receiverThread.interrupt();
	}

	public int getFromAddress()
	{
		return fromAddress;
	}
	
	public UartBusDevice device(int addr)
	{
		return new UartBusDevice(this, addr);
	}
	
	public static void main(String[] args) throws Throwable
	{
		UartBus bus = fromTcp("127.0.0.1", 2112, 63);
		UartBusDevice dev = bus.device(0);
		UbDevStdNsRoot root = dev.getRpcRoot();
		
		//root.getBusFunctions().ping();
		
		//root.getBootloaderFunctions().getPowerFunctions().hardwareReset();
		
		if(true)
		{
			return;
		}
		
		for(int i=0;i<200;++i)
		{
			//ensure host is online
			root.getBusFunctions().ping();
			
			System.out.println("pong");
			
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
		System.exit(0);
	}
}
