package eu.javaexperience.electronic.uartbus.rpc.client.types;

import eu.javaexperience.electronic.uartbus.rpc.client.device.UbDeviceNs;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbIndex;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbRetransmittable;
import eu.javaexperience.electronic.uartbus.rpc.data_type.VSigned;
import eu.javaexperience.electronic.uartbus.rpc.data_type.VUnsigned;

public interface UbRemoteString extends UbDeviceNs
{
	@UbIndex(ns=1)
	public VSigned getLength();
	
	@UbIndex(ns=2)
	@UbRetransmittable
	public byte[] getPart(VUnsigned from, VUnsigned length);
}
