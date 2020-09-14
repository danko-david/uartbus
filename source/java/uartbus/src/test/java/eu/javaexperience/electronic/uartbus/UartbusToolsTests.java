package eu.javaexperience.electronic.uartbus;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.math.BigInteger;
import java.util.Arrays;

import org.junit.Test;

import eu.javaexperience.electronic.uartbus.rpc.datatype.VSigned;
import eu.javaexperience.text.StringTools;

public class UartbusToolsTests
{
	@Test
	public void testPackPackUnsignInt1b()
	{
		byte[] data = new byte[1];
		assertEquals(1, UartbusTools.packValue(false, 0, data, 0));
		assertEquals(0b00000000, data[0]);
		
		assertEquals(1, UartbusTools.packValue(false, 63, data, 0));
		assertEquals(0b00111111, data[0]);
		
		assertEquals(1, UartbusTools.packValue(false, 64, data, 0));
		assertEquals(0b01000000, data[0]);
		
		assertEquals(1, UartbusTools.packValue(false, 127, data, 0));
		assertEquals(0b01111111, data[0]);
		
		assertEquals(1, UartbusTools.packValue(false, 0b01010101, data, 0));
		assertEquals(0b01010101, data[0]);
		
		assertEquals(1, UartbusTools.packValue(false, 0b00101010, data, 0));
		assertEquals(0b00101010, data[0]);
	}
	
	@Test
	public void testPackPackUnsignInt2b()
	{
		byte[] data = new byte[2];
		assertEquals(2, UartbusTools.packValue(false, 128, data, 0));
		assertEquals((byte) 0b10000001, data[0]);
		assertEquals((byte) 0b00000000, data[1]);
		
		assertEquals(2, UartbusTools.packValue(false, 255, data, 0));
		assertEquals((byte) 0b10000001, data[0]);
		assertEquals((byte) 0b01111111, data[1]);
		
		assertEquals(2, UartbusTools.packValue(false, 256, data, 0));
		assertEquals((byte) 0b10000010, data[0]);
		assertEquals((byte) 0b00000000, data[1]);
		
		assertEquals(2, UartbusTools.packValue(false, 16383, data, 0));
		assertEquals((byte) 0b11111111, data[0]);
		assertEquals((byte) 0b01111111, data[1]);
		
		assertEquals(2, UartbusTools.packValue(false, 0b00010101_01010101, data, 0));
		assertEquals((byte) 0b10101010, data[0]);
		assertEquals((byte) 0b01010101, data[1]);
		
		assertEquals(2, UartbusTools.packValue(false, 0b0001010_10101010, data, 0));
		assertEquals((byte) 0b10010101, data[0]);
		assertEquals((byte) 0b00101010, data[1]);
	}
	
	@Test
	public void testPackPackUnsignInt3b()
	{
		byte[] data = new byte[3];
		assertEquals(3, UartbusTools.packValue(false, 16384, data, 0));
		assertEquals((byte) 0b10000001, data[0]);
		assertEquals((byte) 0b10000000, data[1]);
		assertEquals((byte) 0b00000000, data[2]);
		
		assertEquals(3, UartbusTools.packValue(false, 16385, data, 0));
		assertEquals((byte) 0b10000001, data[0]);
		assertEquals((byte) 0b10000000, data[1]);
		assertEquals((byte) 0b00000001, data[2]);
		
		assertEquals(3, UartbusTools.packValue(false, 1048576, data, 0));
		assertEquals((byte) 0b11000000, data[0]);
		assertEquals((byte) 0b10000000, data[1]);
		assertEquals((byte) 0b00000000, data[2]);
		
		assertEquals(3, UartbusTools.packValue(false, 2097151, data, 0));
		assertEquals((byte) 0b11111111, data[0]);
		assertEquals((byte) 0b11111111, data[1]);
		assertEquals((byte) 0b01111111, data[2]);
	}
	
