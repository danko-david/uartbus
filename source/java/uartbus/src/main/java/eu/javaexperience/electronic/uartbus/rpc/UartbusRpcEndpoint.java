package eu.javaexperience.electronic.uartbus.rpc;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import eu.javaexperience.collection.map.OneShotMap;
import eu.javaexperience.electronic.uartbus.UartbusPacketConnector;
import eu.javaexperience.log.JavaExperienceLoggingFacility;
import eu.javaexperience.log.LogLevel;
import eu.javaexperience.log.Loggable;
import eu.javaexperience.log.Logger;
import eu.javaexperience.log.LoggingTools;
import eu.javaexperience.reflect.CastTo;
import eu.javaexperience.reflect.Mirror;
import eu.javaexperience.rpc.RpcSession;
import eu.javaexperience.rpc.RpcSessionTools;

public class UartbusRpcEndpoint implements UartbusConnection
{
	protected static final Logger LOG = JavaExperienceLoggingFacility.getLogger(new Loggable("UartbusRpcEndpoint"));
	
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
					LoggingTools.tryLogFormatException(LOG, LogLevel.ERROR, e, "Excepton ocurred while dispatching a package from the UB network at session `%s` the packet `%s`", sess, packet);
				}
			}
		});
		conn.startListen();
	}
	
	protected Set<PacketEndpointQueue> sessions = Collections.newSetFromMap(new WeakHashMap<>());
	
	protected static class PacketEndpointQueue
	{
		public static final PacketEndpointQueue[] emptyPacketEndpointQueue = new PacketEndpointQueue[0];
		protected boolean loopback_send_packets = false;
		protected BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
		protected RpcSession rpcSession;
	}
	
	protected PacketEndpointQueue getSessionQueue()
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
				pq.rpcSession = sess;
				map.put(SESSION_KEY, pq);
				sessions.add(pq);
			}
		}
		
		return pq;
	}
	
	@Override
	public void sendPacket(byte[] data) throws IOException
	{
		conn.sendPacket(data);
		PacketEndpointQueue[] queues;
		synchronized(sessions)
		{
			queues = sessions.toArray(PacketEndpointQueue.emptyPacketEndpointQueue);
		}
		
		if(LOG.mayLog(LogLevel.TRACE))
		{
			//this spotted my ugly solution of double uartbus connection and the fact why loopback_send_packets not work (called only at the first connection)
			LoggingTools.tryLogFormat(LOG, LogLevel.TRACE, "sendPacket trying loopback on sessions: `%s`", Arrays.toString(queues));
		}
		
		for(PacketEndpointQueue sess:queues)
		{
			try
			{
				if(sess.loopback_send_packets)
				{
					sess.queue.put(data);
				}
			}
			catch(Exception e)
			{
				LoggingTools.tryLogFormatException(LOG, LogLevel.ERROR, e, "Excepton ocurred while dispatching a loopback package at session `%s` the packet `%s`", sess, data);
			}
		}
	}

	@Override
	public Map<String, String> listAttributes()
	{
		return new OneShotMap<String, String>("loopback_send_packets", "The call of `getNextPacket` functions returns also the you sent with `sendPacket`. Used to implement full network sniffing");
	}
	
	@Override
	public String getAttribute(String key)
	{
		if("loopback_send_packets".equals(key))
		{
			return String.valueOf(getSessionQueue().loopback_send_packets);
		}
		
		return null;
	}

	@Override
	public void setAttribute(String key, String value)
	{
		if("loopback_send_packets".equals(key))
		{
			Boolean set = (Boolean) CastTo.Boolean.cast(value);
			if(null == set)
			{
				throw new RuntimeException("The given value `"+value+"` can not be casted to boolean.");
			}
			PacketEndpointQueue sess = getSessionQueue();
			LoggingTools.tryLogFormat(LOG, LogLevel.DEBUG, "Set loopback 'loopback_send_packets' to `%s` for session `%s`", set, sess);
			sess.loopback_send_packets = set;
		}
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
		
		try
		{
			return getSessionQueue().queue.take();
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
