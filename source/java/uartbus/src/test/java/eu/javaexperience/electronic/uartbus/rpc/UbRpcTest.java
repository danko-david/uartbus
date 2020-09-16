package eu.javaexperience.electronic.uartbus.rpc;


import static org.junit.Assert.assertArrayEquals;

import java.math.BigDecimal;
import java.math.BigInteger;

import eu.javaexperience.electronic.uartbus.UartbusTestTools;
import eu.javaexperience.electronic.uartbus.rpc.client.UartBus;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UartBusDevice;
import eu.javaexperience.electronic.uartbus.rpc.datatype.VSigned;
import eu.javaexperience.electronic.uartbus.rpc.datatype.VUnsigned;
import eu.javaexperience.electronic.uartbus.rpc.datatype.uint16_t;
import eu.javaexperience.electronic.uartbus.rpc.datatype.uint8_t;
import eu.javaexperience.electronic.uartbus.rpc.service.UartbusServiceBridge;
import eu.javaexperience.electronic.uartbus.rpc.virtual.VirtualUartBus;
import eu.javaexperience.io.IOTools;
import eu.javaexperience.log.JavaExperienceLoggingFacility;
import eu.javaexperience.log.LogLevel;
import eu.javaexperience.nativ.posix.ERRNO;
import eu.javaexperience.nativ.posix.PosixErrnoException;
import eu.javaexperience.struct.GenericStruct1;
import eu.javaexperience.struct.GenericStruct2;
import eu.javaexperience.struct.GenericStruct3;
import junit.framework.TestCase;

public class UbRpcTest extends TestCase
{
	protected VirtualUartBus cb;
	protected UartbusServiceBridge bridge;
	
	protected UartBus apiBus;
	protected RpcTestService api;
	
	@Override
	protected void setUp() throws Exception
	{
		UartbusTestTools.testStartLock();
		super.setUp();
		cb = VirtualUartBus.createEnv();
		cb.addPacketLogging();
		
		JavaExperienceLoggingFacility.addStdOut();
		JavaExperienceLoggingFacility.setAllFacilityLoglevel(LogLevel.TRACE);
		
		bridge = UartbusServiceBridge.attachServiceTo(new RpcTestServiceImpl(), cb.getBus(), 63, (byte)32);
		apiBus = cb.newBus(63);
		UartBusDevice dev = apiBus.device(63);
		dev.timeout = UartbusTestTools.getDeviceTimeoutMs();
		api = dev.getRpcRoot().getAppFunctions().cast(RpcTestService.class);
	}
	
	@Override
	protected void tearDown() throws Exception
	{
		super.tearDown();
		IOTools.silentClose(cb);
		UartbusTestTools.testEndRelease();
	}
	
	public static void ensureErrnoThrown(Throwable t, ERRNO err)
	{
		if(t instanceof PosixErrnoException)
		{
			assertEquals(err, ((PosixErrnoException) t).getErrno());
			return;
		}
		else
		{
			t.printStackTrace();
			fail("Not PosixErrnoException are thrown.");
		}
	}
	
	public static enum RpcVals
	{
		VAL_0,
		VAL_1,
		VAL_2,
	}
	
	public void testPass_Call()
	{
		api.call();
	}
	
	public void testExceptions()
	{
		try
		{
			api.callEx();
		}
		catch(PosixErrnoException t)
		{
			ensureErrnoThrown(t, ERRNO.EAGAIN);
		}
	}
	
	public void testMoreExceptions() throws Throwable
	{
		api.callShort((short) 0);
		
		try
		{
			api.callShort((short)13);
		}
		catch(Exception e)
		{
			ensureErrnoThrown(e, ERRNO.EACCES);
		}
	}
	
	public void testInternalError()
	{
		try
		{
			api.callShort((short) 65500);
			fail();
		}
		catch(Exception e)
		{
			//cause internal error witout response
			if(e instanceof PosixErrnoException)
			{
				fail();
			}
		}
	}
	
	public void testType_bool()
	{
		assertEquals(true, api.setBool(true));
		assertEquals(false, api.setBool(false));
	}
	
	public void testType_S8()
	{
		assertEquals((byte)0, api.setS8((byte)0));
		assertEquals((byte) 127, api.setS8((byte) 127));
		assertEquals((byte) 128, api.setS8((byte) -128));
	}
	
	public void testType_U8()
	{
		assertEquals(new uint8_t(0), api.setU8(new uint8_t(0)));
		assertEquals(new uint8_t(255), api.setU8(new uint8_t(255)));
	}
	
	public void testType_S16()
	{
		assertEquals((short) 0, api.setS16((short)0));
		assertEquals(Short.MIN_VALUE, api.setS16(Short.MIN_VALUE));
		assertEquals(Short.MAX_VALUE, api.setS16(Short.MAX_VALUE));
	}
	
