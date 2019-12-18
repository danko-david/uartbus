package eu.javaexperience.electronic.uartbus.rpc.client.device.fns.reflect;

import eu.javaexperience.electronic.uartbus.rpc.client.device.UbIndex;
import eu.javaexperience.electronic.uartbus.rpc.client.device.VUnsigned;
import eu.javaexperience.electronic.uartbus.rpc.client.types.UbRemoteString;

public interface UbReflectNs 
{
	@UbIndex(ns = 1)
	public boolean isExists();
	
	@UbIndex(ns = 2)
	public short getModifiers();
	
	@UbIndex(ns = 3)
	public UbRemoteString getName();

	@UbIndex(ns = 4)
	public UbRemoteString getDescription();
	
	@UbIndex(ns = 5)
	public UbRemoteString getMeta();
	
	
	
	
	@UbIndex(ns = 9)
	public VUnsigned getSubNodesCount();
	
	@UbIndex(ns = 10)
	public UbReflectNs getSubNode(short ns);
	
	
	
	
}
