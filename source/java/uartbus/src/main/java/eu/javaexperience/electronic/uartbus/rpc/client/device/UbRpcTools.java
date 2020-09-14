package eu.javaexperience.electronic.uartbus.rpc.client.device;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import eu.javaexperience.electronic.uartbus.PacketAssembler;
import eu.javaexperience.electronic.uartbus.PacketReader;
import eu.javaexperience.electronic.uartbus.UartbusTools;
import eu.javaexperience.electronic.uartbus.rpc.client.types.UbRemoteString;
import eu.javaexperience.electronic.uartbus.rpc.datatype.VSigned;
import eu.javaexperience.electronic.uartbus.rpc.datatype.VUnsigned;
import eu.javaexperience.electronic.uartbus.rpc.datatype.uint16_t;
import eu.javaexperience.electronic.uartbus.rpc.datatype.uint8_t;
import eu.javaexperience.electronic.uartbus.rpc.service.UartbusPacketDispatch;
import eu.javaexperience.interfaces.simple.getBy.GetBy1;
import eu.javaexperience.nativ.posix.ERRNO;
import eu.javaexperience.nativ.posix.PosixErrnoException;
import eu.javaexperience.reflect.Mirror;
import eu.javaexperience.reflect.PrimitiveTools;
import eu.javaexperience.resource.pool.IssuedResource;
import eu.javaexperience.resource.pool.TrackedLimitedResourcePool;
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
		
		Object ret = readType(pr, retc, method.getGenericReturnType());
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
	
	protected static Object[] extractStruct(Object o)
	{
		String n = StringTools.getSubstringAfterFirstString(o.getClass().getName(), "eu.javaexperience.struct.GenericStruct", null);
		if(null == n)
		{
			return null;
		}
		
		Integer p = Integer.parseInt(n);
		Object[] ret = new Object[p];
		try
		{
			for(int i=0;i<p;++i)
			{
				Field f = Mirror.getClassFieldOrNull(o.getClass(), String.valueOf(((char)('a'+i))));
				ret[i] = f.get(o);
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
		return readType(reader, reqType, null);
	}
	
	public static Object readType(PacketReader reader, Class reqClass, Type reqType)
	{
		reqClass = PrimitiveTools.toObjectClassType(reqClass, reqClass);
		
		if(reqClass == Boolean.class)
		{
			return 0 == reader.readSByte()? Boolean.FALSE:Boolean.TRUE;
		}
		else if(reqClass == Byte.class)
		{
			return reader.readSByte();
		}
		else if(reqClass == Character.class)
		{
			return (char) reader.readSByte();
		}
		else if(reqClass == Short.class)
		{
			return reader.readSShort();
		}
		else if(reqClass == Integer.class)
		{
			return reader.readSInt();
		}
		else if(reqClass == Long.class)
		{
			return reader.readSLong();
		}
		else if(reqClass == Float.class)
		{
			return reader.readFloat();
		}
		else if(reqClass == Double.class)
		{
			return reader.readDouble();
		}
		else if(reqClass == String.class)
		{
			return reader.readString();
		}
		else if(reqClass == byte[].class)
		{
			return reader.readBlobRemain();
		}
		else if(reqClass == VSigned.class)
		{
			return new VSigned(reader.readVsNumber());
		}
		else if(reqClass == VUnsigned.class)
		{
			return new VUnsigned(reader.readVuNumber());
		}
		else if(reqClass == uint8_t.class)
		{
			return new uint8_t(reader.readUByte());
		}
		else if(reqClass == uint16_t.class)
		{
			return new uint16_t(reader.readUShort());
		}
		else if
		(
			reqClass.getCanonicalName().startsWith("eu.javaexperience.struct.GenericStruct")
			&&
			null != reqType
		)
		{
			return tryExtractStruct(reqClass, reqType, reader);
		}
		else if(reqClass.isArray())
		{
			List ret = new ArrayList(); 
			while(reader.hasUnprocessedBytes())
			{
				ret.add(readType(reader, reqClass.getComponentType()));
			}
			
			return ret.toArray((Object[]) Array.newInstance(reqClass.getComponentType(), 1));
		}
		else if(reqClass.isEnum())
		{
			return reqClass.getEnumConstants()[(int) reader.readVuint()];
		}
		
		return null;
	}
	
	public static boolean isUartbusDataType(Class o)
	{
		return
			Boolean.class.isAssignableFrom(o) ||
			Byte.class.isAssignableFrom(o) ||
			uint8_t.class.isAssignableFrom(o) ||
			uint16_t.class.isAssignableFrom(o) ||
			Short.class.isAssignableFrom(o) ||
			byte[].class.isAssignableFrom(o) ||
			Number.class.isAssignableFrom(o) ||
			String.class.isAssignableFrom(o) ||
			VSigned.class.isAssignableFrom(o) ||
			VUnsigned.class.isAssignableFrom(o) ||
			o.isEnum()||
			o.getCanonicalName().startsWith("eu.javaexperience.struct.GenericStruct")||
			(o.isArray() && isUartbusDataType(o.getComponentType()))
		;
	}
	
	public static boolean isUartbusDataType(Object o)
	{
		return isUartbusDataType(o.getClass());
	}
	
	public static void appendElements(PacketAssembler pa, Object... elements) throws IOException
	{
		for(Object o:elements)
		{
			if(null == o)
			{
				throw new NullPointerException("Can't serialize null");
			}
			else if(o instanceof Boolean)
			{
				pa.writeByte((byte)((Boolean.FALSE.equals(o))?0:1));
			}
			else if(o instanceof Byte)
			{
				pa.writeByte((Byte) o);
			}
			else if(o instanceof uint8_t)
			{
				pa.writeByte((((uint8_t)o).value & 0xff));
			}
			else if(o instanceof uint16_t)
			{
				pa.writeShort((((uint16_t)o).value));
			}
			//note: if you miss int8_t and int16_t it is because are the same as byte and short
			else if(o instanceof Short)
			{
				pa.writeShort((Short)o);
			}
			else if(o instanceof byte[])
			{
				pa.write((byte[]) o);
			}
			else if(o instanceof String)
			{
				pa.writeString(o.toString());
			}
			else if(o instanceof VSigned)
			{
				pa.writePackedValue(true, ((VSigned) o).value);
			}
			else if(o instanceof VUnsigned)
			{
				pa.writePackedValue(true, ((VUnsigned) o).value);
			}
			
			//keep this in clast case from the type of numbers
			else if(o instanceof Number)
			{
				pa.writePackedValue(true, (Number) o);
			}
			else if(o.getClass().isEnum())
			{
				pa.write(UartbusTools.packInt(false, ((Enum)o).ordinal()));
			}
			else if
			(
				o.getClass().getCanonicalName().startsWith("eu.javaexperience.struct.GenericStruct")
			)
			{
				//serialize all value
				appendElements(pa, extractStruct(o));
			}
			else if(o.getClass().isArray())
			{
				int len = Array.getLength(o);
				for(int i=0;i<len;++i)
				{
					Object add = Array.get(o, i);
					appendElements(pa, add);
				}
			}
			else
			{
				throw new RuntimeException("Can't serialize packet component ("+(null == o?"null":o.getClass())+"): "+o);
			}
		}
	}
	
	protected static final TrackedLimitedResourcePool<PacketAssembler> PA_POOL = new TrackedLimitedResourcePool<PacketAssembler>(()->new PacketAssembler(), 1024);
	
	public static byte[] toPacket(int to, int from, Object... elements)
	{
		try(IssuedResource<PacketAssembler> res = PA_POOL.acquireResource())
		{
			PacketAssembler pa = res.getResource();
			pa.writeAddressing(from, to);
			appendElements(pa, elements);
			
			pa.appendCrc8();
			return pa.done();
		}
		catch(Exception e)
		{
			Mirror.propagateAnyway(e);
			return null;
		}
	}

	public static GetBy1<Boolean, UartbusPacketDispatch> createRequestPacketAcceptor
	(
		Integer targetAddress,
		byte[] path
	)
	{
		return p->
		{
			if
			(
				null != targetAddress
				&&
				!targetAddress.equals(p.getPacket().to)
			)
			{
				return false;
			}
			
			if
			(
				null != path
			&&
				0 != path.length
			)
			{
				byte[] pl = p.getPayload();
				if(pl.length < path.length)
				{
					return false;
				}
				for(int i=0;i<path.length;++i)
				{
					if(path[i] != pl[i])
					{
						return false;
					}
				}
				
				p.setDispatchByteIndex(path.length);
			}
			
			return true;
		};
	}
	
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
