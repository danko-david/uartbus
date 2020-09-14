package eu.javaexperience.electronic.uartbus.rpc.datatype;

import java.math.BigInteger;

public final class VSigned
{
	public final BigInteger value;
	public VSigned(BigInteger dec)
	{
		this.value = dec;
	}
	
	public VSigned(long dec)
	{
		this.value = BigInteger.valueOf(dec);
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if(!(obj instanceof VSigned))
		{
			return false;
		}
		
		return value.equals(((VSigned)obj).value);
	}
	
	@Override
	public int hashCode()
	{
		return value.hashCode();
	}
	
	@Override
	public String toString()
	{
		return "VSigned: "+value;
	}
}
