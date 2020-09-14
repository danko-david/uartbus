package eu.javaexperience.electronic.uartbus.rpc;

import eu.javaexperience.electronic.uartbus.rpc.UbRpcTest.RpcVals;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbDeviceNs;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbIndex;
import eu.javaexperience.electronic.uartbus.rpc.datatype.VSigned;
import eu.javaexperience.electronic.uartbus.rpc.datatype.VUnsigned;
import eu.javaexperience.electronic.uartbus.rpc.datatype.uint16_t;
import eu.javaexperience.electronic.uartbus.rpc.datatype.uint8_t;
import eu.javaexperience.nativ.posix.PosixErrnoException;
import eu.javaexperience.struct.GenericStruct1;
import eu.javaexperience.struct.GenericStruct2;
import eu.javaexperience.struct.GenericStruct3;

public interface RpcTestService extends UbDeviceNs
{
	@UbIndex(ns=1)
	public boolean setBool(boolean bool);
	
	@UbIndex(ns=2)
	public byte setS8(byte b);
	
	@UbIndex(ns=3)
	public uint8_t setU8(uint8_t v);
	
	@UbIndex(ns=4)
	public uint16_t setU16(uint16_t v);
	
	@UbIndex(ns=5)
	public short setS16(short v);
	
	@UbIndex(ns=6)
	public byte[] setBytes(byte[] v);
	
	@UbIndex(ns=7)
	public String setString(String s);
	
	@UbIndex(ns=8)
	public VSigned setVS(VSigned v);
	
	@UbIndex(ns=9)
	public VUnsigned setVU(VUnsigned v);
	
	@UbIndex(ns=10)
	public RpcVals setEnum(RpcVals v);
	
	@UbIndex(ns=11)
	public GenericStruct1<Short> setStruct1(GenericStruct1<Short> v);
	
	@UbIndex(ns=12)
	public GenericStruct2<Short, String> setStruct2(GenericStruct2<Short, String> v);
	
	@UbIndex(ns=13)
	public GenericStruct3<Short, VUnsigned, byte[]> setStruct3(GenericStruct3<Short, VUnsigned, byte[]> v);
	
	@UbIndex(ns=14)
	public String[] setStrings(String[] v);
	
	@UbIndex(ns=15)
	public VUnsigned[] setVUnsigneds(VUnsigned[] v);
	
	@UbIndex(ns=16)
	public void call();
	
	@UbIndex(ns=50)
	public void callEx() throws PosixErrnoException;
	
	@UbIndex(ns=51)
	public void callShort(short v) throws PosixErrnoException;
}
