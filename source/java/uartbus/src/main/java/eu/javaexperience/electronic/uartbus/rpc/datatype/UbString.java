package eu.javaexperience.electronic.uartbus.rpc.datatype;

import java.math.BigInteger;
import java.util.Arrays;

import eu.javaexperience.electronic.uartbus.rpc.client.device.UbDeviceNs;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbIndex;
import eu.javaexperience.electronic.uartbus.rpc.client.types.UbRemoteString;
import eu.javaexperience.electronic.uartbus.rpc.datatype.VUnsigned;

public class UbString implements UbRemoteString
{
	public final String string;
	
	protected VSigned length;
	protected byte[] bytes;
	
	public UbString(String string)
	{
		this.string = string;
		this.bytes = string.getBytes();
		this.length = new VSigned(BigInteger.valueOf(bytes.length));
	}
	
	@UbIndex(ns=1)
	public VSigned getLength()
	{
		return length;
	}
	
	@UbIndex(ns=2)
	public byte[] getStringPart(VUnsigned from, VUnsigned length)
	{
		long f = from.value.longValue();
		long t = length.value.longValue();
		t += f;
		f = Math.min(f, bytes.length);
		t = Math.min(t, bytes.length);
		
		return Arrays.copyOfRange(bytes, (int) f, (int) t);
	}

	@Override
	public <T extends UbDeviceNs> T cast(Class<T> dst)
	{
		return null;
	}

	@Override
	public <T extends UbDeviceNs> T customNs(Class<T> dst, short num)
	{
		return null;
	}
}