package eu.javaexperience.electronic.build;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONObject;

import java.util.Set;

import eu.javaexperience.collection.PublisherCollection;
import eu.javaexperience.collection.list.NullList;
import eu.javaexperience.datareprez.DataArray;
import eu.javaexperience.datareprez.DataCommon;
import eu.javaexperience.datareprez.DataObject;
import eu.javaexperience.datareprez.DataReprezTools;
import eu.javaexperience.datareprez.jsonImpl.DataObjectJsonImpl;
import eu.javaexperience.io.IOTools;
import eu.javaexperience.io.file.FileTools;
import eu.javaexperience.process.ProcessTools;
import eu.javaexperience.reflect.Mirror;
import eu.javaexperience.text.StringTools;

/**
 * The point of this CModule stuff:
 * 	- Declarative building type:
 * 		- must be declarative about the source structure rather than functional
 * 			like make. (This way used to )
 * 	- an infrastructure that we can bound to a project without touching it's
 * 		  repositroy
 * 	- This declarative stuff 
 * 
 * 
 * 
 * 	Final baptism by fire goal:
 * 	1) Create CModule descriptor for some
 * 		internal (uartbus, ub_app, utils...) and some external libarary
 * 		(arduino-avr, AltSoftSerial) and x86_64 executables as
 * 		module (avr-gcc, sdcc).
 * 
 *  2) Check out the stable version of these projects. (On externals where
 *  	it's available)
 *  
 *  3) Propagate back build profiles like:
 *  	A)	- app for "bus host 1" (profile: atmega328_16Mhz_ub1)
 *  			depends:
 *  				- ub_app => atmega328
 *  				- ub_bus_isp => atmega328  
 *  		- ub_app (backprop profile: atmega328, but without 16Mhz and ub1
 *  			definitions)
 *  		- ub_bus_isp -||- 
 *   	 
 * 		B) same scheme like previous but with sdcc for 8051
 * 
 * 
 *  4) build the project with dependencies and the the final 
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
 * 
 * 
 * download sources from github: https://github.com/arduino/ArduinoCore-avr
 * 
 * 
 * */
public class GccBuilderContext implements Closeable
{
	protected final File directory;
	
	protected String name;
	
	protected CModuleContext originCtx;
	
	protected CModuleContext fetchedCtx;
	
	protected Map<String, Object> extraInformations;
	
	
	public static class GccBuildInstruction
	{
		//TODO later this might have other Gcc tools to target different platforms with the same library.
		
		//TODO file unit selector: exact string glob, regex, enumeration
		protected Set<Map<String, String>> dependencies = new HashSet<>();
		protected Set<String> flags = new HashSet<>();
		
		public DataObject toObject(DataCommon proto)
		{
			DataObject ret = proto.newObjectInstance();
			DataReprezTools.put(ret, "dependencies", dependencies);
			DataReprezTools.put(ret, "flags", flags);
			return ret;
		}
	}
	
	public GccBuilderContext(File directory)
	{
		this.directory = directory;
	}
	
	public void reset()
	{
		//TODO
	}
	
	public void load() throws IOException
	{
		DataObject src = null;
		try(InputStream is = new FileInputStream(directory+"/CModule"))
		{
			src = DataObjectJsonImpl.receiveObject(is);
		}
		
		name = src.getString("name");
		originCtx.variables = VariableFetch.parse(src.getObject("variables"));
		originCtx.sourcesLocation = src.getString("sourcesLocation");
		originCtx.headersLocation = src.getString("headersLocation");
		
		{
			originCtx.includeDirectories = new HashSet<>();
			DataArray arr = src.getArray("includeDirectories");
			for(int i=0;i<arr.size();++i)
			{
				originCtx.includeDirectories.add(arr.getString(i));
			}
		}

		originCtx.progs = GccPrograms.parse(src.getObject("programs"));
		
		DataObject bs = src.getObject("builds");
		for(String s:bs.keys())
		{
			originCtx.builds.put(s, GccBuildProfile.parse(this, bs.getObject(s)));
		}
		
		
		fetchedCtx = originCtx.substitue(this, originCtx.getSubstitueVariables());
	}
	
