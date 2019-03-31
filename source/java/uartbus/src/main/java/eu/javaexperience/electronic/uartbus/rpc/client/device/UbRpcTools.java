package eu.javaexperience.electronic.uartbus.rpc.client.device;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import eu.javaexperience.electronic.uartbus.PacketReader;
import eu.javaexperience.nativ.posix.ERRNO;
import eu.javaexperience.nativ.posix.PosixErrnoException;
import eu.javaexperience.reflect.Mirror;
import eu.javaexperience.reflect.PrimitiveTools;
import eu.javaexperience.text.StringTools;

public class UbRpcTools
{
	public static Object extractOrThrowResult(Method method, byte[] bs) throws PosixErrnoException
	{
		boolean posix = false;
		
		PacketReader pr = new PacketReader(bs);
		
		for(Class c:method.getExceptionTypes())
		{
			if(PosixErrnoException.class.isAssignableFrom(c))
			{
				posix = true;
				break;
			}
		}
		
		if(posix)
		{
			short err = pr.readUByte();
			if(0 != err)
			{
				throw new PosixErrnoException(ERRNO.ERRNOOfValue(err));
			}
		}
		
		Class<?> retc = method.getReturnType();
		
		if(Void.class == retc || void.class == retc)
		{
			return null;
		}
		
		Object ret = tryExtractStruct(retc, method.getGenericReturnType(), pr);
		if(null != ret)
		{
			return ret;
		}
		
		ret = readType(pr, retc);
		if(null != ret)
		{
			return ret;
		}
		
		throw new RuntimeException("Unknown, unextractable returning type: "+method);
	}
	
	protected static Object tryExtractStruct(Class c, Type t, PacketReader pr)
	{
		String n = StringTools.getSubstringAfterFirstString(c.getName(), "eu.javaexperience.struct.GenericStruct", null);
		if(null == n)
		{
			return null;
		}
		
		Integer p = Integer.parseInt(n);
		
		try
		{
			ParameterizedType pt = (ParameterizedType) t;
			Type[] ts = pt.getActualTypeArguments();
			Object ret = c.newInstance();
			
			for(int i=0;i<p;++i)
			{
				Field f = Mirror.getClassFieldOrNull(c, String.valueOf(((char)('a'+i))));
				if(null != f)
				{
					f.set(ret, readType(pr, Mirror.extracClass(ts[i])));
				}
			}
			
			return ret;
		}
		catch(Exception e)
		{
			Mirror.propagateAnyway(e);
			return null;
		}
	}
	
	public static Object readType(PacketReader reader, Class reqType)
	{
		reqType = PrimitiveTools.toObjectClassType(reqType, reqType);
		
		if(reqType == Boolean.class)
		{
			return 0 == reader.readSByte()? Boolean.FALSE:Boolean.TRUE;
		}
		else if(reqType == Byte.class)
		{
			return reader.readSByte();
		}
		else if(reqType == Character.class)
		{
			return (char) reader.readSByte();
		}
		else if(reqType == Short.class)
		{
			return reader.readSShort();
		}
		else if(reqType == Integer.class)
		{
			return reader.readSInt();
		}
		else if(reqType == Long.class)
		{
			return reader.readSLong();
		}
		else if(reqType == Float.class)
		{
			return reader.readFloat();
		}
		else if(reqType == Double.class)
		{
			return reader.readDouble();
		}
		else if(reqType == String.class)
		{
			return reader.readString();
		}
		else if(reqType == byte[].class)
		{
			return reader.readBlobRemain();
		}
		else if(reqType  == VSigned.class)
		{
			return new VSigned(reader.readVsNumber());
		}
		else if(reqType  == VUnsigned.class)
		{
			return new VUnsigned(reader.readVuNumber());
		}
		
		return null;
	}
	
}
