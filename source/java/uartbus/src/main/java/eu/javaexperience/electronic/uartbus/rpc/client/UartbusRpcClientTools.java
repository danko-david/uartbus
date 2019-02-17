package eu.javaexperience.electronic.uartbus.rpc.client;

import java.io.IOException;
import java.util.Arrays;

import eu.javaexperience.electronic.uartbus.PacketAssembler;
import eu.javaexperience.electronic.uartbus.rpc.UartbusConnection;
import eu.javaexperience.rpc.bidirectional.BidirectionalRpcDefaultProtocol;
import eu.javaexperience.rpc.javaclient.JavaRpcClientTools;

public class UartbusRpcClientTools
{
	public static UartbusConnection connectTcp(String ip, int port) throws IOException
	{
		return JavaRpcClientTools.createApiWithIpPort(UartbusConnection.class, ip, port, "uartbus", BidirectionalRpcDefaultProtocol.DEFAULT_PROTOCOL_HANDLER_WITH_CLASS);
	}
	
	public static void readAndPrintPackets() throws IOException
	{
		UartbusConnection conn = connectTcp("127.0.0.1", 2112);
		while(true)
		{
			System.out.println(Arrays.toString(conn.getNextPacket()));
		}
	}
	
	public static void main(String[] args) throws Throwable
	{
		new Thread()
		{
			@Override
			public void run()
			{
				try {
					readAndPrintPackets();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}.start();
		
		UartbusConnection conn = connectTcp("127.0.0.1", 2112);
		
		PacketAssembler asm = new PacketAssembler();
		asm.writeShort(3);
		asm.writeShort(12);
		asm.writeByte(1);
		asm.writeByte(0);
		asm.appendCrc8();
		byte[] data = asm.done();
		
		while(true)
		{
			Thread.sleep(50);
			conn.sendPacket(data);
		}
	}
}
