package eu.javaexperience.electronic.uartbus;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.Arrays;

import eu.javaexperience.electronic.uartbus.rpc.UartbusConnection;
import eu.javaexperience.electronic.uartbus.rpc.client.ParsedUartBusPacket;
import eu.javaexperience.log.JavaExperienceLoggingFacility;
import eu.javaexperience.log.LogLevel;
import eu.javaexperience.log.Loggable;
import eu.javaexperience.log.Logger;
import eu.javaexperience.log.LoggingTools;
import eu.javaexperience.text.StringTools;

public class UartbusTools
{
	protected static final Logger LOG = JavaExperienceLoggingFacility.getLogger(new Loggable("UartbusTools"));
	
	private UartbusTools() {}
	
	public static byte crc8(byte[] data)
	{
		return crc8(data, data.length);
	}
	
	public static byte crc8(byte[] data, int length)
	{
		byte crc = 0;
		byte v;
		int i;
		
		for(i=0;i<length;++i)
		{
			v = (byte) ((data[i] ^ crc) & 0xff); 
			crc = 0;
			if((v & 1) != 0)
				crc ^= 0x5e; 
			if((v & 2) != 0)
				crc ^= 0xbc; 
			if((v & 4) != 0)
				crc ^= 0x61; 
			if((v & 8) != 0)
				crc ^= 0xc2; 
			if((v & 0x10) != 0)
				crc ^= 0x9d; 
			if((v & 0x20) != 0)
				crc ^= 0x23; 
			if((v & 0x40) != 0)
				crc ^= 0x46; 
			if((v & 0x80) != 0)
				crc ^= 0x8c;
		}

		return crc;
	}
	
	public static byte[] parseColonData(String data)
	{
		String[] ns = StringTools.plainSplit(data, ":");
		byte[] ret = new byte[ns.length];
		for(int i=0;i<ns.length;++i)
		{
			ret[i] = (byte) (Integer.parseInt(ns[i]) & 0xff);
		}
		return ret;
	}

	public static String formatColonData(byte[] e)
	{
		StringBuilder sb = new StringBuilder();
		for(int i=0;i<e.length;++i)
		{
			if(i != 0)
			{
				sb.append(":");
			}
			sb.append(0xff & e[i]);
		}
		return sb.toString();
	}
	
	public static String formatColonDataWithValidation(byte[] e)
	{
		if(UartbusTools.crc8(e, e.length-1) != e[e.length-1])
		{
			return "!"+formatColonData(e);
		}
		return formatColonData(e);
	}
	
	public static enum PacketFormattingMode
	{
		RAW_COLON,
		RAW_COLON_WITH_VERIFY,
		PARSED_PACKET
	}
	
	public static String formatPacketWithMode(PacketFormattingMode mode, byte[] data)
	{
		switch(mode)
		{
		case PARSED_PACKET:
			try
			{
				return ParsedUartBusPacket.fromRawPacketWithCrc(data).toShortUserText();
			}
			catch(Throwable t)
			{
				return formatColonDataWithValidation(data);
			}
			
		case RAW_COLON:
			return formatColonData(data);
			
		case RAW_COLON_WITH_VERIFY:
			return formatColonDataWithValidation(data);
		}
		
		return null;
	}
	
	public static int packValue(boolean signed, int value, byte[] dst, int startIndex)
	{
		return packValue(signed, BigInteger.valueOf(value), dst, startIndex);
	}
	
	public static int packValue(boolean signed, long value, byte[] dst, int startIndex)
	{
		return packValue(signed, BigInteger.valueOf(value), dst, startIndex);
	}
	
	public static int packValue(boolean signed, BigInteger value, byte[] dst, int startIndex)
	{
		Boolean negative = null;
		if(!signed && value.signum() < 0)
		{
			throw new RuntimeException("Value must be unsigned: "+value);
		}

		if(signed)
		{
			if(value.signum() < 0)
			{
				value = value.negate().subtract(BigInteger.ONE);
				negative = true;
			}
			else
			{
				negative = false;
			}
		}
		
		byte[] re = value.toByteArray();
		if(re[0] == 0)
		{
			re = Arrays.copyOfRange(re, 1, re.length);
		}
		
		return packValue(negative, re, dst, startIndex);
	}
	
	public static int calcPackReqBytes(boolean signed, BigInteger value)
	{
		if(signed && value.signum() < 0)
		{
			value = value.negate();
		}
		return calcPackReqBytes(signed, value.toByteArray());
	}
	
	public static int calcPackReqBytes(boolean signed, byte[] value)
	{
		int sReq = 9*(value.length-1) + (value.length-1)/7;
		
		int lst = 0xff & value[0];
		
		boolean msb = (lst & 0x80) == 0x80;
		
		while(lst != 0)
		{
			++sReq;
			lst >>>= 1;
		}
		
		if(msb)
		{
			++sReq;
		}
		
		if(signed)
		{
			++sReq;
		}
		
		sReq += 8;
		sReq /= 8;
		
		return sReq;
	}
	
