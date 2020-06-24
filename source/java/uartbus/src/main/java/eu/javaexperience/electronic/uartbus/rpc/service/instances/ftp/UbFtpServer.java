package eu.javaexperience.electronic.uartbus.rpc.service.instances.ftp;

import java.io.IOException;
import java.math.BigInteger;

import eu.javaexperience.electronic.uartbus.rpc.client.device.VSigned;
import eu.javaexperience.electronic.uartbus.rpc.client.device.VUnsigned;
import eu.javaexperience.file.FileSystemTools;
import eu.javaexperience.nativ.posix.PosixErrnoException;

public class UbFtpServer
{
	public static void main(String[] args) throws IOException, PosixErrnoException
	{
		FtpBusService fbs = new FtpBusService(FileSystemTools.DEFAULT_FILESYSTEM.fromUri("/home/szupervigyor/temp/ubftp"));
		
		fbs.appendFile(fid(3), neg(-1), "Hello".getBytes());
	}
	
	public static VUnsigned fid(int id)
	{
		return new VUnsigned(BigInteger.valueOf(id));
	}
	
	public static VSigned neg(int id)
	{
		return new VSigned(BigInteger.valueOf(id));
	}
}
