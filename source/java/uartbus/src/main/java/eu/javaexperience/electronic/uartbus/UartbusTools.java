package eu.javaexperience.electronic.uartbus;

public class UartbusTools
{
	private UartbusTools() {}
	
	public static byte crc8(byte[] data)
	{
		return crc8(data, data.length);
	}
	
	public static byte crc8(byte[] data, int length)
	{
		byte crc = 0;
		byte v;
		int i;
		
		for(i=0;i<length;++i)
		{
			v = (byte) ((data[i] ^ crc) & 0xff); 
			crc = 0;
			if((v & 1) != 0)
				crc ^= 0x5e; 
			if((v & 2) != 0)
				crc ^= 0xbc; 
			if((v & 4) != 0)
				crc ^= 0x61; 
			if((v & 8) != 0)
				crc ^= 0xc2; 
			if((v & 0x10) != 0)
				crc ^= 0x9d; 
			if((v & 0x20) != 0)
				crc ^= 0x23; 
			if((v & 0x40) != 0)
				crc ^= 0x46; 
			if((v & 0x80) != 0)
				crc ^= 0x8c;
		}

		return crc;
	}
}
