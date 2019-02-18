package eu.javaexperience.electronic.uartbus.rpc;

import java.io.Closeable;
import java.io.IOException;

public interface UartbusConnection extends Closeable
{
	//sending data
	public void sendPacket(byte[] data) throws IOException;
	
	public String getAttribute(String key) throws IOException;
	public void setAttribute(String key, String value) throws IOException;
	
	public long getCurrentPacketIndex() throws IOException;
	public byte[] getPacket(long index) throws IOException;
	
	public byte[] getNextPacket() throws IOException;
}
