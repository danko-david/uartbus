package eu.javaexperience.electronic.uartbus;

import java.io.Closeable;

import eu.javaexperience.interfaces.simple.publish.SimplePublish1;

public interface UartbusPacketConnector extends Closeable
{
	public void setPacketHook(SimplePublish1<byte[]> process);
	public void sendPacket(byte[] data);
	public void startListen();
}
