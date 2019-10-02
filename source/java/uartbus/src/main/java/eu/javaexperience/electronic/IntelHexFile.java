package eu.javaexperience.electronic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.javaexperience.collection.ReadOnlyAndRwCollection;
import eu.javaexperience.file.AbstractFile;
import eu.javaexperience.file.FileSystemTools;
import eu.javaexperience.io.IOTools;
import eu.javaexperience.math.MathTools;
import eu.javaexperience.reflect.Mirror;
import eu.javaexperience.text.Format;
import eu.javaexperience.text.StringTools;

/**
 * https://en.wikipedia.org/wiki/Intel_HEX
 * */
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
			
			length = 0xff & raw[0];
			
			address = ((0xff& raw[1]) << 8) | (0xff & raw[2]);
			
			//hossz (1), tipus(2), rekord tipus(1), adat(n), chksum
			
			recordType = 0xff & raw[3];
			data = Arrays.copyOfRange(raw, 4, raw.length-1);
			chksum = raw[raw.length-1];
		}
		
		protected IntelHexLine()
		{
			
		}
		
		protected byte[] raw;
		
		public byte calcChkSum()
		{
			return checksum(raw, 0, raw.length-1);
		}
		
		@Override
		public String toString()
		{
			return "IntelHexLine: :"+Format.toHex(raw);
		}
		
		int length;
		int address;
		
		/**
		 * 0 = data
		 * 1 = EOF (End Of File)
		 * 2 = Extended Segment Address
		 * 3 = Extended Linear Address
		 * 4 = 	Start Linear Address
		 * */
		int recordType;//TODO enum type
		byte[] data;
		byte chksum;
		
		protected byte[] toRawByData()
		{
			byte[] ret = new byte[data.length+5];
			ret[0] = (byte) data.length;
			ret[1] = (byte) ((address >> 8) & 0xff);
			ret[2] = (byte) (address & 0xff);
			ret[3] = (byte) recordType; 
			
			for(int i=0;i<data.length;++i)
			{
				ret[4+i] = data[i];
			}
			
			ret[ret.length-1] = chksum;
			
			return ret;
		}

		public String toLineString()
		{
			return ":"+Format.toHex(raw).toUpperCase();
		}
	}
	
	public static byte checksum(byte[] data, int from, int to)
	{
		int ret = 0;
		for(int i=from;i<to;++i)
		{
			ret += 0xFF & data[i];
		}
		
		return (byte) ((ret^0xff)+1);
	}
	
	protected static final byte[] EOF_DATA = new byte[] {0,0,0,1, (byte) 0xff};
	
	public IntelHexLine createEofLine()
	{
		IntelHexLine ret = new IntelHexLine();
		ret.raw = EOF_DATA;
		ret.length = 0;
		ret.address = 0;
		ret.recordType = 1;
		ret.data = Mirror.emptyByteArray;
		ret.chksum = (byte)0xff;
		
		return ret;
		
	}
	
	public IntelHexLine createDataLine(int address, byte[] data)
	{
		IntelHexLine ret = new IntelHexLine();
		
		ret.address = address;
		ret.recordType = 0;
		ret.data = data;
		ret.length = data.length;

		byte[] raw = ret.toRawByData();
		ret.chksum = checksum(raw, 0, raw.length-1);
		ret.raw = ret.toRawByData();
		
		return ret;
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
	
	public IntelHexFile(){}
	
	public IntelHexFile(String[] lines)
	{
		List<IntelHexLine> w = this.lines.getWriteable();
		for(int i=0;i<lines.length;++i)
		{
			w.add(new IntelHexLine(lines[i]));
		}
	}
	
	public static IntelHexFile loadFile(String file) throws IOException
	{
		return loadFile(FileSystemTools.DEFAULT_FILESYSTEM.fromUri(file));
	}
	
	public static IntelHexFile loadFile(AbstractFile file) throws IOException
	{
		IntelHexFile ih = new IntelHexFile(new String(IOTools.loadFileContent(file)).split("\n"));
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
	
	public static class CodeSegment
	{
		public static final Comparator<CodeSegment> ORDER_BY_START_ADDRESS =
			(a, b)->Long.compare(a.startAddress, b.startAddress);
			
		public long startAddress;
		public long endAddress;
		public byte[] data;
		
		public CodeSegment(long startAddress)
		{
			this.endAddress = this.startAddress = startAddress;
			baos = new ByteArrayOutputStream();
		}
		
		protected ByteArrayOutputStream baos;
		
		public void appendCode(byte[] data) throws IOException
		{
			baos.write(data);
			endAddress += data.length;
		}
		
		public boolean isMiddleOf(long addr)
		{
			return startAddress < addr && addr < endAddress;
		}
		
		public void endCodeWrite()
		{
			data = baos.toByteArray();
			baos = null;
		}

		public byte[] getCodePiece(int offset, int _length)
		{
			int length = Math.min(_length, data.length-offset);
			return Arrays.copyOfRange(data, offset, offset+length);
		}
	}

	public List<CodeSegment> getCode() throws IOException
	{
		List<CodeSegment> ret = new ArrayList<>();
		for(IntelHexLine l:lines.getReadOnly())
		{
			if(1 == l.recordType)
			{
				break;
			}
			
			if(0 != l.recordType)
			{
				continue;
				//throw new RuntimeException("Unknown intel hexfile record: "+l.recordType);
			}
			
			long s = l.address;
			
			
			CodeSegment seg = null;
			for(CodeSegment cs: ret)
			{
				if(cs.isMiddleOf(s))
				{
					throw new RuntimeException("Data intersect with the previous code: "+l.toString());
				}
				
				if(s == cs.endAddress)
				{
					seg = cs;
					break;
				}
				
			}
			
			if(null == seg)
			{
				seg = new CodeSegment(s);
				ret.add(seg);
			}
			
			seg.appendCode(l.data);
		}
		
		for(CodeSegment cs:ret)
		{
			cs.endCodeWrite();
		}
		
		return ret;
	}
	
	public static List<CodeSegment> concatSegments(List<CodeSegment> codes, int maxGap, byte paddingByte) throws IOException
	{
		if(codes.size() < 2)
		{
			return codes;
		}
		
		byte[] padding = new byte[] {paddingByte};
		
		Collections.sort(codes, CodeSegment.ORDER_BY_START_ADDRESS);
		
		List<CodeSegment> ret = new ArrayList<>();
		for(CodeSegment c:codes)
		{
			long s = c.startAddress;
			
			CodeSegment seg = null;
			for(CodeSegment cs: ret)
			{
				if(cs.isMiddleOf(s))
				{
					throw new RuntimeException("Data intersect with the previous code: "+cs.toString());
				}
				
				if(s+maxGap > cs.endAddress)
				{
					seg = cs;
					break;
				}
			}
			
			if(null == seg)
			{
				seg = new CodeSegment(s);
				ret.add(seg);
			}
			
			long diff = c.startAddress - seg.endAddress;
			if(diff != 0)
			{
				for(int i=0;i<diff;++i)
				{
					seg.appendCode(padding);
				}
			}
			
			seg.appendCode(c.data);
		}
		
		for(CodeSegment cs:ret)
		{
			cs.endCodeWrite();
		}
		
		return ret;
	}

	public static IntelHexFile fromSegments(List<CodeSegment> codes)
	{
		IntelHexFile ret = new IntelHexFile();
		
		for(CodeSegment code:codes)
		{
			int addr = (int) code.startAddress;
			for(int offset = 0;offset < code.endAddress-code.startAddress;)
			{
				int to = MathTools.clamp(0, offset+16, (int) code.endAddress);
				ret.lines.getWriteable().add(ret.createDataLine(addr+offset, Arrays.copyOfRange(code.data, offset, to)));
				offset = to;
			}
		}
		
		ret.lines.getWriteable().add(ret.createEofLine());
		
		ret.assertValid();
		return ret;
	}

	public void writeToFile(String file) throws IOException
	{
		writeToFile(FileSystemTools.DEFAULT_FILESYSTEM.fromUri(file));
	}
	
	public void writeToFile(AbstractFile file) throws IOException
	{
		try(OutputStream w = file.openWrite(false))
		{
			for(IntelHexLine line:lines.getReadOnly())
			{
				w.write(line.toLineString().getBytes());
				w.write('\n');
			}
			w.flush();
		}
	}
}
