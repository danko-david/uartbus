package eu.javaexperience.electronic.uartbus.rpc.client.device.fns;

import eu.javaexperience.electronic.uartbus.rpc.client.device.UbDeviceNs;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbIndex;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbRetransmittable;

public interface UbBusFunctions extends UbDeviceNs
{
	@UbIndex(ns=0)
	@UbRetransmittable
	public void ping();
}
