package eu.javaexperience.electronic.uartbus.rpc.client.device;

import java.util.Arrays;

import eu.javaexperience.generic.annotations.Ignore;
import eu.javaexperience.reflect.Mirror;

public class UbDeviceNsLazyImpl implements UbDeviceNs
{
	protected UartBusDevice dev;
	protected byte[] path = Mirror.emptyByteArray;
	
	public UbDeviceNsLazyImpl(UartBusDevice dev)
	{
		this.dev = dev;
	}
	
	public UbDeviceNsLazyImpl(UartBusDevice dev, byte[] path)
	{
		this(dev);
		this.path = Arrays.copyOf(path, path.length);
	}
	
	@Ignore
	@Override
	public <T extends UbDeviceNs> T cast(Class<T> dst){return null;}

	@Ignore
	@Override
	public <T extends UbDeviceNs> T customNs(Class<T> dst, short num){return null;}
}
