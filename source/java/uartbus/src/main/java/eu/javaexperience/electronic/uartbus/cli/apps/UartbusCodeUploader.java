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
import eu.javaexperience.log.Loggable;
import eu.javaexperience.log.Logger;
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
	
	public static void printHelpAndExit(int exit)
	{
		System.err.println("Usage of UartbusCodeUploader:\n");
		System.err.println(CliTools.renderListAllOption(PROG_CLI_ENTRIES));
		System.exit(1);
	}
	
	protected static UartBus connect(String... args) throws IOException
	{
		Map<String, List<String>> pa = CliTools.parseCliOpts(args);
		return UartBus.fromTcp
		(
			RPC_HOST.tryParseOrDefault(pa, "127.0.0.1"),
			RPC_PORT.tryParseOrDefault(pa, 2112),
			UartbusCliTools.parseFrom(pa)
		);
	}
	
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
			System.out.println(Long.toHexString(cs.startAddress)+": "+Format.toHex(cs.data));
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
				System.out.println("reboot done");
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
	
	public static void main(String[] args) throws Throwable
	{
		//args = new String[]{"-t", "1", "-c", "/home/szupervigyor/projektek/electronics/uartbus/source/uc/utils/ub_app/app.hex"};
		JavaExperienceLoggingFacility.addStdOut();
		Map<String, List<String>> pa = CliTools.parseCliOpts(args);
		String un = CliTools.getFirstUnknownParam(pa, PROG_CLI_ENTRIES);
		if(null != un)
		{
			printHelpAndExit(1);
		}
		
		String sfile = CODE.tryParse(pa);
		if(null == sfile)
		{
			System.err.println("No ihex file specified. Use -c cli switch to specify.");
			System.exit(1);
		}
		
		Integer to = TO.tryParse(pa);
		if(null == to)
		{
			System.err.println("No target bus device specified. Use -t cli switch to specify.");
			System.exit(1);
		}
		
		UartBus bus = UartbusCliTools.cliBusConnect(pa); 
		
		CodeSegment code = getCodeFromFile(sfile);
		
		UartBusDevice dev = bus.device(to);
		dev.timeout = 100;
		dev.retryCount = 100;
		System.out.println("Target device: "+dev.getAddress());
		UbDevStdNsRoot root = dev.getRpcRoot();
		
		try
		{
			//Retransmittable call, so don't need to handle retransmission 
			root.getBusFunctions().ping();
		}
		catch(TransactionException e)
		{
			e.printStackTrace();
			System.err.println("Requested bus device ("+to+") not responding on the bus, maybe not attached.");
			System.exit(2);
		}
		
		dev.timeout = 20;
		dev.retryCount = 100;
		
		UbBootloaderFunctions boot = root.getBootloaderFunctions();
		UbbFlashFunctions flash = boot.getFlashFunctions();
		
		//TODO
		if(0 != flash.getFlashStage())
		{
			throw new RuntimeException("Bus device is already in programming state, before we started the process. Maybe other code upload in progress. Or just try restart the device.");
		}
		
		//start
		try
		{
			transaction(dev.retryCount, new SimpleGet<Void>()
			{
				@Override
				public Void get()
				{
					try
					{
						if(0 != flash.getFlashStage())
						{
							return null;
						}
					
						flash.getStartFlash();
						return null;
					} catch (PosixErrnoException e)
					{
						Mirror.propagateAnyway(e);
					}
					return null;
				}
			});
			
			//verify start address match
			
			final short start = flash.getNextAddress();
			
			if(start != code.startAddress)
			{
				throw new RuntimeException("Application section and code start address mismatch: APP_START region: "+start+", upload code start address: "+code.startAddress);
			}
			
			while(true)
			{
				Boolean ret = transaction(dev.retryCount, new SimpleGet<Boolean>()
				{
					@Override
					public Boolean get()
					{
						short addr = flash.getNextAddress();
						if(code.startAddress+code.data.length <= addr)
						{
							//done
							return true;
						}
						
						int off = (int) (addr-code.startAddress);
						
						byte[] cp = code.getCodePiece(off, 16);
						System.out.println("Uploading: "+Long.toHexString(addr)+": "+Format.toHex(cp));
						try
						{
							flash.pushCode(addr, cp);
						}
						catch (PosixErrnoException e)
						{
							e.printStackTrace();
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
						flash.commitFlash();
						System.out.println("Committing flash modification done.");
					}
					catch (PosixErrnoException e)
					{
						Mirror.propagateAnyway(e);
					}
					return null;
				}
			});
			
			short[] addr = new short[1];
			addr[0] = start;
			
			if(!NO_VERIFY.hasOption(pa))
			{
			
				System.out.println("Verifying uploaded code.");
				
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
							
							final int blockSize = 8;
							
							int off = (int) (addr[0]-code.startAddress);
							
							System.out.print("Verifying "+Long.toHexString(addr[0])+":");
							try
							{
								byte[] cp = code.getCodePiece(off, blockSize);
								
								GenericStruct2<Short, byte[]> c = boot.readProgramCode(addr[0], (byte) cp.length);
								if(addr[0] != c.a)
								{
									throw new RuntimeException("Different address returned, than the requested. Req: "+addr[0]+", ret: "+c.a);
								}
								
								if(Arrays.equals(cp, c.b))
								{
									System.out.print(" OK");
								}
								else
								{
									System.out.print(" Code mismatch:");
									System.out.print("Actual:   "+Format.toHex(c.b));
									System.out.print("Expected: "+Format.toHex(cp));
									throw new RuntimeException("Uploaded code verification failed.");
								}
								
								addr[0] += blockSize;
								
								return false;
							}
							finally
							{
								System.out.println("");
							}
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
		//load intel hex file, verify the content
		

		//connect to the UART bus
		
		//check destination is alive
		
		//enter progamming mode
		
		//get the start address
		
		//fail if it's different from the IHEX start address
		
		//send code piece
		//check the next address pointer, discard and report fail on error.
		
		//commit
		//verify code space (if verification is not disabled)
		
		//restart the target.
		//as it's appeared, clear the recovery mode and start app.
		
		
		//TODO later: update app variables before commit
		
	}
}
