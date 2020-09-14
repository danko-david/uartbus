package eu.javaexperience.electronic.uartbus.rpc.service;

import java.util.Arrays;

import eu.javaexperience.electronic.uartbus.PacketReader;
import eu.javaexperience.electronic.uartbus.rpc.client.ParsedUartBusPacket;
import eu.javaexperience.reflect.Mirror;

public class UartbusPacketDispatch
{
	protected ParsedUartBusPacket packet;
	protected int ep = 0;
	
	public UartbusPacketDispatch(ParsedUartBusPacket packet)
	{
		this.packet = packet;
	}

	public ParsedUartBusPacket getPacket()
	{
		return packet;
	}
	
	public byte[] getPayload()
	{
		return packet.payload;
	}
	
	public int getDispatchByteIndex()
	{
		return ep;
	}
	
	public void setDispatchByteIndex(int index)
	{
		ep = index;
	}
	
	public int getRemainingBytes()
	{
		return getPayload().length-ep;
	}

	public byte popNextByte()
	{
		return packet.payload[ep++];
	}

	public byte[] getCurrentPath()
	{
		return Arrays.copyOfRange(packet.payload, 0, Math.max(0, ep-1));
	}

	public byte[] getPayloadTo(int dix)
	{
		return Arrays.copyOfRange(packet.payload, 0, Math.max(0, dix-1));
	}

	public PacketReader createReaderCurrentPoz()
	{
		if(ep == packet.payload.length)
		{
			return new PacketReader(Mirror.emptyByteArray);
		}
		return new PacketReader(Arrays.copyOfRange(packet.payload, ep, packet.payload.length));
	}

	public void moveDispatch(int diff)
	{
		ep += diff;
	}
}
