package eu.javaexperience.electronic.uartbus.rpc.service;

import eu.javaexperience.electronic.uartbus.PacketAssembler;
import eu.javaexperience.reflect.Mirror;

public class UartbusServiceRequest
{
	protected UartbusServiceBridge bridge;
	protected UartbusPacketDispatch request;
	
	public PacketAssembler createResponseBuilder()
	{
		try
		{
			PacketAssembler pa = new PacketAssembler();
			pa.writeAddressing(bridge.getFromAddress(request.getPacket().to), request.getPacket().from);
			pa.write(new byte[] {0});
			pa.write(request.getCurrentPath());
			return pa;
		}
		catch(Exception e)
		{
			Mirror.propagateAnyway(e);
			return null;
		}
	}
}
