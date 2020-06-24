package eu.javaexperience.electronic.uartbus.rpc.service.instances.ftp;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbIndex;
import eu.javaexperience.electronic.uartbus.rpc.client.device.VSigned;
import eu.javaexperience.electronic.uartbus.rpc.client.device.VUnsigned;
import eu.javaexperience.electronic.uartbus.rpc.types.UbString;
import eu.javaexperience.nativ.posix.PosixErrnoException;

public interface UbFileSystemService
{
	// File attributes and filesystem discovery
	
	@UbIndex(ns=1)
	public boolean exists(VUnsigned fileNo) throws PosixErrnoException;
	
	@UbIndex(ns=2)
	public VUnsigned getParentFile(VUnsigned fileNo) throws PosixErrnoException;
	
	@UbIndex(ns=3)
	public byte getFileType(VUnsigned fileNo) throws PosixErrnoException;
	
	@UbIndex(ns=4)
	public UbString getFileName(VUnsigned fileNo) throws PosixErrnoException;
	
	@UbIndex(ns=5)
	public VUnsigned getSize(VUnsigned fileNo) throws PosixErrnoException;
	
	@UbIndex(ns=6)
	public VUnsigned[] listFiles(VUnsigned fileNo) throws PosixErrnoException;
	
	@UbIndex(ns=7)
	public VUnsigned getDirectoryFile(VUnsigned fileNo, VUnsigned index) throws PosixErrnoException;
	
	@UbIndex(ns=8)
	public boolean canRead(VUnsigned fileNo) throws PosixErrnoException;
	
	@UbIndex(ns=9)
	public boolean canWrite(VUnsigned fileNo) throws PosixErrnoException;
	
	@UbIndex(ns=10)
	public VUnsigned createTime(VUnsigned fileNo) throws PosixErrnoException;
	
	@UbIndex(ns=11)
	public VUnsigned lastModified(VUnsigned fileNo) throws PosixErrnoException;
	
	
	
	//file read functions
	
	@UbIndex(ns=20)
	public byte[] getFilePart(VUnsigned fileNo, VUnsigned from, VUnsigned length) throws PosixErrnoException;
	
	
	//file create, filesystem modify
	
	@UbIndex(ns=30)
	public VUnsigned mkdir(VUnsigned fileNo, String str) throws PosixErrnoException;
	
	@UbIndex(ns=31)
	public VUnsigned createNewRegularFile(VUnsigned fileNo, String str) throws PosixErrnoException;
	
	@UbIndex(ns=32)
	public boolean setLastModified(VUnsigned fileNo, VUnsigned time) throws PosixErrnoException;
	
	@UbIndex(ns=33)
	public boolean delete(VUnsigned fileNo) throws PosixErrnoException;
	
	@UbIndex(ns=34)
	public VUnsigned moveFile(VUnsigned fileNo, VUnsigned dstDir) throws PosixErrnoException;
	
	@UbIndex(ns=35)
	public VUnsigned renameFile(VUnsigned fileNo, String name) throws PosixErrnoException;
	
	
	//modify file content
	@UbIndex(ns=40)
	public VUnsigned truncateSize(VUnsigned fileNo, VUnsigned size) throws PosixErrnoException;
	
	@UbIndex(ns=41)
	public VUnsigned appendFile(VUnsigned fileNo, VSigned poz, byte[] data) throws PosixErrnoException;
	
}