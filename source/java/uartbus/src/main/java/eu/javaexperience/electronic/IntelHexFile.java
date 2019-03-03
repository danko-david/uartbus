package eu.javaexperience.electronic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.javaexperience.collection.ReadOnlyAndRwCollection;
import eu.javaexperience.io.IOTools;
import eu.javaexperience.reflect.Mirror;
import eu.javaexperience.rpc.JavaClassRpcUnboundFunctionsInstance;
import eu.javaexperience.rpc.RpcFacility;
import eu.javaexperience.rpc.cli.RpcCliTools;
import eu.javaexperience.text.Format;
import eu.javaexperience.text.StringTools;

public class IntelHexFile
{
	protected static final String H = "[0-9a-zA-Z]";
	protected static final Pattern IHEX_MINIMAL_LINE_PATTERN = Pattern.compile(H+"{10,}");

	public class IntelHexLine
	{
		public IntelHexLine(String line)
		{
			line = line.trim();
			if(!line.startsWith(":"))
			{
				throw new RuntimeException("IHEX line must start with ':'. Content:"+line);
			}
			
			line = StringTools.getSubstringAfterFirstString(line, ":", null);
			
			Matcher m = IHEX_MINIMAL_LINE_PATTERN.matcher(line);
			if(!m.find())
			{
				throw new RuntimeException("Not an IHEX line");
			}
			raw = Format.fromHex(line);
			
			lenght = 0xff & raw[0];
			
			address = ((0xff& raw[1]) << 8) | (0xff & raw[2]); 
			
			recordType = 0xff & raw[3];
			data = Arrays.copyOfRange(raw, 4, raw.length-1);
			chksum = raw[raw.length-1];
		}
		
		protected byte[] raw;
		
		public byte calcChkSum()
		{
			int ret = 0;
			for(int i=0;i<raw.length-1;++i)
			{
				ret += 0xFF & raw[i];
			}
			
			return (byte) (((ret)^0xff)+1);
		}
		
		int lenght;
		int address;
		int recordType;//TODO enum type
		byte[] data;
		byte chksum;
	}
	
	public ReadOnlyAndRwCollection<List<IntelHexLine>> lines = ReadOnlyAndRwCollection.createList(); 
	
	public void assertValid()
	{
		//address occurs only once
		//ends with EOP line ":00000001FF"
		for(IntelHexLine line:lines.getReadOnly())
		{
			byte origin  = line.chksum;
			byte calc = line.calcChkSum();
			if(origin != calc)
			{
				throw new RuntimeException("Checksum mismatch: :"+Format.toHex(line.raw)+" | "+toHex(origin)+" != "+toHex(calc));
			}
		}
	}
	
	public static String toHex(byte data)
	{
		return Format.toHex(new byte[]{data});
	}
	
	public IntelHexFile(String[] lines)
	{
		List<IntelHexLine> w = this.lines.getWriteable();
		for(int i=0;i<lines.length;++i)
		{
			w.add(new IntelHexLine(lines[i]));
		}
	}
	
	public static void main(String[] args) throws Throwable
	{
		RpcFacility rpc = new JavaClassRpcUnboundFunctionsInstance<>(new IntelHexFilesCli(), IntelHexFilesCli.class);
		if(0 == args.length)
		{
			System.err.println(RpcCliTools.generateCliHelp(rpc));
			System.exit(1);
		}
		RpcCliTools.cliExecute(null, rpc, args);
	}
	
	public static IntelHexFile loadFile(String file) throws IOException
	{
		ArrayList<String> lines = new ArrayList<>();
		IOTools.loadFillAllLine(file, lines);
		IntelHexFile ih = new IntelHexFile(lines.toArray(Mirror.emptyStringArray));
		ih.assertValid();
		return ih;
	}
	
	public int getDataSize()
	{
		int ret = 0;
		for(IntelHexLine l:lines.getReadOnly())
		{
			ret += l.data.length;
		}
		return ret;
	}
	
	public static class IntelHexFilesCli
	{
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
	}
}
