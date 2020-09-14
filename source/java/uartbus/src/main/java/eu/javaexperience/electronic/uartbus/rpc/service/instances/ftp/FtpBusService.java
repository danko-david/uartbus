package eu.javaexperience.electronic.uartbus.rpc.service.instances.ftp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.util.Arrays;

import eu.javaexperience.electronic.uartbus.rpc.client.device.UbDeviceNs;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbIndex;
import eu.javaexperience.electronic.uartbus.rpc.client.types.UbRemoteString;
import eu.javaexperience.electronic.uartbus.rpc.datatype.UbString;
import eu.javaexperience.electronic.uartbus.rpc.datatype.VSigned;
import eu.javaexperience.electronic.uartbus.rpc.datatype.VUnsigned;
import eu.javaexperience.file.AbstractFile;
import eu.javaexperience.file.AbstractFileSystem;
import eu.javaexperience.file.fs.os.OsFile;
import eu.javaexperience.nativ.posix.ERRNO;
import eu.javaexperience.nativ.posix.PosixErrnoException;
import eu.javaexperience.text.StringTools;


public class FtpBusService implements UbFileSystemService
{
	protected FileRegistry fr;
	protected String rootPath;
	protected AbstractFileSystem fs;
	
	public FtpBusService(AbstractFile af) throws IOException
	{
		rootPath = af.getUrl();
		if(rootPath.endsWith("/"))
		{
			rootPath = rootPath.substring(0, rootPath.length()-1);
		}
		
		fs = af.getFileSystem();
		fr = new FileRegistry(af);
	}
	
	protected String getRelPath(AbstractFile af)
	{
		return StringTools.getSubstringAfterFirstString(af.getUrl(), rootPath);
	}
	
	protected AbstractFile relToFile(String path)
	{
		return fs.fromUri(rootPath+path);
	}
	
	protected FileEntry translateFile(AbstractFile file)
	{
		return fr.byPath.get(getRelPath(file));
	}
	
	protected FileEntry ensureTranslateFile(AbstractFile file) throws PosixErrnoException
	{
		String url = getRelPath(file);
		FileEntry ret = fr.byPath.get(url);
		if(null == ret)
		{
			ret = fr.getFileEntry(url);
			if(null == ret)
			{
				throw new PosixErrnoException(ERRNO.ENOENT);
			}
		}
		
		return fr.byPath.get(getRelPath(file));
	}
	
	protected static class FileEntry
	{
		public FileEntry(String path, AbstractFile file, int index)
		{
			this.path = path;
			this.file = file;
			this.fileNo = new VUnsigned(BigInteger.valueOf(index));
			
		}
		protected String path;
		
		protected AbstractFile file;
		protected VUnsigned fileNo;
	}
	
	protected FileEntry fetchFile(VUnsigned fileNo) throws PosixErrnoException
	{
		FileEntry ret = fr.byId.get(fileNo.value);
		if(null == ret)
		{
			throw new PosixErrnoException(ERRNO.ENOENT);
		}
		return ret;
	}
	
	@UbIndex(ns=1)
	public boolean exists(VUnsigned fileNo) throws PosixErrnoException
	{
		return fetchFile(fileNo).file.exists();
	}
	
	@UbIndex(ns=2)
	public VUnsigned getParentFile(VUnsigned fileNo) throws PosixErrnoException
	{
		return ensureTranslateFile(fetchFile(fileNo).file.getParentFile()).fileNo;
	}
	
	@UbIndex(ns=3)
	public byte getFileType(VUnsigned fileNo) throws PosixErrnoException
	{
		AbstractFile f = fetchFile(fileNo).file;
		if(f.isRegularFile())
		{
			return 1;
		}
		if(f.isDirectory())
		{
			return 2;
		}
		
		return 127;
	}

	
	@UbIndex(ns=4)
	public UbRemoteString getFileName(VUnsigned fileNo) throws PosixErrnoException
	{
		return new UbString(fetchFile(fileNo).file.getFileName());
	}
	
