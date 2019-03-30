package eu.javaexperience.electronic.uartbus.rpc.client;

import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.FROM;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.RPC_HOST;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.RPC_PORT;
import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.TO;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import eu.javaexperience.cli.CliEntry;
import eu.javaexperience.cli.CliTools;
import eu.javaexperience.datastorage.TransactionException;
import eu.javaexperience.electronic.IntelHexFile;
import eu.javaexperience.electronic.IntelHexFile.CodeSegment;
import eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UartBusDevice;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbDevStdNsRoot;
import eu.javaexperience.log.JavaExperienceLoggingFacility;
import eu.javaexperience.log.Loggable;
import eu.javaexperience.log.Logger;
import eu.javaexperience.measurement.MeasurementSerie;

public class UartbusCodeUploader
{
	protected static final Logger LOG = JavaExperienceLoggingFacility.getLogger(new Loggable("UartbusCodeUploader"));

	public static final CliEntry<String> CODE = CliEntry.createFirstArgParserEntry
	(
		(e)->e,
		"Code to upload in Intel Hex format",
		"c", "-code"
	);
	
	protected static final CliEntry[] PROG_CLI_ENTRIES =
	{
		RPC_HOST,
		RPC_PORT,
		FROM,
		TO,
		CODE
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
			FROM.tryParseOrDefault(pa, 63)
		);
	}
	
	protected static CodeSegment getCodeFromFile(String sfile) throws IOException
	{
		IntelHexFile file = IntelHexFile.loadFile(sfile);
		
		//check file
		file.assertValid();
				
		List<CodeSegment> css = file.getCode();
		switch(css.size())
		{
		case 0: System.err.println("Ihex file doesn't contains any code.");System.exit(3);
		case 1: break;
		default: System.err.println("Ihex file contains multiple code segments.");System.exit(4);
		}
		
		return css.get(0);
	}
	
	public static void main(String[] args) throws Throwable
	{
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
		UbDevStdNsRoot root = dev.getRpcRoot();
		
		try
		{
			root.getBusFunctions().ping();
		}
		catch(TransactionException e)
		{
			System.err.println("Requested bus device ("+to+") not responding in the bus, maybe not attached.");
			System.exit(2);
		}
		
		
		
		
		
		//TODO check code start address
		

		System.exit(0);
		
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
