package eu.javaexperience.electronic.uartbus.rpc;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

public interface UartbusConnection extends Closeable
{
	public void init();
	
	//sending data
	public void sendPacket(byte[] data) throws IOException;
	
	public Map<String, String> listAttributes();
	public String getAttribute(String key) throws IOException;
	public void setAttribute(String key, String value) throws IOException;
	
	public byte[] getNextPacket() throws IOException;
}