	@UbIndex(ns=5)
	public VUnsigned getSize(VUnsigned fileNo) throws PosixErrnoException
	{
		AbstractFile file = fetchFile(fileNo).file;
		return new VUnsigned(BigInteger.valueOf(file.isDirectory()?file.listFiles().length:file.getSize()));
	}
	
	@UbIndex(ns=6)
	public VUnsigned[] listFiles(VUnsigned fileNo) throws PosixErrnoException
	{
		AbstractFile[] fl = fetchFile(fileNo).file.listFiles();
		VUnsigned[] ret = new VUnsigned[fl.length];
		
		int ep = 0;
		for(int i=0;i<ret.length;++i)
		{
			if(!fl[i].getFileName().equals(".ubfs"))
			{
				ret[ep++] = ensureTranslateFile(fl[i]).fileNo;
			}
		}
		
		if(ep != ret.length)
		{
			return Arrays.copyOf(ret, ep);
		}
		
		return ret;
	}
	
	@UbIndex(ns=7)
	public VUnsigned getDirectoryFile(VUnsigned fileNo, VUnsigned index) throws PosixErrnoException
	{
		AbstractFile file = fetchFile(fileNo).file;
		if(!file.isDirectory())
		{
			throw new PosixErrnoException(ERRNO.ENOTDIR);
		}
		
		AbstractFile[] fl = file.listFiles();
		
		int i = index.value.intValue();
		if(fl.length <= i)
		{
			throw new PosixErrnoException(ERRNO.ENOTDIR);
		}
		
		return ensureTranslateFile(fl[i]).fileNo;
	}
	
	@UbIndex(ns=8)
	public boolean canRead(VUnsigned fileNo) throws PosixErrnoException
	{
		return fetchFile(fileNo).file.canRead();
	}
	
	@UbIndex(ns=9)
	public boolean canWrite(VUnsigned fileNo) throws PosixErrnoException
	{
		return fetchFile(fileNo).file.canWrite();
	}
	
	@UbIndex(ns=10)
	public VUnsigned createTime(VUnsigned fileNo) throws PosixErrnoException
	{
		return new VUnsigned(BigInteger.ZERO);//fetchFile(fileNo).file.createTime();
	}
	
	@UbIndex(ns=11)
	public VUnsigned lastModified(VUnsigned fileNo) throws PosixErrnoException
	{
		return new VUnsigned(BigInteger.valueOf(fetchFile(fileNo).file.lastModified()));
	}
	
	//file read functions
	@UbIndex(ns=20)
	public byte[] getFilePart(VUnsigned fileNo, VUnsigned from, VUnsigned length) throws PosixErrnoException
	{
		int l = length.value.intValue();
		if(l > 4096)
		{
			throw new PosixErrnoException(ERRNO.EMSGSIZE);
		}
		
		try(InputStream in = fetchFile(fileNo).file.openRead())
		{
			in.skip(from.value.longValue());
			byte[] ret = new byte[l];
			int all = 0;
			int r = 0;
			while((r = in.read(ret, all, l-all)) > 0)
			{
				all += r;
			};
			
			if(all == l)
			{
				return ret;
			}
			
			return Arrays.copyOf(ret, all);
		}
		catch(Exception e)
		{
			throw new PosixErrnoException(ERRNO.EIO);
		}
	}

	
	
	//file create, filesystem modify
	
