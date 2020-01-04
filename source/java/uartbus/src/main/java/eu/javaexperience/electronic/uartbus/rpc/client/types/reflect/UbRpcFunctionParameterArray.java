package eu.javaexperience.electronic.uartbus.rpc.client.types.reflect;

import eu.javaexperience.electronic.uartbus.rpc.client.device.UbIndex;
import eu.javaexperience.electronic.uartbus.rpc.data_type.VUnsigned;

public interface UbRpcFunctionParameterArray
{
	@UbIndex(ns=1)
	public VUnsigned getLength();
	
	//TODO annotate and handle namespace selection
	public UbRpcFunctionParameter get(short index);
}
