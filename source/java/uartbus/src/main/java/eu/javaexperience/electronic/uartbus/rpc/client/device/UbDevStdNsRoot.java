package eu.javaexperience.electronic.uartbus.rpc.client.device;

import eu.javaexperience.electronic.uartbus.rpc.client.device.fns.UbBusFunctions;
import eu.javaexperience.electronic.uartbus.rpc.client.device.fns.ubb.UbBootloaderFunctions;

public interface UbDevStdNsRoot extends UbDeviceNs
{
	@UbIndex(ns=1)
	public UbBusFunctions getBusFunctions();
	
	@UbIndex(ns=2)
	public UbBootloaderFunctions getBootloaderFunctions();
	
	@UbIndex(ns=32)
	public UbDeviceNs getAppFunctions();
}
