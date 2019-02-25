package eu.javaexperience.electronic.build;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import eu.javaexperience.process.ProcessTools;
import eu.javaexperience.reflect.Mirror;

public class ObjectFile
{
	protected GccBuildProfile prof;
	protected String source;
	
	protected File fSource; 
	protected File target;
	
	public void compileUnit() throws IOException
	{
		ArrayList<String> prog = new ArrayList<>();
		prog.add(prof.ctx.fetchedCtx.progs.gcc);
		prog.addAll(prof.defaults.flags);
		prog.add("-I"+prof.ctx.directory+prof.ctx.fetchedCtx.headersLocation);
		for(String id:prof.ctx.fetchedCtx.includeDirectories)
		{
			prog.add("-I"+id);
		}
		prog.add(fSource.toString());
		prog.add("-o");
		prog.add(target.toString());
		try
		{
			ProcessTools.assertedProcessExitStatus(prog.toArray(Mirror.emptyStringArray));
			target.setLastModified(fSource.lastModified());
		}
		catch(Exception e)
		{
			Mirror.propagateAnyway(e);
		}
	}
	
	public void ensureNewestCompiled() throws IOException
	{
		if(null == fSource)
		{
			ArrayList<String> prog = new ArrayList<>();
			prog.add(prof.ctx.fetchedCtx.progs.ar);
			prog.add("rcs");
			prog.add(target.toString());
			
			long tmax = 0;
			
			for(String s:prof.ctx.listSources())
			{
				ObjectFile obj = prof.getObject(s);
				//TODO compile concurrently
				obj.ensureNewestCompiled();
				tmax = Math.max(tmax, obj.target.lastModified());
				prog.add(obj.target.toString());
			}
			
			try
			{
				ProcessTools.assertedProcessExitStatus(prog.toArray(Mirror.emptyStringArray));
				target.setLastModified(tmax);
			}
			catch(Exception e)
			{
				Mirror.propagateAnyway(e);
			}
			//need to link
		}
		else if(fSource.lastModified() != target.lastModified())
		{
			compileUnit();
		}
	}
}