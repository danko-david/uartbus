package eu.javaexperience.electronic.uartbus;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

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

	public void appendCrc8() throws IOException
	{
		flush();
		ByteArrayOutputStream baos = (ByteArrayOutputStream) out;
		writeByte(UartbusTools.crc8(baos.toByteArray()));
	}
}
