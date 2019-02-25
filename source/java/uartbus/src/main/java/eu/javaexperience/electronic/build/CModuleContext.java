package eu.javaexperience.electronic.build;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import eu.javaexperience.datareprez.DataCommon;
import eu.javaexperience.datareprez.DataObject;
import eu.javaexperience.semantic.references.MayNull;

public class CModuleContext
{
	protected VariableFetch variables;
	
	protected GccPrograms progs;
	
	//TODO variables for eg: includeDirectoies, Gcc tools, header source location
	//variables might based on lookups (location probes), prompt, ENV_var, properties, CLI vars
	
	//TODO fetch rel file
	protected String sourcesLocation = "./src/main/c/";
	
	protected String headersLocation = "./src/main/public/";
	
	protected String buildsLocation = "./builds/";
	
	protected Map<String, GccBuildProfile> builds = new HashMap<>();
	
	//TODO maybe remove this, include directories might should be imported as an dependency.
	protected Set<String> includeDirectories = new HashSet<>();
	
	public static CModuleContext parse(@MayNull CModuleContext defaults, DataObject obj)
	{
		if(null == defaults)
		{
			defaults = new CModuleContext();
		}
		
		
		return null;
	}
	
	public static DataObject toObject(DataCommon obj)
	{
		return null;
	}
	
	public CModuleContext substitue(GccBuilderContext ctx, VariableFetch fetch)
	{
		//TODO
		return null;
	}
	
	public CModuleContext copy()
	{
		//TODO
		return null;
	}

	//constains the fetched vartiables (not varaibles should appear at eny field)
	public VariableFetch getSubstitueVariables()
	{
		return null;
	}
}
