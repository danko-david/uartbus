package eu.javaexperience.electronic.uartbus.rpc.datatype;

import java.math.BigInteger;

public final class VUnsigned
{
	public final BigInteger value;
	
	public VUnsigned(BigInteger dec)
	{
		this.value = dec;
	}

	public VUnsigned(int length)
	{
		this(BigInteger.valueOf(length));
	}
}

