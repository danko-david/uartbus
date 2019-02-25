package eu.javaexperience.electronic.build;

import java.io.File;

import eu.javaexperience.datareprez.DataCommon;
import eu.javaexperience.datareprez.DataObject;

public class GccPrograms
{
	protected String gcc;
	protected String gpp;
	protected String ar;
	protected String objcopy;
	
	public GccPrograms
	(
		String gcc, 
		String gpp,
		String ar,
		String objcopy
	)
	{
		this.gcc = gcc;
		this.gpp = gpp;
		this.ar = ar;
		this.objcopy = objcopy;
	}
	
	public String getGcc()
	{
		return gcc;
	}
	
	public String getGpp()
	{
		return gpp;
	}
	
	public String getAr()
	{
		return ar;
	}
	
	public String getObjCopy()
	{
		return objcopy;
	}
	
	protected void appendIfFileNotExists(StringBuilder sb, String f)
	{
		if(!new File(f).exists())
		{
			if(0 == sb.length())
			{
				sb.append("\n");
			}
			sb.append("GCC program `"+f+"` doesn't exists.");
		}
	}
	
	public void ensureProgramsExists()
	{
		StringBuilder sb = new StringBuilder();
		appendIfFileNotExists(sb, gcc);
		appendIfFileNotExists(sb, gpp);
		appendIfFileNotExists(sb, ar);
		appendIfFileNotExists(sb, objcopy);
		if(sb.length() > 0)
		{
			throw new RuntimeException(sb.toString());
		}
	}

	public DataObject toObject(DataCommon proto)
	{
		DataObject ret = proto.newObjectInstance();
		
		ret.putString("gcc", gcc);
		ret.putString("gpp", gpp);
		ret.putString("ar", ar);		
		ret.putString("objcopy", objcopy);
		
		return ret;
	}

	public static GccPrograms parse(DataObject src)
	{
		return new GccPrograms
		(
			src.getString("gcc"),
			src.getString("gpp"),
			src.getString("ar"),
			src.getString("objcopy")
		);
	}
}
