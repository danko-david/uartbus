package eu.javaexperience.electronic.uartbus.rpc.datatype;

public class uint8_t extends Number
{
	public final short value;
	
	public uint8_t(short value)
	{
		this.value = value;
	}
	
	public uint8_t(Number value)
	{
		this.value = value.shortValue();
	}

	@Override
	public int intValue()
	{
		return value;
	}

	@Override
	public long longValue()
	{
		return value;
	}

	@Override
	public float floatValue()
	{
		return value;
	}

	@Override
	public double doubleValue()
	{
		return value;
	}
}
