package eu.javaexperience.electronic.uartbus.rpc.client;

import java.io.IOException;

import eu.javaexperience.electronic.uartbus.rpc.UartbusConnection;
import eu.javaexperience.interfaces.simple.publish.SimplePublish1;
import eu.javaexperience.rpc.bidirectional.BidirectionalRpcDefaultProtocol;
import eu.javaexperience.rpc.javaclient.JavaRpcClientTools;

public class UartbusRpcClientTools
{
	public static UartbusConnection connectTcp(String ip, int port) throws IOException
	{
		return JavaRpcClientTools.createApiWithIpPort
		(
			UartbusConnection.class,
			ip,
			port,
			"uartbus",
			BidirectionalRpcDefaultProtocol.DEFAULT_PROTOCOL_HANDLER_WITH_CLASS
		);
	}
	
	public static Thread streamPackets(String ip, int port, SimplePublish1<byte[]> onNewPacket)
	{
		Thread t = new Thread()
		{
			@Override
			public void run()
			{
				try(UartbusConnection conn = connectTcp(ip, port))
				{
					while(true)
					{
						onNewPacket.publish(conn.getNextPacket());
					}
				}
				catch(Exception e)
				{
					e.printStackTrace();
				}
			}
		};
		
		t.start();
		return t;
	}
}
