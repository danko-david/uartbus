package eu.javaexperience.electronic.uartbus.rpc.client;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import eu.javaexperience.asserts.AssertArgument;
import eu.javaexperience.datastorage.TransactionException;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbRpcTools;
import eu.javaexperience.exceptions.IllegalOperationException;
import eu.javaexperience.log.JavaExperienceLoggingFacility;
import eu.javaexperience.log.LogLevel;
import eu.javaexperience.log.Loggable;
import eu.javaexperience.log.Logger;
import eu.javaexperience.log.LoggingTools;
import eu.javaexperience.multithread.notify.WaitForSingleEvent;

/**
 * I know, this is a blob but maybe it's easier to manager request-response
 * 	as one unit. 
 * */
public class UartbusTransaction implements Closeable
{
	protected static final Logger LOG = JavaExperienceLoggingFacility.getLogger(new Loggable("UartbusTransaction"));
	
	public final UartBus bus;
	
	public boolean zeroNamespaceAnswer;
	public int to;
	public int from;
	
	//if channel is negative, we send the request directly
	
	public byte[] path;
	public byte[] payload;
	
	public volatile boolean revoked = false;
	
	public volatile byte[] responsePayload;
	
	public UartbusTransaction(UartBus bus)
	{
		this.bus = bus;
	}
	
	public byte[] toPacket()
	{
		return UbRpcTools.toPacket(to, from, payload);
	}
	
	public void send() throws IOException
	{
		if(isResponsed())
		{
			throw new IllegalOperationException("Request already sent and responded!");
		}
		
		//this also add the request to the pending list.
		bus.addPendingRequest(this);
		
		byte[] send = toPacket();
		synchronized(this)
		{
			bus.sendPacket.publish(send);
		}
		
		if(LOG.mayLog(LogLevel.TRACE))
		{
			LoggingTools.tryLogFormat(LOG, LogLevel.TRACE, "Request sent: %s", hashCode());
		}
	}
	
	@Override
	public void close() throws IOException
	{
		revoked = true;
		bus.revokePendingRequest(this);
	}
	
	@Override
	protected void finalize() throws Throwable
	{
		//safety net
		close();
	}
	
/****************************** response section ******************************/
	protected final WaitForSingleEvent wait = new WaitForSingleEvent();
	
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
		AssertArgument.assertTrue(!revoked, "Request has been revoked");
		long t0 = System.currentTimeMillis();
		wait.waitForEvent(timeout, unit);
		AssertArgument.assertTrue(!revoked, "Request has been revoked");
		if(LOG.mayLog(LogLevel.TRACE))
		{
			LoggingTools.tryLogFormat(LOG, LogLevel.TRACE, "waitResponse ended for %s after %s ms with result: %s", hashCode(), System.currentTimeMillis()-t0, null != responsePayload);
		}
		return null != responsePayload;
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
		responsePayload = data;
		if(LOG.mayLog(LogLevel.TRACE))
		{
			LoggingTools.tryLogFormat(LOG, LogLevel.TRACE, "Response received for request: %s", hashCode());
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