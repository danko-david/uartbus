package eu.javaexperience.electronic.uartbus.rpc;

import java.io.EOFException;
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
	
	protected static final byte[] END_OF_RECEIVE = new byte[]{0};
	
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
					LoggingTools.tryLogFormatException(LOG, LogLevel.ERROR, e, "Exception ocurred while dispatching a package from the UB network at session `%s` the packet `%s`", sess, packet);
				}
			}
		});
		conn.startListen();
	}
	
	protected volatile Set<PacketEndpointQueue> sessions = Collections.newSetFromMap(new WeakHashMap<>());
	
	protected boolean default_echo_loopback = true;
	protected boolean default_loopback_send_packets = false;
	
	protected static class PacketEndpointQueue
	{
		public PacketEndpointQueue(UartbusRpcEndpoint uartbusRpcEndpoint)
		{
			echo_loopback = uartbusRpcEndpoint.default_echo_loopback;
			loopback_send_packets = uartbusRpcEndpoint.default_loopback_send_packets;
		}
		
		public static final PacketEndpointQueue[] emptyPacketEndpointQueue = new PacketEndpointQueue[0];
		protected boolean echo_loopback = true;
		protected boolean loopback_send_packets = false;
		protected BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
		protected RpcSession rpcSession;
	}
	
	protected PacketEndpointQueue getSessionQueue()
	{
		if(null == sessions)
		{
			return null;
		}
		
		RpcSession sess = RpcSessionTools.ensureGetCurrentRpcSession();
		
		Map<String, Object> map = sess.getExtraDataMap();
		PacketEndpointQueue pq = null;
		synchronized(sessions)
		{
			pq = (PacketEndpointQueue) map.get(SESSION_KEY);
			if(null == pq)
			{
				pq = new PacketEndpointQueue(this);
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
		
		RpcSession csess = RpcSessionTools.getCurrentRpcSession();
		
		for(PacketEndpointQueue sess:queues)
		{
			try
			{
				if(sess.loopback_send_packets || (sess.echo_loopback && sess != csess))
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
		
		if("echo_loopback".equals(key))
		{
			return String.valueOf(getSessionQueue().echo_loopback);
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
		
		if("echo_loopback".equals(key))
		{
			Boolean set = (Boolean) CastTo.Boolean.cast(value);
			if(null == set)
			{
				throw new RuntimeException("The given value `"+value+"` can not be casted to boolean.");
			}
			PacketEndpointQueue sess = getSessionQueue();
			LoggingTools.tryLogFormat(LOG, LogLevel.DEBUG, "Set loopback 'echo_loopback' to `%s` for session `%s`", set, sess);
			sess.echo_loopback = set;
		}
	}

	protected static final String SESSION_KEY = "UartbusPacketConnector_PacketEndpointQueue";
	
	@Override
	public byte[] getNextPacket() throws IOException
	{
		try
		{
			PacketEndpointQueue sess = getSessionQueue();
			byte[] ret = END_OF_RECEIVE;
			if(null != sess)
			{
				ret = sess.queue.take();
			}
			if(END_OF_RECEIVE == ret)
			{
				throw new EOFException("End of receive.");
			}
			return ret;
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
		
		//interrupt readers 
		Set<PacketEndpointQueue> old = sessions;
		sessions = null;
		synchronized(old)
		{
			for(PacketEndpointQueue s:old)
			{
				s.queue.add(END_OF_RECEIVE);
			}
		}
	}

	@Override
	public void init()
	{
		getSessionQueue();
	}
}
