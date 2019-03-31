package eu.javaexperience.electronic.uartbus.rpc.client.device.fns.ubb;

import eu.javaexperience.electronic.uartbus.rpc.client.device.UbDeviceNs;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbIndex;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbRetransmittable;
import eu.javaexperience.struct.GenericStruct1;
import eu.javaexperience.struct.GenericStruct2;

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
	
	@UbIndex(ns=3)
	@UbRetransmittable
	public GenericStruct2<Short, byte[]> readProgramCode(short address, byte length);
	
	@UbIndex(ns=4)
	public UbbFlashFunctions getFlashFunctions();
}
