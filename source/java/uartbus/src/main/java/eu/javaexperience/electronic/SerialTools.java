package eu.javaexperience.electronic;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import eu.javaexperience.interfaces.simple.SimpleCall;
import eu.javaexperience.io.IOStream;

public class SerialTools
{
	private SerialTools() {}
	
	public static IOStream openSerial(final String dev, int baud) throws IOException
	{
		ProcessBuilder pb = new ProcessBuilder
		(
			"socat", dev+",b"+baud+",raw,echo=0", "-"
		);
		
		final Process p = pb.start();
		return new IOStream()
		{
			@Override
			public void flush() throws IOException
			{
				
			}
			
			@Override
			public String remoteAddress()
			{
				return "serial:"+dev;
			}
			
			@Override
			public String localAddress()
			{
				return "serial:"+dev;
			}
			
			@Override
			public boolean isClosed()
			{
				return false;
			}
			
			protected void assertRunning()
			{
				if(!p.isAlive())
				{
					throw new RuntimeException("Communication process is dead");
				}
			}
			
			@Override
			public OutputStream getOutputStream()
			{
				assertRunning();
				return p.getOutputStream();
			}
			
			@Override
			public InputStream getInputStream()
			{
				assertRunning();
				return p.getInputStream();
			}
			
			@Override
			public void close()
			{
				p.destroy();
			}
		};
	}
}
