package eu.javaexperience.electronic.uartbus.rpc.types;

import java.math.BigInteger;
import java.util.Arrays;

import eu.javaexperience.electronic.uartbus.rpc.client.device.UbIndex;
import eu.javaexperience.electronic.uartbus.rpc.client.device.VUnsigned;

public class UbString
{
	public final String string;
	
	protected VUnsigned length;
	protected byte[] bytes;
	
	public UbString(String string)
	{
		this.string = string;
		this.bytes = string.getBytes();
		this.length = new VUnsigned(BigInteger.valueOf(bytes.length));
	}
	
	@UbIndex(ns=1)
	public VUnsigned getLength()
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
		
		return Arrays.copyOfRange(bytes, (int) t, (int) f);
	}
}