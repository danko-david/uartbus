package eu.javaexperience.electronic.uartbus;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;

public class PacketAssembler extends DataOutputStream
{
	public PacketAssembler()
	{
		super(new ByteArrayOutputStream());
	}
	
	public byte[] done() throws IOException
	{
		flush();
		ByteArrayOutputStream baos = (ByteArrayOutputStream) out;
		byte[] ret = baos.toByteArray();
		baos.reset();
		return ret;
	}
	
	public void writePackedValue(boolean signed, Number value) throws IOException
	{
		if(value instanceof BigInteger)
		{
			writePackedValue(signed, (BigInteger) value);
		}
		else
		{
			writePackedValue(signed, value.longValue());
		}
	}
	
	public void writeString(String val) throws IOException
	{
		write(val.getBytes());
		writeByte(0);
	}
	
	public void writePackedValue(boolean signed, BigInteger value) throws IOException
	{
		byte[] data = new byte[UartbusTools.calcPackReqBytes(signed, value)];
		UartbusTools.packValue(signed, value, data, 0);
		write(data);
	}
	
	public void writePackedValue(boolean signed, long value) throws IOException
	{
		writePackedValue(signed, BigInteger.valueOf(value));
	}
	
	public void writePackedValue(boolean signed, int value) throws IOException
	{
		writePackedValue(signed, BigInteger.valueOf(value));
	}
	
	public void writeAddress(int val) throws IOException
	{
		writePackedValue(true, val);
	}
	
	public void writeAddressing(int from, int to) throws IOException
	{
		writeAddress(to);
		writeAddress(from);
	}

	public void appendCrc8() throws IOException
	{
		flush();
		ByteArrayOutputStream baos = (ByteArrayOutputStream) out;
		writeByte(UartbusTools.crc8(baos.toByteArray()));
	}
}
