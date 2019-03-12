package eu.javaexperience.electronic.uartbus;

import java.math.BigInteger;
import java.util.Arrays;

public class PacketReader
{
	protected byte[] data;
	protected int ep = 0;
	
	public PacketReader(byte[] data)
	{
		this.data = data;
	}
	
	public PacketReader(byte[] data, int ep)
	{
		this(data);
		this.ep = ep;
	}

	
	public byte readSByte()
	{
		return data[ep++];
	}
	
	public short readUByte()
	{
		return (short) (0xff & data[ep++]);
	}
	
	public long readVuint()
	{
		return readInt(false).longValue();
	}
	
	public long readVsint()
	{
		return readInt(true).longValue();
	}
	
	public BigInteger readInt(boolean signed)
	{
		int[] u = new int[1];
		BigInteger ret = UartbusTools.unpackValue(signed, data, ep, u);
		ep += u[0];
		return ret;
	}
	
	public byte[] readBlob(int length)
	{
		byte[] ret = Arrays.copyOfRange(data, ep, ep+length);
		ep += length;
		return ret;
	}
	
	public byte[] readBlobRemain()
	{
		return readBlob(data.length-ep);
	}
	
	public String readString()
	{
		for(int i=0;i<data.length-ep;++i)
		{
			if(0 == data[ep+i])
			{
				return new String(readBlob(i));
			}
		}
		
		throw new RuntimeException("No string in the buffer");
	}
	
}