	public String getProperty(String var)
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	public DataObject toObject(DataCommon proto)
	{
		DataObject out = proto.newObjectInstance();
		
		out.putString("name", name);
		out.putObject("variables", originCtx.variables.toObject(proto));
		out.putString("sourcesLocation", originCtx.sourcesLocation);
		out.putString("headersLocation", originCtx.headersLocation);
		
		out.putObject("programs", originCtx.progs.toObject(proto));
		
		DataObject bs = out.newObjectInstance();
		for(Entry<String, GccBuildProfile> kv:originCtx.builds.entrySet())
		{
			bs.putObject(kv.getKey(), kv.getValue().toObject(proto));
		}
		
		out.putObject("builds", bs);
		
		DataReprezTools.put(out, "includeDirectories", originCtx.includeDirectories);
		
		return out;
	}
	
	public void save() throws IOException
	{
		JSONObject obj = (JSONObject) toObject(DataObjectJsonImpl.instane).getImpl();
		IOTools.putFileContent(directory+"/CModule", obj.toString(1).getBytes());
	}
	
	public void reinitializeContainer()
	{
		originCtx = new CModuleContext();
		originCtx.progs = new GccPrograms("$PROG_GCC", "$PROG_GPP", "$PROG_AR", "$PROG_OBJDUMP");
		fetchedCtx = originCtx.copy();
		new File(directory+fetchedCtx.sourcesLocation).mkdirs();
		new File(directory+fetchedCtx.headersLocation).mkdirs();
	}
	
	public static GccBuilderContext acquireDirectory(File f) throws IOException
	{
		if(!f.exists())
		{
			f.mkdirs();
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
		if(!new File(f, "./CModule").exists())
		{
			ctx.reinitializeContainer();
		}
		else
		{
			ctx.load();
		}
		
		return ctx;
	}
	
	public static GccBuilderContext loadExistingConfig(File f) throws IOException
	{
		File cfg = new File(f, "./CModule");
		if(!cfg.exists())
		{
			throw new RuntimeException("Not a CModule directory: `"+f.toString()+"` initialize directory first.");
		}
		
		return acquireDirectory(f);
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
		return listRelFiles(directory+fetchedCtx.sourcesLocation);
	}
	
	public List<String> listHeaders()
	{
		return listRelFiles(directory+fetchedCtx.headersLocation);
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
	
	public void copyHeader(boolean linkOnly, File f, String fe)
	{
		copyOrLink(linkOnly, directory+fetchedCtx.headersLocation, f, fe);
	}

	public void copySource(boolean linkOnly, File f, String fe)
	{
		copyOrLink(linkOnly, directory+fetchedCtx.sourcesLocation, f, fe);
	}
	
	public GccBuildProfile getBuildProfile(String name, boolean createIfNotExists)
	{
		GccBuildProfile ret = fetchedCtx.builds.get(name);
		if(null != ret)
		{
			return ret;
		}
		
		GccBuildProfile prof = new GccBuildProfile();
		prof.name = name;
		prof.touch();
		fetchedCtx.builds.put(prof.name, prof);
		originCtx.builds.put(prof.name, prof);
		
		return prof;
	}

	@Override
	public void close() throws IOException
	{
		save();
	}

	public List<GccBuilderContext> getParentContextes()
	{
		return NullList.instance;
	}
	
	public String passTroughtValue(String variable)
	{
		return fetchedCtx.variables.passTroughtValue(variable, this);
	}
	
	public String fetchVariable(String var)
	{
		return fetchedCtx.variables.fetch(var, this);
	}

	public File relFile(String path)
	{
		return new File(directory, path);
	}

	public VariableFetch getVariables()
	{
		return originCtx.variables;
	}
}
