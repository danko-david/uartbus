package eu.javaexperience.electronic.build;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eu.javaexperience.collection.CollectionTools;
import eu.javaexperience.collection.PublisherCollection;
import eu.javaexperience.io.file.FileTools;
import eu.javaexperience.process.ProcessTools;
import eu.javaexperience.reflect.Mirror;
import eu.javaexperience.text.StringTools;

/**
 * 
 * 
	required tools:
		- compiler, linker, objdump

	- file headers
		- avr header (or 8051, arm)
		- uartbus header
		- arduino headers
		
	- build environment (flags)
		-D switches
		- microcontroller variable
		
	- libraries to compile
		- uartbus CPP
		- 
	
	- libraries to link
		- uartbus
		- arduino
		- uartbus_rpc
		
	
	make able to build:
		- C file set with environment and result an archive file  

 * Build scheme:
 * 
 * .progs: Map: unit => programPath
 * .sources: Set files
 * .headers: Set files
 * .builds: Map: name => Map: dependeny, dep_build_name, options=> value
 * 
 * files/	/sources
 * 			/headers
 * 
 * builds/{build_name}	/objects/dir/function.c.o
 *						/ultimate.o
 * 
 * */
public class GccBuilderContext implements Closeable
{
	protected final File directory;
	
	protected GccPrograms progs;
	
	protected Map<String, String> builds = new HashMap<>();
	
	protected Set<String> includeDirectories = new HashSet<>();
	
	public class GccBuildProfile
	{
		protected String name;
		
		protected Set<Map<String, String>> dependencies;
		
		protected Set<String> flags = new HashSet<>();
		
		public void ensureBuildReady() throws IOException
		{
			//ultimate takes the care of the whole compilation
			getUltimateObjectFile().ensureNewestCompiled();
		}
		
		public ObjectFile getObject(String srcName)
		{
			File src = new File(directory+"/files/sources/"+srcName);
			if(!src.exists())
			{
				return null;
			}
			
			ObjectFile of = new ObjectFile();
			of.prof = this;
			of.source = srcName;
			of.fSource = src;
			of.target = new File(directory+"/builds/"+name+"/objects/"+srcName+".o");
			
			return of;
		}
		
		public ObjectFile getUltimateObjectFile() throws IOException
		{
			File f = new File(directory+"/builds/"+name+"/ultimate.o");
			
			ObjectFile ret = new ObjectFile();
			ret.prof = this;
			ret.source = null;
			ret.target = f;
			
			return ret;
		}

		public Set<String> getFlags()
		{
			return flags;
		}
		
		public void touch()
		{
			new File(directory+"/builds/"+name+"/objects/").mkdirs();
		}
		
		public void clean()
		{
			FileTools.deleteDirectory(new File(directory+"/builds/"+name+"/"), false);
		}
	}
	
	public class ObjectFile
	{
		protected GccBuildProfile prof;
		protected String source;
		
		protected File fSource; 
		protected File target;
		
		public void compileUnit() throws IOException
		{
			ArrayList<String> prog = new ArrayList<>();
			prog.add(progs.gcc);
			prog.addAll(prof.flags);
			prog.add("-I"+directory+"/files/headers/");
			for(String id:includeDirectories)
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
			catch (InterruptedException e)
			{
				Mirror.propagateAnyway(e);
			}
		}
		
