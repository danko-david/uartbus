package eu.javaexperience.electronic.uartbus.rpc.virtual;

import java.io.IOException;
import java.util.Map;

import eu.javaexperience.electronic.uartbus.UartbusTools;
import eu.javaexperience.electronic.uartbus.rpc.UartbusConnection;

public abstract class UartbusThreadLocalDistributor implements UartbusConnection
{
	protected UartbusConnection conn;
	
	public UartbusThreadLocalDistributor(UartbusConnection conn)
	{
		this.conn = conn;
	}
	
	@Override
	public void close() throws IOException
	{
	}
	
	protected abstract void setBeforeCall();
	protected abstract void resetAfterCall();

	@Override
	public void sendPacket(byte[] data) throws IOException
	{
		setBeforeCall();
		try
		{
			conn.sendPacket(data);
		}
		finally
		{
			resetAfterCall();
		}
	}

	@Override
	public Map<String, String> listAttributes()
	{
		setBeforeCall();
		try
		{
			return conn.listAttributes();
		}
		finally
		{
			resetAfterCall();
		}
	}

	@Override
	public String getAttribute(String key) throws IOException
	{
		setBeforeCall();
		try
		{
			return conn.getAttribute(key);
		}
		finally
		{
			resetAfterCall();
		}
	}

	@Override
	public void setAttribute(String key, String value) throws IOException
	{
		setBeforeCall();
		try
		{
			conn.setAttribute(key, value);
		}
		finally
		{
			resetAfterCall();
		}
	}

	@Override
	public byte[] getNextPacket() throws IOException
	{
		setBeforeCall();
		try
		{
			return conn.getNextPacket();
		}
		finally
		{
			resetAfterCall();
		}
	}
	
	@Override
	public void init()
	{
		setBeforeCall();
		try
		{
			UartbusTools.initConnection(conn);
		}
		finally
		{
			resetAfterCall();
		}
	}
}
