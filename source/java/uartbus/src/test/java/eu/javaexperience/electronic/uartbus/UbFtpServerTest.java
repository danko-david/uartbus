package eu.javaexperience.electronic.uartbus;

import static org.junit.Assert.*;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;

import eu.javaexperience.electronic.uartbus.rpc.client.UartBus;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UartBusDevice;
import eu.javaexperience.electronic.uartbus.rpc.datatype.VSigned;
import eu.javaexperience.electronic.uartbus.rpc.datatype.VUnsigned;
import eu.javaexperience.electronic.uartbus.rpc.service.UartbusServiceBridge;
import eu.javaexperience.electronic.uartbus.rpc.service.instances.ftp.FtpBusService;
import eu.javaexperience.electronic.uartbus.rpc.service.instances.ftp.UbFileSystemService;
import eu.javaexperience.electronic.uartbus.rpc.virtual.VirtualUartBus;
import eu.javaexperience.file.FileSystemTools;
import eu.javaexperience.io.IOTools;
import eu.javaexperience.io.file.FileTools;
import eu.javaexperience.io.file.TmpFile;
import eu.javaexperience.reflect.Mirror;
import eu.javaexperience.text.StringTools;
import junit.framework.TestCase;

public class UbFtpServerTest extends TestCase
{
	protected TmpFile tmp;
	protected VirtualUartBus cb;
	
	@Override
	protected void setUp() throws Exception
	{
		UartbusTestTools.testStartLock();
		super.setUp();
		tmp = new TmpFile("/tmp/", "_ubftp");
		cb = VirtualUartBus.createEnv();
		File f = tmp.getFile();
		f.mkdirs();
		//JavaExperienceLoggingFacility.setAllFacilityLoglevel(LogLevel.DEBUG);
		//JavaExperienceLoggingFacility.setFutureDefaultLoglevel(LogLevel.DEBUG);
		//JavaExperienceLoggingFacility.addStdOut();
		//cb.addPacketLogging();
	}
	
	@Override
	protected void tearDown() throws Exception
	{
		super.tearDown();
		IOTools.silentClose(cb);
		FileTools.deleteDirectory(tmp.getFile(), false);
		UartbusTestTools.testEndRelease();
	}
	
	protected UbFileSystemService setupFtpAndGetNamespace() throws IOException
	{
		File f = tmp.getFile();
		new File(f.toString()+"/my_file").createNewFile();
		
		FtpBusService fbs = new FtpBusService(FileSystemTools.DEFAULT_FILESYSTEM.fromUri(f.toString()));
		
		UartbusServiceBridge bridge = UartbusServiceBridge.attachServiceTo
		(
			fbs,
			UartBus.fromConnection(()->
			{
				try
				{
					return cb.createNewConnection();
				}
				catch (EOFException e)
				{
					Mirror.propagateAnyway(e);
					return null;
				}
			}, null, 10),
			10,
			(byte) 32
		);
		
		UartBus bus = cb.getBus();
		UartBusDevice node = bus.device(10);
		node.timeout = UartbusTestTools.getDeviceTimeoutMs();
		
		UbFileSystemService ftp = node.getRpcRoot().customNs(UbFileSystemService.class, (short) 32);
		return ftp;
	}
	
	public void testBasics() throws Throwable
	{
		File f = tmp.getFile();
		UbFileSystemService ftp = setupFtpAndGetNamespace();
		
		assertTrue(ftp.exists(new VUnsigned(0)));
		assertEquals
		(
			StringTools.getSubstringAfterLastString(f.toString(), "/"),
			new String(ftp.getFileName(new VUnsigned(0)).getStringPart(new VUnsigned(0), new VUnsigned(256)))
		);
		
		//create new file, write string, ensure written to file, read content back
		VUnsigned nf = ftp.createNewRegularFile(new VUnsigned(0), "hello");
		
		ftp.appendFile(nf, new VSigned(-1), "world".getBytes());
		
		File hello = new File(f.toString()+"/hello");
		
		assertTrue(hello.exists());
		
		assertArrayEquals
		(
			"world".getBytes(),
			IOTools.getFilePart(hello.toString(), 0, 1024)
		);
		
		assertArrayEquals
		(
			"world".getBytes(),
			ftp.getFilePart(nf, new VUnsigned(0), new VUnsigned(1024))
		);
	}
}
