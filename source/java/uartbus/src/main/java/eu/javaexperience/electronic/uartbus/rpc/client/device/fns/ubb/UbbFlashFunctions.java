package eu.javaexperience.electronic.uartbus.rpc.client.device.fns.ubb;

import eu.javaexperience.electronic.uartbus.rpc.client.device.UbDeviceNs;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbIndex;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbRetransmittable;
import eu.javaexperience.nativ.posix.PosixErrnoException;

public interface UbbFlashFunctions extends UbDeviceNs 
{
	@UbRetransmittable
	@UbIndex(ns=0)
	public byte getFlashStage() throws PosixErrnoException;
	
	@UbIndex(ns=1)
	public void getStartFlash() throws PosixErrnoException;
	
	@UbRetransmittable
	@UbIndex(ns=2)
	public short getNextAddress();
	
	@UbIndex(ns=3)
	public short pushCode(short startAddress, byte[] data) throws PosixErrnoException;
	
	@UbIndex(ns=4)
	public void commitFlash() throws PosixErrnoException;
}