	@Test
	public void testFirstUnsignedOfSeries()
	{
		testFirstUnsignedOfLength(2);
		testFirstUnsignedOfLength(3);
		testFirstUnsignedOfLength(4);
		testFirstUnsignedOfLength(5);
		testFirstUnsignedOfLength(6);
		testFirstUnsignedOfLength(7);
		testFirstUnsignedOfLength(8);
		testFirstUnsignedOfLength(9);
		testFirstUnsignedOfLength(10);
		testFirstUnsignedOfLength(11);
		testFirstUnsignedOfLength(12);
		testFirstUnsignedOfLength(31);
		testFirstUnsignedOfLength(32);
		testFirstUnsignedOfLength(58);
		testFirstUnsignedOfLength(59);
		testFirstUnsignedOfLength(63);
		testFirstUnsignedOfLength(64);
		testFirstUnsignedOfLength(1023);
		testFirstUnsignedOfLength(1024);
		testFirstUnsignedOfLength(1025);
		testFirstUnsignedOfLength(4095);
		testFirstUnsignedOfLength(4096);
	}
	
	public static void testFirstUnsignedOfLength(int n)
	{
		byte[] data = new byte[n];
		BigInteger val = BigInteger.valueOf(2);
		val = val.pow(7*(n-1));
		
		//System.out.println("("+n+") 2^"+(7*(n-1))+" => "+val+" "+Arrays.toString(val.toByteArray()));
		
		assertEquals(n, UartbusTools.packValue(false, val, data, 0));
		for(int i=0;i<n;++i)
		{
			if(0 == i)
			{
				assertEquals((byte) 0x81, data[i]);
			}
			else if(i == n-1)
			{
				assertEquals(0x0, data[i]);
			}
			else
			{
				assertEquals((byte) 0x80, data[i]);				
			}
		}
	}
	
	@Test
	public void testLastUnsignedOfSeries()
	{
		testLastUnsignedOfLength(2);
		testLastUnsignedOfLength(3);
		testLastUnsignedOfLength(4);
		testLastUnsignedOfLength(5);
		testLastUnsignedOfLength(6);
		testLastUnsignedOfLength(7);
		testLastUnsignedOfLength(8);
		testLastUnsignedOfLength(9);
		testLastUnsignedOfLength(10);
		testLastUnsignedOfLength(11);
		testLastUnsignedOfLength(12);
		testLastUnsignedOfLength(63);
		testLastUnsignedOfLength(64);
		testLastUnsignedOfLength(1023);
		testLastUnsignedOfLength(1024);
		testLastUnsignedOfLength(1025);
		testFirstUnsignedOfLength(4095);
		testFirstUnsignedOfLength(4096);
	}
	
	public static void testLastUnsignedOfLength(int n)
	{
		byte[] data = new byte[n];
		BigInteger val = BigInteger.valueOf(2);
		val = val.pow(7*n);
		val = val.subtract(BigInteger.valueOf(1));
		assertEquals(n, UartbusTools.packValue(false, val, data, 0));
		for(int i=0;i<n;++i)
		{
			if(i != n-1)
			{
				assertEquals((byte) 0xff, data[i]);
			}
			else
			{
				assertEquals((byte)~0x80, data[i]);
			}
		}
	}
	
	@Test
	public void testPackPackSignedInt1b()
	{
		byte[] data = new byte[1];
		assertEquals(1, UartbusTools.packValue(true, 0, data, 0));
		assertEquals(0b00000000, data[0]);
		
		assertEquals(1, UartbusTools.packValue(true, -1, data, 0));
		assertEquals(0b01000000, data[0]);
		
		assertEquals(1, UartbusTools.packValue(true, -64, data, 0));
		assertEquals(0b01111111, data[0]);
		
		assertEquals(1, UartbusTools.packValue(true, 63, data, 0));
		assertEquals(0b00111111, data[0]);
	}

	@Test
	public void testPackPackSignedInt2b()
	{
		byte[] data = new byte[2];
		assertEquals(2, UartbusTools.packValue(true, 128, data, 0));
		assertEquals((byte) 0b10000001, data[0]);
		assertEquals((byte) 0b00000000, data[1]);
		
		assertEquals(2, UartbusTools.packValue(true, -129, data, 0));
		assertEquals((byte) 0b11000001, data[0]);
		assertEquals((byte) 0b00000000, data[1]);
		
		assertEquals(2, UartbusTools.packValue(true, 256, data, 0));
		assertEquals((byte) 0b10000010, data[0]);
		assertEquals((byte) 0b00000000, data[1]);
		
		assertEquals(2, UartbusTools.packValue(true, -257, data, 0));
		assertEquals((byte) 0b11000010, data[0]);
		assertEquals((byte) 0b00000000, data[1]);
		
		assertEquals(2, UartbusTools.packValue(true, 8191, data, 0));
		assertEquals((byte) 0b10111111, data[0]);
		assertEquals((byte) 0b01111111, data[1]);
		
		assertEquals(2, UartbusTools.packValue(true, -8192, data, 0));
		assertEquals((byte) 0b11111111, data[0]);
		assertEquals((byte) 0b01111111, data[1]);
	}
	
