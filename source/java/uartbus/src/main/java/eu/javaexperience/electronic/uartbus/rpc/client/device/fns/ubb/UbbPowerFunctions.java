package eu.javaexperience.electronic.uartbus.rpc.client.device.fns.ubb;

import eu.javaexperience.electronic.uartbus.rpc.client.device.UbDeviceNs;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbIndex;
import eu.javaexperience.electronic.uartbus.rpc.data_type.NoReturn;

public interface UbbPowerFunctions extends UbDeviceNs
{
	/**
	 * Reset using the watchdog timer
	 * */
	@UbIndex(ns=0)
	public NoReturn hardwareReset();
	
	/**
	 * reset by jumping to the reset vector
	 * */
	@UbIndex(ns=1)
	public NoReturn softwareReset();
}
