package eu.javaexperience.electronic.uartbus;

import java.math.BigDecimal;
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

	public short readSShort()
	{
		short ret = 0;
		ret |= ((short) data[ep]) << 8;
		ret |= (data[ep+1] & 0xff);
		ep += 2;
		return ret;
	}

	public int readSInt()
	{
		int ret = 0;
		ret |= ((int) data[ep]) << 24;
		ret |= ((int) data[ep+1]) << 16;
		ret |= ((int) data[ep+2]) << 8;
		ret |= data[ep+3] & 0xff;
		ep += 4;
		return ret;
	}

	public long readSLong()
	{
		long ret = 0;
		ret |= ((long) data[ep]) << 56;
		ret |= ((long) data[ep+1]) << 48;
		ret |= ((long) data[ep+2]) << 40;
		ret |= ((long) data[ep+3]) << 32;
		ret |= ((long) data[ep+4]) << 24;
		ret |= ((long) data[ep+5]) << 16;
		ret |= ((long) data[ep+6]) << 8;
		ret |= data[ep+7] & 0xff;
		ep += 8;
		return ret;
	}
	
	public float readFloat()
	{
		return Float.intBitsToFloat(readSInt());
	}
	
	public double readDouble()
	{
		return Double.longBitsToDouble(readSLong());
	}

	public BigInteger readVsNumber()
	{
		return readInt(true);
	}
	
	public BigInteger readVuNumber()
	{
		return readInt(false);
	}
}
