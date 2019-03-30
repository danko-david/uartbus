package eu.javaexperience.electronic.uartbus.rpc.client.device.fns.ubb;

import eu.javaexperience.electronic.uartbus.rpc.client.device.UbDeviceNs;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbIndex;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbRetransmittable;

public interface UbBootloaderFunctions extends UbDeviceNs
{
	public static enum UbBootloaderVariable
	{
		IS_APPLICATION_RUNNING,
		IS_SIGNALING_SOS,
		RESET_FLAG,
	}
	
	
	@UbIndex(ns=0)
	public UbbPowerFunctions getPowerFunctions();
	
	@UbIndex(ns=1)
	@UbRetransmittable
	public byte getVar(UbBootloaderVariable var);
	
	@UbIndex(ns=2)
	public void setVar(UbBootloaderVariable var, byte value);
	
	//TODO return value
	@UbIndex(ns=3)
	@UbRetransmittable
	public void readProgramCode(long address);
	
	@UbIndex(ns=4)
	public UbbFlashFunctions getFlashFunctions();
}
