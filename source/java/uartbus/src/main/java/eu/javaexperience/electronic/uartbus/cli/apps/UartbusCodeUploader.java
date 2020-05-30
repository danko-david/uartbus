package eu.javaexperience.electronic.uartbus.cli.apps;

import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.FROM;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.RPC_HOST;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.RPC_PORT;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.TO;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import eu.javaexperience.cli.CliEntry;
import eu.javaexperience.cli.CliTools;
import eu.javaexperience.datastorage.TransactionException;
import eu.javaexperience.electronic.IntelHexFile;
import eu.javaexperience.electronic.IntelHexFile.CodeSegment;
import eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools;
import eu.javaexperience.electronic.uartbus.rpc.client.UartBus;
import eu.javaexperience.electronic.uartbus.rpc.client.UartBus.UartbusTransaction;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UartBusDevice;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbDevStdNsRoot;
import eu.javaexperience.electronic.uartbus.rpc.client.device.fns.ubb.UbBootloaderFunctions;
import eu.javaexperience.electronic.uartbus.rpc.client.device.fns.ubb.UbBootloaderFunctions.UbBootloaderVariable;
import eu.javaexperience.electronic.uartbus.rpc.client.device.fns.ubb.UbbFlashFunctions;
import eu.javaexperience.interfaces.simple.SimpleGet;
import eu.javaexperience.log.JavaExperienceLoggingFacility;
import eu.javaexperience.log.LogLevel;
import eu.javaexperience.log.Loggable;
import eu.javaexperience.log.Logger;
import eu.javaexperience.log.LoggingTools;
import eu.javaexperience.nativ.posix.ERRNO;
import eu.javaexperience.nativ.posix.PosixErrnoException;
import eu.javaexperience.reflect.Mirror;
import eu.javaexperience.struct.GenericStruct2;
import eu.javaexperience.text.Format;

public class UartbusCodeUploader
{
	protected static final Logger LOG = JavaExperienceLoggingFacility.getLogger(new Loggable("UartbusCodeUploader"));

	public static final CliEntry<String> CODE = CliEntry.createFirstArgParserEntry
	(
		(e)->e,
		"Code to upload in Intel Hex format",
		"c", "-code"
	);
	
	public static final CliEntry<String> NO_VERIFY = CliEntry.createFirstArgParserEntry
	(
		(e)->e,
		"If option is present, skip the verification",
		"n", "-no-verify"
	);
	
	protected static final CliEntry[] PROG_CLI_ENTRIES =
	{
		RPC_HOST,
		RPC_PORT,
		FROM,
		TO,
		CODE,
		NO_VERIFY
	};
	
	protected static CodeSegment getCodeFromFile(String sfile) throws IOException
	{
		IntelHexFile file = IntelHexFile.loadFile(sfile);
		
		//check file
		file.assertValid();
				
		List<CodeSegment> css = IntelHexFile.concatSegments(file.getCode(), 100, (byte) 0xff);
		switch(css.size())
		{
		case 0: System.err.println("Ihex file doesn't contains any code.");System.exit(3);
		case 1: break;
		default:
		
		System.err.println("Ihex file contains multiple code segments.");
		for(CodeSegment cs:css)
		{
			info(Long.toHexString(cs.startAddress)+": "+Format.toHex(cs.data));
		}
		
		System.exit(4);
		}
		
		return css.get(0);
	}
	
	public static <R> R transaction(int retry, SimpleGet<R> tr)
	{
		TransactionException trex = null;
		for(int i=0;i<retry;++i)
		{
			try
			{
				return tr.get();
			}
			catch(TransactionException ex)
			{
				trex = ex;
				continue;
			}
		}
		
		throw trex;
	}
	
	public static void restartGrabDevice(UartBusDevice dev)
	{
		info("Doing grab reboot");
		final long OT = dev.timeout;
		dev.timeout = 1000;
		try
		{
			UbDevStdNsRoot root = dev.getRpcRoot();
			try
			{
				UartbusTransaction reboot = dev.getBus().subscribeResponse(-1, dev.getAddress(), Mirror.emptyByteArray, true);
				root.getBootloaderFunctions().getPowerFunctions().hardwareReset();
				//this waits until reboot complete
				reboot.ensureResponse(3, TimeUnit.SECONDS);
				info("grab reboot done");
			}
			catch(Exception e)
			{
				System.err.println("Can't restart device or we just missed the restart signal.");
				Mirror.propagateAnyway(e);
			}
			
			transaction(dev.retryCount, new SimpleGet<Void>()
			{
				@Override
				public Void get()
				{
					root.getBootloaderFunctions().setVar(UbBootloaderVariable.IS_SIGNALING_SOS, (byte) 0);
					return null;
				}
			});
			
			transaction(dev.retryCount, new SimpleGet<Void>()
			{
				@Override
				public Void get()
				{
					
					root.getBootloaderFunctions().setVar(UbBootloaderVariable.IS_APPLICATION_RUNNING, (byte) 0);
					return null;
				}
			});
			
			Byte appRun = transaction(dev.retryCount, new SimpleGet<Byte>()
			{
				@Override
				public Byte get()
				{
					return root.getBootloaderFunctions().getVar(UbBootloaderVariable.IS_APPLICATION_RUNNING);
				}
			});
			
			if(0 != appRun)
			{
				String error = "After doing grab reboot application is still running. appRun:"+appRun;
				LoggingTools.tryLogFormat(LOG, LogLevel.ERROR, error);
				throw new RuntimeException(error);
			}
		}
		finally
		{
			dev.timeout = OT;
		}
	}
	
