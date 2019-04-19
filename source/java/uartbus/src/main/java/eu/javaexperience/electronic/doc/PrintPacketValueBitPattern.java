package eu.javaexperience.electronic.doc;

import eu.javaexperience.electronic.uartbus.UartbusTools;

public class PrintPacketValueBitPattern
{
	public static void printPattern(long val, boolean signed)
	{
		byte[] data = new byte[10];
		int len = UartbusTools.packValue(signed, val, data, 0);
		StringBuilder sb = new StringBuilder();
		sb.append(val);
		sb.append("\t");
		for(int i=0;i<len;++i)
		{
			if(0 != i)
			{
				sb.append("   ");
			}
			String s = Integer.toBinaryString(data[i] & 0xff);
			for(int p=s.length();p<8;++p)
			{
				sb.append("0");
			}
			sb.append(s);
		}
		
		System.out.println(sb);
	}
	
	public static void main(String[] args)
	{
		printPattern(0, true);
		printPattern(-1, true);
		printPattern(16, true);
		printPattern(-16, true);
		printPattern(-64, true);
		printPattern(63, true);
		
		printPattern(128, true);
		printPattern(-128, true);
		
		printPattern(8191, true);
		printPattern(-8192, true);

		printPattern(524288, true);
		printPattern(-524289, true);
		
		
		printPattern(1048575, true);
		printPattern(-1048576, true);
		
	}
}
