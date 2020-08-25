package eu.javaexperience.electronic.uartbus.rpc.datatype;

import java.math.BigInteger;

public final class VSigned
{
	public final BigInteger value;
	public VSigned(BigInteger dec)
	{
		this.value = dec;
	}
}
