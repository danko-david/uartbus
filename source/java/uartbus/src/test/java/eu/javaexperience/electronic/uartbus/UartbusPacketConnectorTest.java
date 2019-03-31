package eu.javaexperience.electronic.uartbus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.Assert;
import org.junit.Test;

import eu.javaexperience.interfaces.simple.publish.SimplePublish1;
import eu.javaexperience.io.fd.IOStreamFactory;
import eu.javaexperience.reflect.Mirror;

public class UartbusPacketConnectorTest
{
	public static UartbusPacketConnector createLoopback(byte escape)
	{
		BlockingQueue<Byte> bs = new LinkedBlockingQueue<>();
		OutputStream os = new OutputStream()
		{
			@Override
			public void write(int b) throws IOException
			{
				bs.add(Byte.valueOf((byte) b));
			}
		};
		
		InputStream is = new InputStream()
		{
			@Override
			public int read() throws IOException
			{
				try
				{
					Byte ret = bs.poll();
					if(null == ret)
					{
						return -1;
					}
					
					return 0xff & ret;
				}
				catch(Exception e)
				{
					Mirror.propagateAnyway(e);
				}
				
				return -1;
			}
			
			@Override
			public int read(byte[] data, int offset, int length)
			{
				try
				{
					int ret = 0;
					int max = length-offset;
					if(max <= 0)
					{
						return 0;
					}
					
					for(int i=0;i<max;++i)
					{
						Byte b = bs.poll();
						if(null == b)
						{
							break;
						}
						data[offset+i] = b;
						
						++ret;
					}
					
					if(0 == ret)
					{
						//block
						Byte b = bs.take();
						data[offset] = b;
						return 1+read(data, offset+1, length-1);
					}
					
					return ret;
				}
				catch(Exception e)
				{
					Mirror.propagateAnyway(e);
				}
				
				return -1;
			}
		};
		
		return new UartbusPacketConnector
		(
			IOStreamFactory.fromInAndOutputStream(is, os),
			escape
		);
	}
	
	protected static class ByteArrayHolder
	{
		volatile byte[] data = null;
	}
	
	public static void testByteLoopbackSquence(byte escape, byte[] data) throws Exception
	{
		UartbusPacketConnector conn = createLoopback(escape);
		ByteArrayHolder h = new ByteArrayHolder();
		conn.setPacketHook(new SimplePublish1<byte[]>()
		{
			@Override
			public void publish(byte[] arg0)
			{
				h.data = arg0;
			}
		});
		
		conn.startListen();
		conn.sendPacket(data);
		
		int i=0;
		while(++i<100 && null == h.data)
		{
			Thread.sleep(50);
		}
		Assert.assertArrayEquals(data, h.data);
	}
	
	@Test
	public void testUartbusPackUnpack() throws Exception
	{
		testByteLoopbackSquence((byte)0xff, new byte[] {});
		testByteLoopbackSquence((byte)0xff, new byte[]{(byte)0xff, (byte)0xff});
		testByteLoopbackSquence((byte)0xff, new byte[]{-1, 0, 0, -1});
		testByteLoopbackSquence((byte)0xff, new byte[]{12, 5, (byte)192});
	}
}
