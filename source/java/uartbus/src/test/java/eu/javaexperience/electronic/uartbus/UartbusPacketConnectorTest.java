package eu.javaexperience.electronic.uartbus;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.Assert;
import org.junit.Test;

import eu.javaexperience.interfaces.simple.publish.SimplePublish1;
import eu.javaexperience.io.fd.IOStreamFactory;
import eu.javaexperience.reflect.Mirror;

public class UartbusPacketConnectorTest
{
	public static UartbusEscapedStreamPacketConnector createLoopback(byte escape)
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
		
		return new UartbusEscapedStreamPacketConnector
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
			Thread.sleep(100);
		}
		Assert.assertArrayEquals(data, h.data);
	}
	
	public static byte[] bytes(int... vals)
	{
		byte[] ret = new byte[vals.length];
		for(int i=0;i<vals.length;++i)
		{
			ret[i] = (byte) vals[i];
		}
		return ret;
	}
	
	@Test
	public void testUartbusPackUnpack() throws Exception
	{
		testByteLoopbackSquence((byte)0xff, new byte[] {});
		testByteLoopbackSquence((byte)0xff, new byte[]{(byte)0xff, (byte)0xff});
		testByteLoopbackSquence((byte)0xff, new byte[]{-1, 0, 0, -1});
		testByteLoopbackSquence((byte)0xff, new byte[]{12, 5, (byte)192});
		
		testByteLoopbackSquence((byte)0xff, bytes(255, 255, 0, 0, 255));
		
		testByteLoopbackSquence((byte)0xff, bytes(0, 1, 1, 1, 5, 254));
		
		testByteLoopbackSquence((byte)0xff, bytes(255));
		
	}
	

	@Test
	public void testUartbusUnpack() throws Exception
	{
		List<byte[]> expected = new ArrayList<>();
		
		expected.add(new byte[]{20,30,1,0,1});
		expected.add(new byte[]{20,30,30,30,30});
		expected.add(new byte[]{20,30,1,0,76});
		
		List<byte[]> actual = new ArrayList<>();
		
		UartbusEscapedStreamPacketConnector conn = createLoopback((byte) 0xff);
		conn.setPacketHook(new SimplePublish1<byte[]>()
		{
			@Override
			public void publish(byte[] arg0)
			{
				actual.add(arg0);
			}
		});
		
		conn.feedBytes(new byte[]
		{
			20,30,1,0,1,-1,0,
			20,30,30,30,30,-1,0,
			20,30,1,0,76,-1,0
		});
		
		assertEquals(expected.size(), actual.size());
		
		for(int i=0;i<expected.size();++i)
		{
			assertArrayEquals(expected.get(i), actual.get(i));
		}
	}
	
	@Test
	public void testUartbusUnpack2() throws Exception
	{
		List<byte[]> expected = new ArrayList<>();
		
		expected.add(new byte[]{20,30,1,0,76});
		expected.add(new byte[]{20,30,1,0,76});
		expected.add(new byte[]{20,30,1,0,76});
		expected.add(new byte[]{20,30,1,0,76});
		
		expected.add(new byte[]{20,30,1,0,1});
		expected.add(new byte[]{20,30,1,0,1});
		
		expected.add(new byte[]{20,30,1,0,76});
		expected.add(new byte[]{20,30,1,0,76});
		expected.add(new byte[]{20,30,1,0,76});
		expected.add(new byte[]{20,30,1,0,76});
		
		List<byte[]> actual = new ArrayList<>();
		
		UartbusEscapedStreamPacketConnector conn = createLoopback((byte) 0xff);
		conn.setPacketHook(new SimplePublish1<byte[]>()
		{
			@Override
			public void publish(byte[] arg0)
			{
				actual.add(arg0);
			}
		});
		
		conn.feedBytes(new byte[]
		{
			20,30,1,0,76,-1,0,
			20,30,1,0,76,-1,0,
			20,30,1,0,76,-1,0,
			20,30,1,0,76,-1,0,
			20,30,1,0,1,-1,0,
			20,30,1,0,1,-1,0,
			20,30,1,0,76,-1,0,
			20,30,1,0,76,-1,0,
			20,30,1,0,76,-1,0,
			20,30,1,0,76,-1,0
		});
		
		assertEquals(expected.size(), actual.size());
		
		for(int i=0;i<expected.size();++i)
		{
			assertArrayEquals(expected.get(i), actual.get(i));
		}
	}
}
