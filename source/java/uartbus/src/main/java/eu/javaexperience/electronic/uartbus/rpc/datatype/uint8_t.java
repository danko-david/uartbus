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
	
	@Override
	public String toString()
	{
		return "uint8_t: "+value;
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
		if(-128 <= l && l <= 255)
		{
			if(l < 0)
			{
				l = 0xff & l;
			}
			
			return l == value;
		}
		return false;
	}
	
	@Override
	public int hashCode()
	{
		return 0xff & value;
	}
}
