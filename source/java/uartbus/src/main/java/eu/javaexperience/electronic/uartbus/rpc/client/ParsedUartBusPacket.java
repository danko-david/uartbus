package eu.javaexperience.electronic.uartbus.rpc.client;

import java.util.Arrays;

import eu.javaexperience.electronic.uartbus.PacketReader;
import eu.javaexperience.electronic.uartbus.UartbusTools;

public class ParsedUartBusPacket
{
	public final byte[] rawPacket;
	public final int from;
	public final int to;
	public final byte[] payload;
	
	/**
	 * payload without the crc8
	 * */
	public ParsedUartBusPacket(byte[] data)
	{
		this.rawPacket = data;
		
		int[] read = new int[1];
		int ep = 0;
		this.to = UartbusTools.unpackValue(true, data, ep, read).intValue();
		ep += read[0];
		this.from = UartbusTools.unpackValue(true, data, ep, read).intValue();
		ep += read[0];
		
		payload = Arrays.copyOfRange(data, ep, data.length);
	}
	
	public PacketReader readPayload()
	{
		return new PacketReader(payload);
	}
}