	@Test
	public void testPackPackSignedInt3b()
	{
		byte[] data = new byte[3];
		assertEquals(3, UartbusTools.packValue(true, 16384, data, 0));
		assertEquals((byte) 0b10000001, data[0]);
		assertEquals((byte) 0b10000000, data[1]);
		assertEquals((byte) 0b00000000, data[2]);
		
		assertEquals(3, UartbusTools.packValue(true, -16385, data, 0));
		assertEquals((byte) 0b11000001, data[0]);
		assertEquals((byte) 0b10000000, data[1]);
		assertEquals((byte) 0b00000000, data[2]);
		
		assertEquals(3, UartbusTools.packValue(true, -524289, data, 0));
		assertEquals((byte) 0b11100000, data[0]);
		assertEquals((byte) 0b10000000, data[1]);
		assertEquals((byte) 0b00000000, data[2]);
		
		assertEquals(3, UartbusTools.packValue(true, 524288, data, 0));
		assertEquals((byte) 0b10100000, data[0]);
		assertEquals((byte) 0b10000000, data[1]);
		assertEquals((byte) 0b00000000, data[2]);
	}
	
	@Test
	public void testPackUnpackUnsignInt1b()
	{
		assertEquals(0, UartbusTools.unpackValue(false, new byte[]{0b00000000}, 0).longValue());
		assertEquals(63, UartbusTools.unpackValue(false, new byte[]{0b00111111}, 0).longValue());
		assertEquals(64, UartbusTools.unpackValue(false, new byte[]{0b01000000}, 0).longValue());
		assertEquals(127, UartbusTools.unpackValue(false, new byte[]{0b01111111}, 0).longValue());
	}
	
	@Test
	public void testPackUnpackUnsignInt2b()
	{
		assertEquals(128, UartbusTools.unpackValue(false, new byte[]{(byte) 0b10000001, 0b00000000}, 0).longValue());
		assertEquals(255, UartbusTools.unpackValue(false, new byte[]{(byte) 0b10000001, 0b01111111}, 0).longValue());
		assertEquals(256, UartbusTools.unpackValue(false, new byte[]{(byte) 0b10000010, 0b00000000}, 0).longValue());
		assertEquals(16383, UartbusTools.unpackValue(false, new byte[]{(byte) 0b11111111, 0b01111111}, 0).longValue());
	}
	
	@Test
	public void testPackUnpackUnsignInt3b()
	{
		assertEquals(16384, UartbusTools.unpackValue(false, new byte[]{(byte) 0b10000001, (byte) 0b10000000, 0b00000000}, 0).longValue());
		assertEquals(16385, UartbusTools.unpackValue(false, new byte[]{(byte) 0b10000001, (byte) 0b10000000, 0b00000001}, 0).longValue());
		assertEquals(1048576, UartbusTools.unpackValue(false, new byte[]{(byte) 0b11000000, (byte) 0b10000000, 0b00000000}, 0).longValue());
		assertEquals(2097151, UartbusTools.unpackValue(false, new byte[]{(byte) 0b11111111, (byte) 0b11111111, 0b01111111}, 0).longValue());
	}
	
	@Test
	public void testPackUnpackSignedInt1b()
	{
		assertEquals(0, UartbusTools.unpackValue(true, new byte[]{0b00000000}, 0).longValue());
		assertEquals(-1, UartbusTools.unpackValue(true, new byte[]{0b01000000}, 0).longValue());
		assertEquals(-64, UartbusTools.unpackValue(true, new byte[]{0b01111111}, 0).longValue());
		assertEquals(63, UartbusTools.unpackValue(true, new byte[]{0b00111111}, 0).longValue());
	}

