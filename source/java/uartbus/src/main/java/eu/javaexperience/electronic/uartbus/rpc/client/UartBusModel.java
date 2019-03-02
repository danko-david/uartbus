package eu.javaexperience.electronic.uartbus.rpc.client;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import eu.javaexperience.asserts.AssertArgument;
import eu.javaexperience.electronic.uartbus.UartbusTools;
import eu.javaexperience.electronic.uartbus.rpc.UartbusConnection;
import eu.javaexperience.interfaces.simple.publish.SimplePublish1;
import eu.javaexperience.io.IOTools;
import eu.javaexperience.multithread.notify.WaitForSingleEvent;

public class UartBusModel implements Closeable
{
	//TODO abstract stream pair to make capable to connect directly to the
		//bus through ttyUSBX
	
	protected UartbusConnection conn;
	protected Thread receiverThread;
	protected int fromAddress;
	
	//TODO dispatch to pending requests: protected MultiMap<Short, PendingPackets>
	
	public static class UartbusRequest
	{
		public int to;
		public int from;
		
		//if channel is negative, we send the request directly
		public int channel;
		public byte[] payload;
		
		public byte[] toPacket()
		{
			return UartbusTools.toPacket(to, from, 6, channel, payload);
		}
		
		public static UartbusRequest request
		(
			int from,
			int to,
			int channel,
			byte[] data
		)
		{
			UartbusRequest req = new UartbusRequest();
			req.from = from;
			req.to = to;
			req.channel = channel;
			req.payload = data;
			
			
			//TODO send
			
			return null;
		}
	}
	
	public static class UartbusResponse
	{
		public UartbusRequest request;
		public byte[] response;
		
		protected WaitForSingleEvent wait = new WaitForSingleEvent();
		
		public boolean waitResponse(long timeoute, TimeUnit unit) throws InterruptedException
		{
			wait.waitForEvent(timeoute, unit);
			synchronized (this)
			{
				return null == response;	
			}
		}
		
		protected void receiveResponse(byte[] data)
		{
			AssertArgument.assertNotNull(data, "data");
			synchronized (this)
			{
				response = data;	
			}
			wait.evenOcurred();
		}
	}
	
	/*
		PacketAssembler asm = new PacketAssembler();
		
		if(null != DATA.getSimple(pa))
		{
			asm.writeAddressing(from, to);
			asm.write(DATA.tryParse(pa));
			asm.appendCrc8();
			byte[] data = asm.done();
			conn.sendPacket(data);
			if(null != EXIT.getAll(pa))
			{
				System.exit(0);
			}
		}
	*/
	
	protected SimplePublish1<byte[]> packetReceived = (e)->
	{
		if(e.length > 0 && UartbusTools.crc8(e, e.length-1) == e[e.length-1])
		{
			//Valid packet
			
			System.out.println(UartbusTools.formatColonData(e));
		}
	};
	
	public static UartBusModel fromTcp(String ip, int port, int fromAddress) throws IOException
	{
		UartBusModel ret = new UartBusModel();
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
}
