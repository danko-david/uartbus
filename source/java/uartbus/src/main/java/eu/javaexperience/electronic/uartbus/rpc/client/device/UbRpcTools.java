package eu.javaexperience.electronic.uartbus.rpc.client.device;

import java.lang.reflect.Type;

public class UbRpcTools
{
	public static Object extractOrThrowResult(Type retType, byte[] bs)
	{
		if(retType == byte.class || retType == Byte.class)
		{
			return bs[0];
		}
		
		//TODO
		return null;
	}
}
