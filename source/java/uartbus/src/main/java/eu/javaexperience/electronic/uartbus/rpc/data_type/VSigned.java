package eu.javaexperience.electronic.uartbus.rpc.data_type;

import java.math.BigInteger;

public final class VSigned
{
	public final BigInteger value;
	public VSigned(BigInteger dec)
	{
		this.value = dec;
	}
}