	public void testType_U16()
	{
		assertEquals(new uint16_t(0), api.setU16(new uint16_t(0)));
		assertEquals(new uint16_t(65535), api.setU16(new uint16_t(65535)));
	}
	
	public void testType_byte()
	{
		assertArrayEquals(new byte[] {}, api.setBytes(new byte[] {}));
		assertArrayEquals(new byte[] {0, 1, -128, 127}, api.setBytes(new byte[] {0, 1, -128, 127}));
	}
	
	public void testType_String()
	{
		assertEquals("", api.setString(""));
		assertEquals("a", api.setString("a"));
		assertEquals("abc def", api.setString("abc def"));
		assertEquals("áé", api.setString("áé"));
		assertEquals("Hello World", api.setString("Hello World"));
	}
	
	public void testType_VS()
	{
		assertEquals(new VSigned(0), api.setVS(new VSigned(0)));
		assertEquals(new VSigned(1), api.setVS(new VSigned(1)));
		assertEquals(new VSigned(-1), api.setVS(new VSigned(-1)));
		assertEquals(new VSigned(Integer.MIN_VALUE), api.setVS(new VSigned(Integer.MIN_VALUE)));
		assertEquals(new VSigned(Integer.MAX_VALUE), api.setVS(new VSigned(Integer.MAX_VALUE)));
		assertEquals(new VSigned(Long.MIN_VALUE), api.setVS(new VSigned(Long.MIN_VALUE)));
		assertEquals(new VSigned(Long.MAX_VALUE), api.setVS(new VSigned(Long.MAX_VALUE)));
	}
	
	public void testType_VU()
	{
		assertEquals(new VUnsigned(0), api.setVU(new VUnsigned(0)));
		assertEquals(new VUnsigned(1), api.setVU(new VUnsigned(1)));
		assertEquals(new VUnsigned(Integer.MAX_VALUE), api.setVU(new VUnsigned(Integer.MAX_VALUE)));
		assertEquals(new VUnsigned(Long.MAX_VALUE), api.setVU(new VUnsigned(Long.MAX_VALUE)));
		assertEquals(new VUnsigned("8656546456345643"), api.setVU(new VUnsigned("8656546456345643")));
	}
	
	public void testType_Enum()
	{
		assertEquals(RpcVals.VAL_0, api.setEnum(RpcVals.VAL_0));
		assertEquals(RpcVals.VAL_1, api.setEnum(RpcVals.VAL_1));
		assertEquals(RpcVals.VAL_2, api.setEnum(RpcVals.VAL_2));
	}
	
	public void testType_Struct1()
	{
		GenericStruct1<Short> struct = new GenericStruct1<>();
		
		struct.a = (short) 0;
		assertEquals(struct, api.setStruct1(struct));
		
		struct.a = (short) 15;
		assertEquals(struct, api.setStruct1(struct));

		struct.a = (short) -15;
		assertEquals(struct, api.setStruct1(struct));
	}
	
	public void testType_Struct2()
	{
		GenericStruct2<Short, String> struct = new GenericStruct2<>();
		
		struct.a = (short) 0;
		struct.b = "Hello";
		assertEquals(struct, api.setStruct2(struct));
		
		struct.a = (short) 15;
		struct.b = "World";
		assertEquals(struct, api.setStruct2(struct));

		struct.a = (short) -15;
		assertEquals(struct, api.setStruct2(struct));
	}
	
	public static void assert3(RpcTestService api, short sh, String number, byte[] data)
	{
		GenericStruct3<Short, VUnsigned, byte[]> struct = new GenericStruct3<>();
		struct.a = sh;
		struct.b = new VUnsigned(new BigInteger(number));
		struct.c = data;
		
		GenericStruct3<Short, VUnsigned, byte[]> ret = api.setStruct3(struct);
		
		assertEquals(struct.a, ret.a);
		assertEquals(struct.b, ret.b);
		assertArrayEquals(struct.c, ret.c);
	}
	
	public void testType_Struct3()
	{
		assert3(api, (short) 0, "1024", "Hello".getBytes());
		assert3(api, (short) 15, "23324", "World".getBytes());
		assert3(api, (short) -15, "63656345634675412366485877684356132412342341", "Hello World".getBytes());
	}
	
	
	public static void assertVuNumbers(RpcTestService api, String... numbers)
	{
		VUnsigned[] nums = new VUnsigned[numbers.length];
		for(int i=0;i<nums.length;++i)
		{
			nums[i] = new VUnsigned(numbers[i]);
		}
		VUnsigned[] ret = api.setVUnsigneds(nums);
		assertArrayEquals(nums, ret);
	}
	
	public void testType_VUnsigneds()
	{
		assertVuNumbers(api, "0", "1", "5", "10");
		assertVuNumbers(api, "10", "1000", "8656546456345643", "0", "1000");
	}

}
