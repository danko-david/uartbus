package eu.javaexperience.electronic.uartbus.rpc.client.types;

import eu.javaexperience.electronic.uartbus.rpc.client.device.UbIndex;
import eu.javaexperience.electronic.uartbus.rpc.client.device.VSigned;
import eu.javaexperience.electronic.uartbus.rpc.client.device.VUnsigned;

public interface UbRemoteString
{
	@UbIndex(ns=1)
	public VSigned getLength();
	
	public byte[] getPart(VUnsigned from, VUnsigned to);
}
