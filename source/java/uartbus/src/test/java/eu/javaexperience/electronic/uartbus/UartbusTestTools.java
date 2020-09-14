package eu.javaexperience.electronic.uartbus;

import java.util.concurrent.Semaphore;

import eu.javaexperience.reflect.Mirror;

public class UartbusTestTools
{
	protected static final Semaphore SEM = new Semaphore(1);
	
	public static void testStartLock()
	{
		try
		{
			SEM.acquire();
		}
		catch (InterruptedException e)
		{
			Mirror.propagateAnyway(e);
		}
	}
	
	public static void testEndRelease()
	{
		SEM.release();
	}

	public static long getDeviceTimeoutMs()
	{
		return 100;
	}
}
