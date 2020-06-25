package eu.javaexperience.electronic.uartbus;

import java.io.IOException;

import eu.javaexperience.text.StringTools;

public class UartbusConsoleEnv
{
	public static final String[] HELP_LINES = new String[]
	{
		"help:",
		"	? : prints this help",
		"	> : sets the destination address like: >3",
		"	?> : prints the current destination address",
		"	< : sets the source address like: >12",
		"	?< : prints the current source address",
		"	lines started with numbers and separated with colon is recognised as packet to send"
	};
	
	protected int addressFrom;
	protected int addressTo;
	
	public static enum UbConsoleInquiry
	{
		NO_OP,
		SEND_PACKET,
		
		QUERY_ADDRESS_FROM,
		QUERY_ADDRESS_TO,
		
		NEED_HELP,
		UNKNOWN_COMMAND
	}
	
	public class UbConsoleResponse
	{
		protected byte[] data;
		protected UbConsoleInquiry inquiry;
		protected String etc;
		
		public UbConsoleResponse(UbConsoleInquiry inquiry, byte[] data, String etc)
		{
			this.inquiry = inquiry;
			this.data = data;
			this.etc = etc;
		}
		
		public UbConsoleInquiry getType()
		{
			return inquiry;
		}
		
		public byte[] getData()
		{
			return data;
		}
		
		public UartbusConsoleEnv getConsole()
		{
			return UartbusConsoleEnv.this;
		}

		public byte[] toPacket() throws IOException
		{
			PacketAssembler asm = new PacketAssembler();
			asm.writeAddressing(addressFrom, addressTo);
			asm.write(data);
			asm.appendCrc8();
			return asm.done();
		}

		public String getMessage()
		{
			return etc;
		}
	}
	
	public UbConsoleResponse feed(String line)
	{
		try
		{
			line = line.trim();
			if(0 == line.length())
			{
				return new UbConsoleResponse(UbConsoleInquiry.NO_OP, null, null);
			}
			
			if(line.startsWith(">"))
			{
				addressFrom = Integer.parseInt(StringTools.getSubstringAfterFirstString(line, ">"));
				line = "?>";
			}
			else if(line.startsWith("<"))
			{
				addressTo = Integer.parseInt(StringTools.getSubstringAfterFirstString(line, "<"));
				line = "?<";
			}
			
			if(line.startsWith("?"))
			{
				if(line.length() == 1)
				{
					return new UbConsoleResponse(UbConsoleInquiry.NEED_HELP, null, null);
				}
				else if(line.length() == 2)
				{
					switch(line.charAt(1))
					{
					case '>':
						return new UbConsoleResponse(UbConsoleInquiry.QUERY_ADDRESS_FROM, null, null);
	
					case '<':
						return new UbConsoleResponse(UbConsoleInquiry.QUERY_ADDRESS_TO, null, null);
					default: 
						return unrecognisedCmd(line);
					}
				}
				else
				{
					return unrecognisedCmd(line);
				}
			}
			else
			{
				return new UbConsoleResponse(UbConsoleInquiry.SEND_PACKET, UartbusTools.parseColonData(line), null);
			}
		}
		catch(Exception e)
		{
			return unrecognisedCmd(line);
		}
	}

	protected UbConsoleResponse unrecognisedCmd(String line)
	{
		return new UbConsoleResponse(UbConsoleInquiry.UNKNOWN_COMMAND, null, line);
	}

	public void setFromAddress(int from)
	{
		addressFrom = from;
	}

	public void setToAddress(Integer to)
	{
		addressTo = to;
	}
	
	public int getAddressFrom()
	{
		return addressFrom;
	}
	
	public int getAddressTo()
	{
		return addressTo;
	}
}
