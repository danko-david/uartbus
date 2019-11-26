package eu.javaexperience.electronic;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.util.Arrays;

import eu.javaexperience.io.IOStream;

public class SerialTools
{
	private SerialTools() {}
	
	public static IOStream openSerial(final String dev, int baud) throws IOException
	{
		return openCommunicatorProcess("socat", dev+",b"+baud+",raw,echo=0", "-");
	}
	
	public static IOStream openDirectSerial(String directCommand, String serial, int baud) throws IOException
	{
		return openCommunicatorProcess(directCommand, serial, String.valueOf(baud));
	}
	
	public static IOStream openCommunicatorProcess(String... params) throws IOException
	{
		ProcessBuilder pb = new ProcessBuilder(params);
		
		pb.redirectError(Redirect.INHERIT);
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
				return "Uartbus serial communication process :"+Arrays.toString(params);
			}
			
			@Override
			public String localAddress()
			{
				return "Uartbus serial communication process :"+Arrays.toString(params);
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
					throw new RuntimeException("Communication process is dead. Exit status: "+p.exitValue());
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
