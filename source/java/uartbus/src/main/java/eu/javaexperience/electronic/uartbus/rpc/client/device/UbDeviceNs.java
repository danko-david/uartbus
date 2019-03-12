package eu.javaexperience.electronic.uartbus.rpc.client.device;

public interface UbDeviceNs
{
	public <T extends UbDeviceNs> T cast(Class<T> dst);
	
	public <T extends UbDeviceNs> T customNs(Class<T> dst, short num);
	
}
