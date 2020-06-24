package eu.javaexperience.electronic.uartbus.rpc.service.instances.ftp;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import eu.javaexperience.file.AbstractFile;

public class ListFile implements Closeable
{
	protected List<String> lines = new ArrayList<>();
	protected Set<String> known = new HashSet<>();
	protected PrintWriter out;
	
	public synchronized int accumulateString(String str)
	{
		lines.add(str);
		known.add(str);
		str = URLEncoder.encode(str);
		out.println(str);
		out.flush();
		return known.size()-1;
	}

	public int getStringIndex(String str)
	{
		str = URLEncoder.encode(str);
		if(!known.contains(str))
		{
			return -1;
		}
		
		return lines.indexOf(str);
	}
	
	public ListFile(AbstractFile af) throws IOException
	{
		try
		(
			InputStream in = af.openRead();
			InputStreamReader isr = new InputStreamReader(in);
			BufferedReader br = new BufferedReader(isr);
		)
		{
			String line = null;
			while(null != (line = br.readLine()))
			{
				line = URLDecoder.decode(line);
				lines.add(line);
				known.add(line);
			}
		}
		
		out = new PrintWriter(af.openWrite(true));
	}
	
	@Override
	public synchronized void close()
	{
		out.close();
	}
}