	/**
	 * packed byte scheme:
	 * 	XN------|X-------|X------- 
	 * 
	 * first byte:
	 * X extended address (0 = no, 1 = yes)
	 * N negated value (0 = no, 1 = yes)
	 * 
	 * n'th byte:
	 * X value continued in the next byte + 7 bit number value
	 * 
	 * negative:
	 * 	null: unsigned value
	 * 	false: signed positive
	 * 	true: signed negative
	 * */
	public static int packValue(Boolean negative, byte[] value, byte[] dst, int startIndex)
	{
		if(0 == value.length)
		{
			if(Boolean.TRUE == negative)
			{
				dst[startIndex] = 0x40;
			}
			else
			{
				dst[startIndex] = 0;
			}
			return 1;
		}
		
		int sReq = calcPackReqBytes(null != negative, value);
		
		int off = 0;
		int index = value.length-1;
		
		for(int i = sReq-1;i >=0;--i,--index)
		{
			int val = 0;
			if(0 == off)//value from the same byte
			{
				val = value[index];//bit offset
			}
			else//different bytes
			{
				val = (0xff & value[index+1]) >> (8-off);
				if(index >= 0)
				{
					val |= (0xff & value[index]) << off;
				}
			}
			
			val &= 0x7f;
			
			if(++off == 8)
			{
				off = 0;
				index += 1;
			}
			
			if(i != sReq-1)
			{
				val |= 0x80;
			}
			
			if(Boolean.TRUE == negative && i == 0)
			{
				val |= 0x40;
			}
			
			dst[startIndex+i] = (byte) val;
		}
		
		return sReq;
	}

	public static byte[] packInt(boolean signed, int value)
	{
		byte[] ret = new byte[5];
		int r = packValue(signed, value, ret, 0);
		return Arrays.copyOf(ret, r);
	}
	
	public static BigInteger unpackValue(boolean signed, byte[] data, int startIndex)
	{
		return unpackValue(signed, data, startIndex, null);
	}
	
	public static BigInteger unpackValue(boolean signed, byte[] data, int startIndex, int[] usedBytes)
	{
		int ahead = 0;
		try
		{
			while((data[startIndex+ahead] & 0x80) > 0)
			{
				++ahead;
			}
		}
		catch(ArrayIndexOutOfBoundsException out)
		{
			throw new RuntimeException("Incomplete value in the buffer.");
		}
		
		final int used = ahead;
		BigInteger ret = null;
		
		int cut = 0xff & ~((signed?0x40:0x00) | 0x80);
		
		final byte cut_first = 0x7f;
		
		int req = ((ahead+1)*7)/8+1;
		
		byte[] num = new byte[req];
		
		int off = -1;
		for(int index = num.length-1;index >= 0&& ahead >= 0;--index, --ahead)
		{
			if(7 == ++off)
			{
				off = -1;
				index += 1;
				continue;
			}
			
			int val = ((index != startIndex?cut_first:cut) & data[startIndex+ahead]) >>> off;
			
			if(ahead > 0)
			{
				int prev = ((cut_first & data[startIndex+ahead-1]) << (7-off));
				/*System.out.println
				(
					index+"/"+off+": "+Integer.toBinaryString(prev)
					+" | "+Integer.toBinaryString(val)
				);*/
				val |= prev;
			}
			
			//System.out.println(index+" "+Integer.toBinaryString(val));
			num[index] = (byte) val;
			
			
		}
		
		ret = new BigInteger(num);
		//System.out.println("unpack: "+Arrays.toString(num)+" "+ret);
		
		if(signed && (data[startIndex] & 0x40) > 0)
		{
			ret = ret.negate().subtract(BigInteger.ONE);
		}
		
		if(null != usedBytes && usedBytes.length > 0)
		{
			usedBytes[0] = used+1;
		}
		
		return ret;
	}
	
	public static byte[] getValidPacket(byte[] e)
	{
		if(e.length > 0 && UartbusTools.crc8(e, e.length-1) == e[e.length-1])
		{
			return Arrays.copyOf(e, e.length-1);
		}
		
		return null;
	}
	
	public static boolean isPacketCrc8Valid(byte[] data)
	{
		if(data.length < 1)
		{
			return false;
		}
		return UartbusTools.crc8(data, data.length-1) == data[data.length-1];
	}
	
	public static void printPacketStdout(byte[] data)
	{
		if(0 != data.length)
		{
			boolean valid = UartbusTools.crc8(data, data.length-1) == data[data.length-1];
			System.out.println((valid?"":"!")+UartbusTools.formatColonData(data));
		}
	}

	public static void initConnection(UartbusConnection conn)
	{
		if(null == conn)
		{
			return;
		}
		
		try
		{
			conn.init();
		}
		catch(Throwable t)
		{
			Object str = conn;
			if(Proxy.isProxyClass(conn.getClass()))
			{
				str = "Proxy: "+System.identityHashCode(conn);
			}
			LoggingTools.tryLogFormatException
			(
				LOG,
				LogLevel.NOTICE,
				t,
				"Exception while calling `%s`.call(). This might happened beacuse you connect to and older version of connection implementation.",
				str
			);
		}
	}
	
}
