package eu.javaexperience.electronic.uartbus.rpc.client.device.fns;

import eu.javaexperience.electronic.uartbus.rpc.client.device.UbDeviceNs;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbIndex;

public interface UbBusFunctions extends UbDeviceNs
{
	@UbIndex(ns=0)
	public void ping();
}
