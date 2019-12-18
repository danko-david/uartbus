package eu.javaexperience.electronic.uartbus.rpc.client.types.reflect;

import eu.javaexperience.electronic.uartbus.rpc.client.device.UbIndex;

public interface UbRpcFunctionDescriptor
{
	@UbIndex(ns=1)
	public UbRpcFunctionParameterArray getReturningTypes();
	
	@UbIndex(ns=2)
	public UbRpcFunctionParameterArray getParameterTypes();
}
