package eu.javaexperience.electronic.uartbus.rpc.datatype;

public class uint16_t extends Number
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public final int value;
	
	public uint16_t(short value)
	{
		this.value = value;
	}
	
	public uint16_t(Number value)
	{
		this.value = value.intValue();
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
