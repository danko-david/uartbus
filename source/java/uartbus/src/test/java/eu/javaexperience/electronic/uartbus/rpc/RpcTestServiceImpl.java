package eu.javaexperience.electronic.uartbus.rpc;

import eu.javaexperience.electronic.uartbus.rpc.UbRpcTest.RpcVals;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbDeviceNs;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbIndex;
import eu.javaexperience.electronic.uartbus.rpc.datatype.VSigned;
import eu.javaexperience.electronic.uartbus.rpc.datatype.VUnsigned;
import eu.javaexperience.electronic.uartbus.rpc.datatype.uint16_t;
import eu.javaexperience.electronic.uartbus.rpc.datatype.uint8_t;
import eu.javaexperience.nativ.posix.ERRNO;
import eu.javaexperience.nativ.posix.PosixErrnoException;
import eu.javaexperience.struct.GenericStruct1;
import eu.javaexperience.struct.GenericStruct2;
import eu.javaexperience.struct.GenericStruct3;

public class RpcTestServiceImpl implements RpcTestService
{
	@UbIndex(ns=1)
	public boolean setBool(boolean v)
	{
		return v;
	}
	
	@UbIndex(ns=2)
	public byte setS8(byte v)
	{
		return v;
	}
	
	@UbIndex(ns=3)
	public uint8_t setU8(uint8_t v)
	{
		return v;
	}
	
	@UbIndex(ns=4)
	public uint16_t setU16(uint16_t v)
	{
		return v;
	}
	
	@UbIndex(ns=5)
	public short setS16(short v)
	{
		return v;
	}
	
	@UbIndex(ns=6)
	public byte[] setBytes(byte[] v)
	{
		return v;
	}
	
	@UbIndex(ns=7)
	public String setString(String v)
	{
		return v;
	}
	
	@UbIndex(ns=8)
	public VSigned setVS(VSigned v)
	{
		return v;
	}
	
	@UbIndex(ns=9)
	public VUnsigned setVU(VUnsigned v)
	{
		return v;
	}
	
	@UbIndex(ns=10)
	public RpcVals setEnum(RpcVals v)
	{
		return v;
	}
	
	@UbIndex(ns=11)
	public GenericStruct1<Short> setStruct1(GenericStruct1<Short> v)
	{
		return v;
	}
	
	@UbIndex(ns=12)
	public GenericStruct2<Short, String> setStruct2(GenericStruct2<Short, String> v)
	{
		return v;
	}
	
	@UbIndex(ns=13)
	public GenericStruct3<Short, VUnsigned, byte[]> setStruct3(GenericStruct3<Short, VUnsigned, byte[]> v)
	{
		return v;
	}
	
	@UbIndex(ns=14)
	public String[] setStrings(String[] v)
	{
		return v;
	}
	
	@UbIndex(ns=15)
	public VUnsigned[] setVUnsigneds(VUnsigned[] v)
	{
		return v;
	}
	
	@UbIndex(ns=16)
	public void call()
	{
	}
	
	@UbIndex(ns=50)
	public void callEx() throws PosixErrnoException
	{
		throw new PosixErrnoException(ERRNO.EAGAIN);
	}
	
	@UbIndex(ns=51)
	public void callShort(short v) throws PosixErrnoException
	{
		if(0 != v)
		{
			throw new PosixErrnoException(ERRNO.ERRNOOfValue(v));
		}
	}
	
	
	@Override
	public <T extends UbDeviceNs> T cast(Class<T> dst)
	{
		return null;
	}

	@Override
	public <T extends UbDeviceNs> T customNs(Class<T> dst, short num)
	{
		return null;
	}
}