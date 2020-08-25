package eu.javaexperience.electronic.uartbus.rpc.client.types.reflect;

import eu.javaexperience.electronic.uartbus.rpc.client.device.UbIndex;
import eu.javaexperience.electronic.uartbus.rpc.client.types.UbRemoteString;

public interface UbRpcFunctionParameter
{
	@UbIndex(ns=1)
	public byte getDataType();
	
	@UbIndex(ns=2)
	public short getDataSize();
	
	@UbIndex(ns=3)
	public UbRemoteString getName();
	
	@UbIndex(ns=4)
	public UbRemoteString getDescription();
	
	@UbIndex(ns=5)
	public UbRemoteString getMeta();
}
