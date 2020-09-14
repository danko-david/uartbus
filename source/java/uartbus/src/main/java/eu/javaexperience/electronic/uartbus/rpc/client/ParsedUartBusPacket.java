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
	
	protected Byte crc8;
	protected Byte validCrc8;
	
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
	
	public static ParsedUartBusPacket fromRawPacketWithCrc(byte[] data)
	{
		byte[] raw = Arrays.copyOf(data, data.length-1);
		ParsedUartBusPacket ret = new ParsedUartBusPacket(raw);
		ret.setCrc8(data[data.length-1]);
		
		return ret;
	}
	
	public Byte getCrc8()
	{
		return crc8;
	}
	
	public void setCrc8(Byte b)
	{
		this.crc8 = b;
	}
	
	public PacketReader readPayload()
	{
		return new PacketReader(payload);
	}
	
	public byte getPacketValidCrc8Value()
	{
		if(null == validCrc8)
		{
			validCrc8 = UartbusTools.crc8(rawPacket);
		}
		return validCrc8;
	}
	
	public boolean isValid()
	{
		if(null == crc8)
		{
			return true;
		}
		
		return getPacketValidCrc8Value() == crc8;
	}

	public String toShortUserText()
	{
		StringBuilder sb = new StringBuilder();
		
		sb.append("From: ");
		sb.append(from);
		sb.append(", To: ");
		sb.append(to);
		sb.append(", payload: ");
		sb.append(UartbusTools.formatColonData(payload));
		if(null != crc8)
		{
			sb.append(", crc8: ");
			sb.append(crc8 & 0xff);
			
			if(!isValid())
			{
				sb.append(" != ");
				sb.append(validCrc8 & 0xff);
			}
		}
		return sb.toString();
	}
}