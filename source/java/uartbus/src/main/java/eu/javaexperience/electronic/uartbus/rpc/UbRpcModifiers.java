package eu.javaexperience.electronic.uartbus.rpc;

public enum UbRpcModifiers
{
	rpc_modifier_function,
	rpc_modifier_namespace,

	rpc_modifier_advanced_handler,
	
	;
	
	public int mask()
	{
		return 0x1 << ordinal();
	}
}
