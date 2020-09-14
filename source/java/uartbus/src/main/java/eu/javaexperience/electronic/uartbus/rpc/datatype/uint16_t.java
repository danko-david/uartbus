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
	
	@Override
	public String toString()
	{
		return "uint16_t: "+value;
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if(!(obj instanceof Number))
		{
			return false;
		}
		
		Number n = (Number) obj;
		
		double d = n.doubleValue();

		//check number is integer (i mean number without fraction)
		if(Math.floor(d) != d)
		{
			return false;
		}
		
		long l = n.longValue();
		
		//btw -128 and 127 or 0 and 255
		if(Short.MIN_VALUE <= l && l <= 65536)
		{
			if(l < 0)
			{
				l = 0xffff & l;
			}
			
			return l == value;
		}
		return false;
	}
	
	@Override
	public int hashCode()
	{
		return 0xffff & value;
	}
}
