package eu.javaexperience.electronic.build;

import java.io.File;

import eu.javaexperience.text.StringTools;

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
}
