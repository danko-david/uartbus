package eu.javaexperience.electronic.build;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import eu.javaexperience.datareprez.DataCommon;
import eu.javaexperience.datareprez.DataObject;
import eu.javaexperience.electronic.build.GccBuilderContext.GccBuildInstruction;
import eu.javaexperience.io.file.FileTools;

public class GccBuildProfile
{
	protected GccBuilderContext ctx;
	protected String name;
	
	protected GccBuildInstruction defaults = new GccBuildInstruction();
	
	//TODO different modes for individual/group of source files
	
	public void ensureBuildReady() throws IOException
	{
		//ultimate takes the care of the whole compilation
		getUltimateObjectFile().ensureNewestCompiled();
	}
	
	public ObjectFile getObject(String srcName)
	{
		File src = new File(ctx.directory+ctx.fetchedCtx.sourcesLocation+srcName);
		if(!src.exists())
		{
			return null;
		}
		
		ObjectFile of = new ObjectFile();
		of.prof = this;
		of.source = srcName;
		of.fSource = src;
		of.target = new File(ctx.directory+"/builds/"+name+"/objects/"+srcName+".o");
		
		return of;
	}
	
	public ObjectFile getUltimateObjectFile() throws IOException
	{
		File f = new File(ctx.directory+"/builds/"+name+"/ultimate.o");
		
		ObjectFile ret = new ObjectFile();
		ret.prof = this;
		ret.source = null;
		ret.target = f;
		
		return ret;
	}

	public Set<String> getFlags()
	{
		return defaults.flags;
	}
	
	public void touch()
	{
		new File(ctx.directory+"/builds/"+name+"/objects/").mkdirs();
	}
	
	public void clean()
	{
		FileTools.deleteDirectory(new File(ctx.directory+"/builds/"+name+"/"), false);
	}

	public DataObject toObject(DataCommon proto)
	{
		DataObject ret = proto.newObjectInstance();
		
		ret.putObject("defaults", defaults.toObject(proto));
		
		//TODO other modes
		return ret;
	}

	public static GccBuildProfile parse(GccBuilderContext ctx, DataObject object)
	{
		// TODO Auto-generated method stub
		return null;
	}
}