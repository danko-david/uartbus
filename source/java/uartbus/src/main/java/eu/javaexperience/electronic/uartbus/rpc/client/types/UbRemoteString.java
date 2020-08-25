package eu.javaexperience.electronic.uartbus.rpc.client.types;

import eu.javaexperience.electronic.uartbus.rpc.client.device.UbDeviceNs;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbIndex;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbRetransmittable;
import eu.javaexperience.electronic.uartbus.rpc.datatype.VSigned;
import eu.javaexperience.electronic.uartbus.rpc.datatype.VUnsigned;

public interface UbRemoteString extends UbDeviceNs
{
	@UbIndex(ns=1)
	public VSigned getLength();
	
	@UbIndex(ns=2)
	@UbRetransmittable
	public byte[] getStringPart(VUnsigned from, VUnsigned length);
}