		public void ensureNewestCompiled() throws IOException
		{
			if(null == fSource)
			{
				ArrayList<String> prog = new ArrayList<>();
				prog.add(progs.ar);
				prog.add("rcs");
				prog.add(target.toString());
				
				long tmax = 0;
				
				for(String s:listSources())
				{
					ObjectFile obj = prof.getObject(s);
					obj.ensureNewestCompiled();
					tmax = Math.max(tmax, obj.target.lastModified());
					prog.add(obj.target.toString());
				}
				
				try
				{
					ProcessTools.assertedProcessExitStatus(prog.toArray(Mirror.emptyStringArray));
					target.setLastModified(tmax);
				}
				catch (InterruptedException e)
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
	
	public GccBuilderContext(File directory)
	{
		this.directory = directory;
	}
	
	public void load()
	{
		
	}
	
	public void save()
	{
		
		
		//if a build removed, remove also it's directory itself
	}
	
	public void reinitializeContainer()
	{
		 new File(directory+"/files/sources/").mkdirs();
		 new File(directory+"/files/headers/").mkdirs();
	}
	
	public static GccBuilderContext acquireDirectory(File f)
	{
		boolean newlyCreated = false;
		if(!f.exists())
		{
			newlyCreated = f.mkdirs();
			if(!f.exists())
			{
				throw new RuntimeException("Can't create directory!");
			}
		}
		else if(!f.isDirectory())
		{
			throw new RuntimeException("Given file is exists but not a directory!");
		}
		
		GccBuilderContext ctx = new GccBuilderContext(f);
		if(newlyCreated)
		{
			ctx.reinitializeContainer();
		}
		else
		{
			ctx.load();
		}
		
		return ctx;
	}
	
	protected static List<String> listRelFiles(String root)
	{
		ArrayList<String> ret = new ArrayList<>();
		FileTools.find(new File(root), new PublisherCollection<File>()
		{
			@Override
			public boolean add(File f)
			{
				if(f.isFile())
				{
					ret.add(StringTools.getSubstringAfterFirstString(f.toString(), root));
					return true;
				}
				return false;
			}
		});
		
		return ret;
	}
	
	public List<String> listSources()
	{
		return listRelFiles(directory+"/files/sources/");
	}
	
	public List<String> listHeaders()
	{
		return listRelFiles(directory+"/files/headers/");
	}
	
	protected static void copyOrLink(boolean linkOnly, String root, File f, String fe)
	{
		try
		{
			ProcessTools.assertedProcessExitStatus(linkOnly?"ln":"cp", f.toString(), root+fe);
		}
		catch(Exception e)
		{
			Mirror.propagateAnyway(e);
		}
	}
	
	public void addHeader(boolean linkOnly, File f, String fe)
	{
		copyOrLink(linkOnly, directory+"/files/headers/", f, fe);
	}

	public void addSource(boolean linkOnly, File f, String fe)
	{
		copyOrLink(linkOnly, directory+"/files/sources/", f, fe);
	}
	
	
	public GccBuildProfile getBuildProfile(String name, boolean createIfNotExists)
	{
		GccBuildProfile prof = new GccBuildProfile();
		prof.name = name;
		
		//TODO create
		prof.touch();
		
		return prof;
	}
	
	public static void main(String[] args) throws Throwable
	{
		//GccBuilderContext ctx = ArduinoGccTools.createArduinoBuildContext("/usr/share/arduino/", "atmega328p", "16000000");
		GccPrograms progs = ArduinoGccTools.pickupArduinoPrograms("/usr/share/arduino/");
		String mcu = "atmega328p";
		String freq = "16000000";
		
		
		GccBuilderContext ctx = acquireDirectory(new File("/tmp/compile/arduino/"));
		ctx.progs = progs;
		File root = new File("/usr/share/arduino/hardware/arduino/cores/arduino/");
		
		for(File f:root.listFiles())
		{
			String sf = f.toString();
			String fe = StringTools.getSubstringAfterFirstString(sf, "/usr/share/arduino/hardware/arduino/cores/arduino/");
			if(fe.endsWith(".cpp") || fe.endsWith(".c"))
			{
				ctx.addSource(false, f, fe);
			}
			else if(sf.endsWith(".h"))
			{
				ctx.addHeader(false, f, fe);
			}
		}
		
		ctx.includeDirectories.add("/usr/share/arduino/hardware/arduino/variants/standard");
		
		GccBuildProfile prof = ctx.getBuildProfile(mcu+"_16Mhz", true);
		CollectionTools.inlineAdd
		(
			prof.getFlags(),
			"-c",
			"-g",
			"-Os",
			"-mmcu="+mcu,
			"-DF_CPU="+freq
		);
		
		prof.ensureBuildReady();
		
		/*"-ffunction-sections",
		"-fdata-sections",*/
	
		
	}

	@Override
	public void close() throws IOException
	{
		
	}
}