	protected static void restartDevice(UartBusDevice dev, boolean flawed)
	{
		final long OT = dev.timeout;
		dev.timeout = 1000;
		try
		{
			if(flawed)
			{
				System.err.println("Something flawed, restarting device.");
			}
			UbDevStdNsRoot root = dev.getRpcRoot();
			try
			{
				UartbusTransaction reboot = dev.getBus().subscribeResponse(-1, dev.getAddress(), Mirror.emptyByteArray, true);
				root.getBootloaderFunctions().getPowerFunctions().hardwareReset();
				//this waits until reboot complete
				reboot.ensureResponse(3, TimeUnit.SECONDS);
				info("reboot done");
			}
			catch(Exception e)
			{
				System.err.println("Can't restart device or we just missed the restart signal.");
				Mirror.propagateAnyway(e);
			}
			
			transaction(dev.retryCount, new SimpleGet<Void>()
			{
				@Override
				public Void get()
				{
					root.getBootloaderFunctions().setVar(UbBootloaderVariable.IS_SIGNALING_SOS, (byte) 0);
					return null;
				}
			});
			
			if(!flawed)
			{
				transaction(dev.retryCount, new SimpleGet<Void>()
				{
					@Override
					public Void get()
					{
						root.getBootloaderFunctions().setVar(UbBootloaderVariable.IS_APPLICATION_RUNNING, (byte) 1);
						return null;
					}
				});
			}
		}
		finally
		{
			dev.timeout = OT;
		}
	}
	
	protected static void info(String info)
	{
		LoggingTools.tryLogFormat(LOG, LogLevel.INFO, info);
	}
	
