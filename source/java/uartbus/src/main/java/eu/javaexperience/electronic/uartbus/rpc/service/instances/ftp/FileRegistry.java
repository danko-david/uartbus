package eu.javaexperience.electronic.uartbus.rpc.service.instances.ftp;

import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import eu.javaexperience.collection.PublisherCollection;
import eu.javaexperience.electronic.uartbus.rpc.service.instances.ftp.FtpBusService.FileEntry;
import eu.javaexperience.file.AbstractFile;
import eu.javaexperience.file.AbstractFileSystem;
import eu.javaexperience.file.FileSystemTools;
import eu.javaexperience.io.file.FileTools;
import eu.javaexperience.regex.RegexTools;
import eu.javaexperience.text.StringTools;

public class FileRegistry
{
	protected AbstractFile root;
	
	protected Map<String, FileEntry> byPath = new HashMap<>();
	protected Map<BigInteger, FileEntry> byId = new HashMap<>();
	protected ListFile fileList;
	
	public FileRegistry(AbstractFile file) throws IOException
	{
		this.root = file;
		AbstractFile fl = root.getFileSystem().fromUri(root.getUrl()+"/.ubfs");
		if(!fl.exists())
		{
			fl.createNewRegularFile();
		}	
		
		fileList = new ListFile(fl);
		if(fileList.known.isEmpty())
		{
			registerNewFile("/", root);
		}
		
		AbstractFileSystem fs = root.getFileSystem();
		String fsr = root.getUrl();
		
		int i=0;
		for(String f:fileList.lines)
		{
			int index = i++;
			FileEntry fe = new FileEntry
			(
				f,
				fs.fromUri(FileTools.normalizeSlashes(fsr+"/"+f)),
				index
			);
			
			byPath.put(f, fe);
			byId.put(fe.fileNo.value, fe);
		}
		
		FileSystemTools.find(root, new PublisherCollection<AbstractFile>()
		{
			@Override
			public boolean add(AbstractFile obj)
			{
				String path = StringTools.getSubstringAfterFirstString(obj.getUrl(), fsr, null);
				if(StringTools.isNullOrEmpty(path) || "/.ubfs".equals(path))
				{
					return false;
				}
				FileEntry f = byPath.get(path);
				if(null == f)
				{
					registerNewFile(path, obj);
				}
				else
				{
					byPath.put(path, f);
					byId.put(f.fileNo.value, f);
				}
				
				return true;
			}
		});
	}
	
	protected static AbstractFile getFileByName(AbstractFile file, String find)
	{
		AbstractFile[] lf = file.listFiles();
		if(null == lf)
		{
			return null;
		}
		
		for(AbstractFile f:lf)
		{
			if(find.equals(f.getFileName()))
			{
				return file;
			}
		}
		
		return null;
	}
	
	protected AbstractFile fetchFile(String... pathComponents)
	{
		AbstractFile crnt = root;
		for(int i=0;i<pathComponents.length;++i)
		{
			String cc = pathComponents[i];
			
			if(0 == cc.length())
			{
				continue;
			}
			
			if(cc.equals(".ubfs"))
			{
				return null;
			}
			
			crnt = getFileByName(crnt, cc);
			
			if(null == crnt)
			{
				return null;
			}
			
		}
		return crnt;
	}
	
	protected synchronized FileEntry registerNewFile(String path, AbstractFile file)
	{
		FileEntry fe = new FileEntry(path, file, fileList.accumulateString(path));
		byPath.put(path, fe);
		byId.put(fe.fileNo.value, fe);
		return fe;
	}
	
	protected synchronized FileEntry getFileEntry(String path)
	{
		path = FileTools.normalizeSlashes("/"+path);
		FileEntry ent = byPath.get(path);
		if(null != ent)
		{
			return ent;
		}
		
		String[] pcs = RegexTools.SLASHES.split(path);
		
		AbstractFile file = fetchFile(pcs);
		if(null != file)
		{
			return registerNewFile(path, file);
		}
		
		return null;
	}
}