	@UbIndex(ns=30)
	public VUnsigned mkdir(VUnsigned fileNo, String str) throws PosixErrnoException
	{
		FileEntry file = fetchFile(fileNo);
		if(!file.file.isDirectory())
		{
			throw new PosixErrnoException(ERRNO.ENOTDIR);
		}
		
		AbstractFile f = relToFile(getRelPath(file.file)+"/"+str);
		if(f.exists())
		{
			throw new PosixErrnoException(ERRNO.EEXIST);
		}
		
		try
		{
			f.mkdir();
		}
		catch(Exception e)
		{
			throw new PosixErrnoException(ERRNO.EACCES);
		}
		
		return ensureTranslateFile(f).fileNo;
	}

	
	@UbIndex(ns=31)
	public VUnsigned createNewRegularFile(VUnsigned fileNo, String str) throws PosixErrnoException
	{
		AbstractFile f = relToFile(getRelPath(fetchFile(fileNo).file)+"/"+str);
		if(f.exists())
		{
			throw new PosixErrnoException(ERRNO.EEXIST);
		}
		
		try
		{
			f.createNewRegularFile();
		}
		catch(Exception e)
		{
			throw new PosixErrnoException(ERRNO.EACCES);
		}
		
		return ensureTranslateFile(f).fileNo;
	}

	
	@UbIndex(ns=32)
	public boolean setLastModified(VUnsigned fileNo, VUnsigned time) throws PosixErrnoException
	{
		try
		{
			fetchFile(fileNo).file.setLastModified(time.value.longValue());
			return true;
		}
		catch(IOException e)
		{
			return false;
		}
	}

	
	@UbIndex(ns=33)
	public boolean delete(VUnsigned fileNo) throws PosixErrnoException
	{
		try
		{
			fetchFile(fileNo).file.delete();
			return true;
		}
		catch(IOException e)
		{
			return false;
		}
	}

	
	@UbIndex(ns=34)
	public VUnsigned moveFile(VUnsigned fileNo, VUnsigned dstDir) throws PosixErrnoException
	{
		//TODO
		return null;
	}

	
	@UbIndex(ns=35)
	public VUnsigned renameFile(VUnsigned fileNo, String name) throws PosixErrnoException
	{
		//TODO
		return null;
	}
	
	@UbIndex(ns=40)
	public VUnsigned truncateSize(VUnsigned fileNo, VUnsigned size) throws PosixErrnoException
	{
		AbstractFile file = fetchFile(fileNo).file;
		if(!(file instanceof OsFile))
		{
			throw new PosixErrnoException(ERRNO.EOPNOTSUPP);
		}
		
		OsFile of = (OsFile) file;
		try(RandomAccessFile raf = new RandomAccessFile(of.getFile(), "rw"))
		{
			raf.setLength(size.value.longValue());
		}
		catch(IOException e)
		{
			throw new PosixErrnoException(ERRNO.ENOENT);
		}
		
		return new VUnsigned(BigInteger.valueOf(file.getSize()));
	}
	
	
	@UbIndex(ns=41)
	public VUnsigned appendFile(VUnsigned fileNo, VSigned poz, byte[] data) throws PosixErrnoException
	{
		long p = poz.value.longValue();
		
		AbstractFile file = fetchFile(fileNo).file;
		if(p < 0)
		{
			try(OutputStream os = file.openWrite(p < 0))
			{
				os.write(data);
				return new VUnsigned(BigInteger.valueOf(file.getSize()));
			}
			catch(IOException e)
			{
				e.printStackTrace();
				throw new PosixErrnoException(ERRNO.EACCES);
			}
		}
		else
		{
			if(!(file instanceof OsFile))
			{
				throw new PosixErrnoException(ERRNO.EOPNOTSUPP);
			}
			
			OsFile of = (OsFile) file;
			try(RandomAccessFile raf = new RandomAccessFile(of.getFile(), "rw"))
			{
				raf.seek(p);
				raf.write(data);
				return new VUnsigned(BigInteger.valueOf(file.getSize()));
			}
			catch(IOException e)
			{
				throw new PosixErrnoException(ERRNO.ENOENT);
			}
		}
	}

	@Override
	public <T extends UbDeviceNs> T cast(Class<T> dst)
	{
		return null;
	}

	@Override
	public <T extends UbDeviceNs> T customNs(Class<T> dst, short num)
	{
		return null;
	}
}
