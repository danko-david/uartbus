package eu.javaexperience.electronic.uartbus.rpc;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import eu.javaexperience.electronic.uartbus.UartbusPacketConnector;
import eu.javaexperience.reflect.Mirror;
import eu.javaexperience.rpc.RpcSession;
import eu.javaexperience.rpc.RpcSessionTools;
import eu.javaexperience.rpc.RpcTools;

public class UartbusRpcEndpoint implements UartbusConnection
{
	protected UartbusPacketConnector conn;
	
	//TODO add packed warehouse
	//TODO add packed delay to manage packet flow rate
	//TODO packed flow delay disable for emergency situation 
	public UartbusRpcEndpoint(UartbusPacketConnector connector)
	{
		this.conn = connector;
		conn.setPacketHook((packet)->
		{
			PacketEndpointQueue[] queues = null;
			synchronized(sessions)
			{
				queues = sessions.toArray(PacketEndpointQueue.emptyPacketEndpointQueue);
			}
			
			for(PacketEndpointQueue sess:queues)
			{
				try
				{
					sess.queue.put(packet);
				}
				catch(Exception e)
				{
					e.printStackTrace();
				}
			}
		});
		conn.startListen();
	}
	
	protected Set<PacketEndpointQueue> sessions = Collections.newSetFromMap(new WeakHashMap<>());
	
	protected static class PacketEndpointQueue
	{
		public final static PacketEndpointQueue[] emptyPacketEndpointQueue = new PacketEndpointQueue[0];
		protected BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
	}
	
	@Override
	public void sendPacket(byte[] data) throws IOException
	{
		conn.sendPacket(data);
	}

	@Override
	public String getAttribute(String key)
	{
		return null;
	}

	@Override
	public void setAttribute(String key, String value)
	{
		
	}

	@Override
	public long getCurrentPacketIndex()
	{
		return 0;
	}

	@Override
	public byte[] getPacket(long index) throws IOException
	{
		throw new UnsupportedOperationException("Packet queue seeking not yet supported.");
	}
	
	protected static final String SESSION_KEY = "UartbusPacketConnector_PacketEndpointQueue";
	
	@Override
	public byte[] getNextPacket() throws IOException
	{
		RpcSession sess = RpcSessionTools.ensureGetCurrentRpcSession();
		
		Map<String, Object> map = sess.getExtraDataMap();
		PacketEndpointQueue pq = null;
		synchronized(sessions)
		{
			pq = (PacketEndpointQueue) map.get(SESSION_KEY);
			if(null == pq)
			{
				pq = new PacketEndpointQueue();
				map.put(SESSION_KEY, pq);
				sessions.add(pq);
			}
		}
		try
		{
			return pq.queue.take();
		}
		catch (InterruptedException e)
		{
			Mirror.propagateAnyway(e);
			return null;
		}
	}

	@Override
	public void close() throws IOException
	{
		conn.close();
	}
}
