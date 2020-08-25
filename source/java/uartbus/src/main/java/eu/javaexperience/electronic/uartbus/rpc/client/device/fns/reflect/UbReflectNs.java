package eu.javaexperience.electronic.uartbus.rpc.client.device.fns.reflect;

import eu.javaexperience.electronic.uartbus.rpc.client.device.UbDeviceNs;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbIndex;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbRetransmittable;
import eu.javaexperience.electronic.uartbus.rpc.client.types.UbRemoteString;
import eu.javaexperience.electronic.uartbus.rpc.datatype.uint16_t;
import eu.javaexperience.electronic.uartbus.rpc.datatype.uint8_t;
import eu.javaexperience.nativ.posix.PosixErrnoException;

public interface UbReflectNs extends UbDeviceNs
{
	@UbIndex(ns = 1)
	@UbRetransmittable
	public uint8_t getNamespaceIndex() throws PosixErrnoException;
	
	@UbIndex(ns = 2)
	@UbRetransmittable
	public uint8_t getModifiers();
	
	@UbIndex(ns = 3)
	public UbRemoteString getName();

	@UbIndex(ns = 4)
	public UbRemoteString getDescription();
	
	@UbIndex(ns = 5)
	public UbRemoteString getMeta();
	
	@UbIndex(ns = 9)
	@UbRetransmittable
	public uint8_t getSubNodesCount();
	
	@UbIndex(ns = 10)
	public UbReflectNs getSubNode(uint8_t ns);
	
	@UbIndex(ns = 11)
	@UbRetransmittable
	public uint8_t getNthSubNodeNamespace(uint8_t ns);
}