	@Test
	public void testPackUnpackSignedInt2b()
	{
		assertEquals(128, UartbusTools.unpackValue(true, new byte[]{(byte) 0b10000001, 0b00000000}, 0).longValue());
		assertEquals(-129, UartbusTools.unpackValue(true, new byte[]{(byte) 0b11000001, 0b00000000}, 0).longValue());
		assertEquals(256, UartbusTools.unpackValue(true, new byte[]{(byte) 0b10000010, 0b00000000}, 0).longValue());
		assertEquals(-257, UartbusTools.unpackValue(true, new byte[]{(byte) 0b11000010, 0b00000000}, 0).longValue());
		assertEquals(8191, UartbusTools.unpackValue(true, new byte[]{(byte) 0b10111111, 0b01111111}, 0).longValue());
		assertEquals(-8192, UartbusTools.unpackValue(true, new byte[]{(byte) 0b11111111, 0b01111111}, 0).longValue());
	}
	
	@Test
	public void testPackUnpackSignedInt3b()
	{
		assertEquals(16384, UartbusTools.unpackValue(true, new byte[]{(byte) 0b10000001, (byte) 0b10000000, 0b00000000}, 0).longValue());
		assertEquals(-16385, UartbusTools.unpackValue(true, new byte[]{(byte) 0b11000001, (byte) 0b10000000, 0b00000000}, 0).longValue());
		assertEquals(524288, UartbusTools.unpackValue(true, new byte[]{(byte) 0b10100000, (byte) 0b10000000, 0b00000000}, 0).longValue());
		assertEquals(-524289, UartbusTools.unpackValue(true, new byte[]{(byte) 0b11100000, (byte) 0b10000000, 0b00000000}, 0).longValue());
		assertEquals(-532481, UartbusTools.unpackValue(true, new byte[]{(byte) 0b11100000, (byte) 0b11000000, 0b00000000}, 0).longValue());
	}
	
	public static void dbgPrintBytes(BigInteger val)
	{
		System.out.print(val+": ");
		byte[] b = val.toByteArray();
		for(int i=0;i<b.length;++i)
		{
			String txt = Integer.toBinaryString(0xff & b[i]);
			System.out.print(" "+txt+StringTools.repeatChar('0', 8-txt.length()));
		}
		
		System.out.println();
	}
	
	public static void testPackUnpack(boolean signed, String value)
	{
		byte[] data = new byte[value.length()];
		BigInteger val = new BigInteger(value);
		int bytes = UartbusTools.packValue(signed, val, data, 0);
		data = Arrays.copyOf(data, bytes);
		BigInteger up = UartbusTools.unpackValue(signed, data, 0);
		/*System.out.print("before: ");
		dbgPrintBytes(val);
		System.out.print("after: ");
		dbgPrintBytes(up);
		System.out.println();*/
		assertEquals(val, up);
	}
	
	@Test
	public void testUnsignedValues()
	{
		testPackUnpack(true, "1024");
		testPackUnpack(true, "95349643593278962348975692345932456");
		testPackUnpack(true, "875423123432230121024332421223");
	}
	
	@Test
	public void testSignedValues()
	{
		
		testPackUnpack(true, "95349643593278962348975692345932456");
		testPackUnpack(true, "875423123432230121024332421223");
		
		testPackUnpack(true, "-95349643593278962348975692345932456");
		testPackUnpack(true, "-875423123432230121024332421223");
		
		testPackUnpack(true, "1024");
	}
	
	
	@Test
	public void testLongPack_min()
	{
		byte[] data = new byte[10];
		UartbusTools.packValue(true, new BigInteger(String.valueOf(Long.MIN_VALUE)), data, 0);
		assertArrayEquals
		(
			new byte[]{-64, -1, -1, -1, -1, -1, -1, -1, -1, 127},
			data
		);
	}
	
	@Test
	public void testLongPack_max()
	{
		byte[] data = new byte[10];
		UartbusTools.packValue(true, new BigInteger(String.valueOf(Long.MAX_VALUE)), data, 0);
		assertArrayEquals
		(
			new byte[]{-128, -1, -1, -1, -1, -1, -1, -1, -1, 127},
			data
		);
	}
	
	
	@Test
	public void testLongUnpack_min()
	{
		assertEquals
		(
			new BigInteger(String.valueOf(Long.MIN_VALUE)),
			UartbusTools.unpackValue(true, new byte[]{-64, -1, -1, -1, -1, -1, -1, -1, -1, 127}, 0)
		);
	}
	
	@Test
	public void testLongUnpack_max()
	{
		assertEquals
		(
			new BigInteger(String.valueOf(Long.MAX_VALUE)),
			UartbusTools.unpackValue(true, new byte[]{-128, -1, -1, -1, -1, -1, -1, -1, -1, 127}, 0)
		);
	}
}
