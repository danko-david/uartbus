package eu.javaexperience.electronic.uartbus.rpc.datatype;

import java.math.BigInteger;

public final class VUnsigned
{
	public final BigInteger value;
	
	public VUnsigned(String dec)
	{
		this(new BigInteger(dec));
	}
	
	public VUnsigned(BigInteger dec)
	{
		if(dec.signum() < 0)
		{
			throw new IllegalArgumentException("The given number is signed: "+dec);
		}
		this.value = dec;
	}

	public VUnsigned(int value)
	{
		this(BigInteger.valueOf(value));
	}
	
	public VUnsigned(long value)
	{
		this(BigInteger.valueOf(value));
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if(!(obj instanceof VUnsigned))
		{
			return false;
		}
		
		return value.equals(((VUnsigned)obj).value);
	}
	
	@Override
	public int hashCode()
	{
		return value.hashCode();
	}
	
	@Override
	public String toString()
	{
		return "VUnsigned: "+value;
	}
}

