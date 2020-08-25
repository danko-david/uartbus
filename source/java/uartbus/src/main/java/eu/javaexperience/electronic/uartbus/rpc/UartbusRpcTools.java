package eu.javaexperience.electronic.uartbus.rpc;

import java.math.BigInteger;

import eu.javaexperience.electronic.uartbus.rpc.client.types.UbRemoteString;
import eu.javaexperience.electronic.uartbus.rpc.datatype.VUnsigned;

public class UartbusRpcTools
{
	private UartbusRpcTools() {}
	
	public static String loadString(UbRemoteString str)
	{
		return loadString(str, 16);
	}
	
	public static String loadString(UbRemoteString str, int maxGetLength)
	{
		int len = str.getLength().value.intValue();
		
		StringBuilder sb = new StringBuilder();
		
		VUnsigned rlen = new VUnsigned(BigInteger.valueOf(maxGetLength));
		
		while(sb.length() < len)
		{
			String app = new String(str.getStringPart(new VUnsigned(sb.length()), rlen));
			if(0 == app.length() || sb.length() >= len)
			{
				break;
			}
			sb.append(app);
		}
		
		if(sb.length() < len)
		{
			throw new RuntimeException("Can't load the whole string.");
		}
		
		return sb.toString();
	}
}