	public static void main(String[] args) throws Throwable
	{
		JavaExperienceLoggingFacility.addStdOut();
		Map<String, List<String>> pa = CliTools.parseCliOpts(args);
		String un = CliTools.getFirstUnknownParam(pa, PROG_CLI_ENTRIES);
		if(null != un)
		{
			CliTools.printHelpAndExit("UartbusCodeUploader", 1, PROG_CLI_ENTRIES);
		}
		
		String sfile = CODE.tryParse(pa);
		if(null == sfile)
		{
			info("No ihex file specified. Use -c cli switch to specify.");
			System.exit(1);
		}
		
		Integer to = TO.tryParse(pa);
		if(null == to)
		{
			info("No target bus device specified. Use -t cli switch to specify.");
			System.exit(1);
		}
		
		UartBus bus = UartbusCliTools.cliBusConnect(pa);
		
		CodeSegment code = getCodeFromFile(sfile);
		
		UartBusDevice dev = bus.device(to);
		dev.timeout = 100;
		dev.retryCount = 100;
		
		info("Target device: "+dev.getAddress());
		UbDevStdNsRoot root = dev.getRpcRoot();
		
		try
		{
			LoggingTools.tryLogFormat(LOG, LogLevel.DEBUG, "Pinging device");
			//Retransmittable call, so don't need to handle retransmission 
			root.getBusFunctions().ping();
			LoggingTools.tryLogFormat(LOG, LogLevel.DEBUG, "Ping response response");
		}
		catch(TransactionException e)
		{
			e.printStackTrace();
			info("Requested bus device ("+to+") not responding on the bus, maybe not attached.");
			System.exit(2);
		}
		
		//restart with hard reset and grab (ensure not enter to application mode) 
		restartGrabDevice(dev);
		
		
		final int BLOCK_SIZE = 32;
		
		UbBootloaderFunctions boot = root.getBootloaderFunctions();
		UbbFlashFunctions flash = boot.getFlashFunctions();
		
		if(0 != flash.getFlashStage())
		{
			LoggingTools.tryLogFormat(LOG, LogLevel.DEBUG, "Already in flashing stage");
		}
		else
		{
			transaction(dev.retryCount, new SimpleGet<Void>()
			{
				@Override
				public Void get()
				{
					try
					{
						LoggingTools.tryLogFormat(LOG, LogLevel.DEBUG, "Start flashing");
						flash.getStartFlash();
						LoggingTools.tryLogFormat(LOG, LogLevel.DEBUG, "Flashing started");
						return null;
					} catch (PosixErrnoException e)
					{
						Mirror.propagateAnyway(e);
					}
					return null;
				}
			});
		}
		//start
		try
		{
			//verify start address match
			
			final short start = flash.getNextAddress();
			
			if(start != code.startAddress)
			{
				throw new RuntimeException("Application section and code start address mismatch: APP_START region: "+start+", upload code start address: "+code.startAddress);
			}
			
			short[] addr = new short[] {0};
			while(true)
			{
				Boolean ret = transaction(dev.retryCount, new SimpleGet<Boolean>()
				{
					@Override
					public Boolean get()
					{
						if(0 == addr[0])
						{
							addr[0] = flash.getNextAddress();
						}
						
						LoggingTools.tryLogFormat(LOG, LogLevel.DEBUG, "Next Address: "+addr[0]);
						if(code.startAddress+code.data.length <= addr[0])
						{
							//done
							return true;
						}
						
						if(0 == addr[0])
						{
							String msg = "NULL nextAddress received during the code upload. Maybe the target device has been resetted during the code upload process."
									+ "This might be caused by a broken host system (uart bootloader) or insufficient device power supply.";
							LoggingTools.tryLogFormat(LOG, LogLevel.ERROR, msg);
							throw new RuntimeException(msg);
						}
						
						int off = (int) (addr[0]-code.startAddress);
						//an invalid adress might come when we get an response of an earlier request
						if(off < 0)
						{
							LoggingTools.tryLogFormat(LOG, LogLevel.DEBUG, "Invalid calcualted offset: "+off+", retrying");
							return false;
						}
						
						byte[] cp = code.getCodePiece(off, BLOCK_SIZE);
						info("Uploading: "+Long.toHexString(addr[0])+": "+Format.toHex(cp));
						try
						{
							addr[0] = flash.pushCode(addr[0], cp);
						}
						catch(Exception e)
						{
							ERRNO err = null;
							if(e instanceof PosixErrnoException)
							{
								err = ((PosixErrnoException) e).getErrno();
							}
							else if(e.getCause() instanceof PosixErrnoException)
							{
								err = ((PosixErrnoException)e.getCause()).getErrno();
							}
							
							if(ERRNO.ENXIO == err)
							{
								addr[0] = 0;
								return false;
							}
							Mirror.propagateAnyway(e);
						}
						
						return false;
					}
				});
				
				if(ret)
				{
					break;
				}
			}
			
			transaction(dev.retryCount, new SimpleGet<Void>()
			{
				@Override
				public Void get()
				{
					try
					{
						LoggingTools.tryLogFormat(LOG, LogLevel.DEBUG, "Committing flash.");

						flash.commitFlash();
						LoggingTools.tryLogFormat(LOG, LogLevel.INFO, "Committing flash modification done.");
					}
					catch (PosixErrnoException e)
					{
						Mirror.propagateAnyway(e);
					}
					return null;
				}
			});
			
			addr[0] = start;
			
			if(!NO_VERIFY.hasOption(pa))
			{
				info("Verifying uploaded code.");
				
				while(true)
				{
					Boolean ret = transaction(dev.retryCount, new SimpleGet<Boolean>()
					{
						@Override
						public Boolean get()
						{
							if(code.startAddress+code.data.length <= addr[0])
							{
								//done
								return true;
							}
							
							int off = (int) (addr[0]-code.startAddress);
							
							info("Verifying code at "+Long.toHexString(addr[0])+":");

							byte[] cp = code.getCodePiece(off, BLOCK_SIZE);
							
							GenericStruct2<Short, byte[]> c = boot.readProgramCode(addr[0], (byte) cp.length);
							if(addr[0] != c.a)
							{
								throw new RuntimeException("Different address returned, than the requested. Req: "+addr[0]+", ret: "+c.a);
							}
							
							if(Arrays.equals(cp, c.b))
							{
								info("Code at "+Long.toHexString(addr[0])+" is OK");
							}
							else
							{
								info("Code at "+Long.toHexString(addr[0])+" is mismatches. Actual:   "+Format.toHex(c.b)+", Expected: "+Format.toHex(cp));
								throw new RuntimeException("Uploaded code verification failed.");
							}
							
							addr[0] += BLOCK_SIZE;
							
							return false;
						}
					});
					
					if(ret)
					{
						break;
					}
				}
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
			//something flawed, reset the target device.
			try
			{
				restartDevice(dev, true);
			}
			finally
			{
				System.exit(0);
			}
		}
		
		try
		{
			restartDevice(dev, false);
		}
		finally
		{
			System.exit(0);
		}
	}
}
