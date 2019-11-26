package eu.javaexperience.electronic;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import eu.javaexperience.cli.CliEntry;
import eu.javaexperience.cli.CliTools;
import eu.javaexperience.collection.map.MapTools;
import eu.javaexperience.collection.map.SmallMap;
import eu.javaexperience.electronic.IntelHexFile.CodeSegment;
import eu.javaexperience.rpc.JavaClassRpcUnboundFunctionsInstance;
import eu.javaexperience.rpc.RpcFacility;
import eu.javaexperience.rpc.cli.RpcCliTools;

public class IhexCli
{
	public static void main(String[] args) throws Throwable
	{
		RpcFacility rpc = new JavaClassRpcUnboundFunctionsInstance<>(new IhexCli(), IhexCli.class);
		if(0 == args.length)
		{
			System.err.println(RpcCliTools.generateCliHelp(rpc));
			System.exit(1);
		}
		RpcCliTools.cliExecute(null, rpc, args);
	}
	
	public void validate(String file) throws Exception
	{
		try
		{
			IntelHexFile f = IntelHexFile.loadFile(file);
		}
		catch(Exception e)
		{
			System.out.println("Not valid.");
			System.exit(1);
		}
		System.out.println("Valid.");
		System.exit(0);
	}
	
	public void size(String file) throws Exception
	{
		IntelHexFile f = IntelHexFile.loadFile(file);
		System.out.println(f.getDataSize()+" bytes");
	}
	
	/**
	 * First version for atmega
	 * -i inputFile
	 * -o outputFile (optional)
	 * -d offsetAddress
	 * -a (address to replace) (optional, is missing the values appears most will be replaced)
	 * */
	public void replace_interrupts(String... params) throws Exception
	{
		//-i inputFile -o outFile -d (offset address)
		
		Map<String, List<String>> pa = CliTools.parseCliOpts(params);
		
		CliEntry<String> IN = CliEntry.createFirstArgParserEntry((e)->e, "Input ihex format file", "i", "-input-file");
		CliEntry<String> OUT = CliEntry.createFirstArgParserEntry((e)->e, "Output ihex format file", "o", "-output-file");
		CliEntry<Integer> OFF = CliEntry.createFirstArgParserEntry(Integer::parseInt, "Program code offset", "p", "-program-offset");
		CliEntry<Integer> ADDR = CliEntry.createFirstArgParserEntry(Integer::parseInt, "Address to replace (optional)", "a", "-address-to-replace");
		
		CliEntry[] cli_entries = {IN, OUT, OFF, ADDR};
		
		String un = CliTools.getFirstUnknownParam(pa, cli_entries);
		
		if(null != un)
		{
			System.err.println("Invalid Cli switch: "+un);
			System.err.println("Usage of ihex.replace_interrupts:\n");
			System.err.println(CliTools.renderListAllOption(cli_entries));
			System.exit(1);
		}
		
		String from = IN.tryParse(pa); 
		String to = OUT.tryParseOrDefault(pa, null); 
		
		Integer offset = OFF.tryParse(pa);
		
		Integer address = ADDR.tryParseOrDefault(pa, null);
		
		boolean fail = false;
		
		if(null == from)
		{
			System.err.println("Ihex file not specified, use the -i cli switch to do that.");
			fail = true;
		}
		
		if(null != from && ! new File(from).exists())
		{
			System.err.println("Ihex file `"+from+"` not exist!");
			fail = true;
		}
		
		if(null == offset)
		{
			System.err.println("Program relay offset not specified, use the -p cli switch to do that.");
			fail = true;
		}
		
		if(offset < 0 || offset >= 0x1 << 16)
		{
			System.err.println("Specified program relay offset `+offset+` is outside of the memory region (0-65536).");
			fail = true;
		}
		
		if(fail)
		{
			System.exit(2);
		}
		
		if(null == to)
		{
			to = from;
		}
		
		IntelHexFile file = IntelHexFile.loadFile(from);
		
		List<CodeSegment> codes = file.getCode();
		CodeSegment start = null;
		for(CodeSegment code:codes)
		{
			if(0 == code.startAddress)
			{
				start = code;
				break;
			}
		}
		
		if(null == start)
		{
			System.err.println("Ihex doesn't contains code segment starts from 0 address.");
			System.exit(3);
		}
		
		int rewite = -1;
		
		if(null != address)
		{
			rewite = address;
		}
		else
		{
			//find the most used destination address to rewrite.
			//start from the 4th index because first address is the reset vector
			
			SmallMap<Integer, Integer> dstAddresses = new SmallMap<>();
			int addr;
			for(addr = 4;addr<start.data.length;addr+=4)
			{
				if
				(
					((byte) 0x0c) != start.data[addr]
				||
					((byte) 0x94) != start.data[addr+1]
				)
				{
					break;
				}
				
				//little endian to big endian
				int dst = ((start.data[addr+3] << 8) | start.data[addr+2]) * 2;
				
				MapTools.incrementCount(dstAddresses, dst);
			}
			
			if(dstAddresses.isEmpty())
			{
				System.err.println("No interrupt function found between 4 and "+addr+" address");
				System.exit(3);
			}
			
			int rDstAddr = 0;
			int rDstAddrCount = 0;
			
			for(Entry<Integer, Integer> kv:dstAddresses.entrySet())
			{
				if(kv.getValue() == rDstAddrCount)
				{
					System.err.println("Multiple interrupt addresses used for the same time so specify explicitly the right one: "+dstAddresses);
					System.exit(4);
					
				}
				else if(kv.getValue() > rDstAddrCount)
				{
					rDstAddr = kv.getKey();
					rDstAddrCount = kv.getValue();
				}
			}
			
			if(0 == rDstAddrCount)
			{
				System.out.println("Nothing to rewrite");
				System.exit(5);
			}
			
			rewite = rDstAddr;
		}
		
		System.out.println("Rewriting: "+rewite);
		
		for(int addr = 4;addr<start.data.length;addr+=4)
		{
			if
			(
				((byte) 0x0c) != start.data[addr]
			||
				((byte) 0x94) != start.data[addr+1]
			)
			{
				break;
			}
			
			//little endian to big endian
			int jmp = ((start.data[addr+3] << 8) | start.data[addr+2]) * 2;
			
			if(jmp == rewite)
			{
				jmp = (addr+offset)/2;//because address points to a code word
				start.data[addr+2] = (byte) (jmp & 0xff);
				start.data[addr+3] =  (byte) ((jmp >> 8) & 0xff);
			}
		}
		
		//TODO assemble ihex and write
		IntelHexFile dst = IntelHexFile.fromSegments(codes);
		dst.writeToFile(to);
	}
}
