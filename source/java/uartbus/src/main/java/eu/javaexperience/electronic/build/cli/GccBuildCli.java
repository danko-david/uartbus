package eu.javaexperience.electronic.build.cli;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import org.json.JSONObject;

import eu.javaexperience.collection.CollectionTools;
import eu.javaexperience.datareprez.jsonImpl.DataObjectJsonImpl;
import eu.javaexperience.electronic.build.GccBuilderContext;
import eu.javaexperience.generic.annotations.Ignore;
import eu.javaexperience.rpc.JavaClassRpcUnboundFunctionsInstance;
import eu.javaexperience.rpc.cli.RpcCliTools;

public class GccBuildCli
{
	@Ignore
	public static void dispatch(Object o, String... args)
	{
		JavaClassRpcUnboundFunctionsInstance rpc = new JavaClassRpcUnboundFunctionsInstance<>(o, o.getClass());
		if(0 == args.length)
		{
			System.out.println(RpcCliTools.generateCliHelp(rpc));
			return;
		}
		
		RpcCliTools.cliExecute(null, rpc, args);
	}
	
	@Ignore
	public static void main(String... args)
	{
		dispatch(new GccBuildCli(), args);
	}
	
/********************************** config ************************************/	
	public void config(String... args)
	{
		dispatch(new GccBuildConfig(), args);
	}
	
	public static class GccBuildConfig
	{
		public void init() throws IOException
		{
			File root = new File("./").getCanonicalFile();
			File cfg = new File(root, "./CModule");
			if(cfg.exists())
			{
				System.out.println("CModule directory already initialized: "+root);
				return;
			}
			GccBuilderContext ctx = GccBuilderContext.acquireDirectory(root);
			ctx.save();
			System.out.println("CModule directory initialized: "+root);
		}
		
		public void variable(String... args)
		{
			dispatch(new ClsVariable(), args);
		}
		
		protected static class ClsVariable
		{
			public void dump() throws IOException
			{
				GccBuilderContext ctx = GccBuilderContext.loadExistingConfig(new File("./").getCanonicalFile());
				JSONObject ret = (JSONObject) ctx.toObject(new DataObjectJsonImpl()).getObject("variables").getImpl();
				System.out.println(ret.toString(4));
			}
			
			public void list() throws IOException
			{
				GccBuilderContext ctx = GccBuilderContext.loadExistingConfig(new File("./").getCanonicalFile());
				ArrayList<String> lst = CollectionTools.inlineAdd(new ArrayList<>(), ctx.getVariables().getVariableFetchers().keySet());
				Collections.sort(lst);
				System.out.println(CollectionTools.toStringMultiline(lst));
			}
			
			public void info() throws IOException
			{
				//list fetch types and content 
			}
			
			//add
			
			///addFetch
			
			//listTypes
			
			public void fetch(String variable) throws IOException
			{
				GccBuilderContext ctx = GccBuilderContext.loadExistingConfig(new File("./").getCanonicalFile());
				System.out.println(ctx.fetchVariable(variable));
			}
		}
		
		public void prop(String... args)
		{
			//setProp
			//getProp
			
		}
		
		public void program(String... args)
		{
			
		}
		
		public void dependency(String... args)
		{
			
		}
		
		//profile add
		public void profile(String... args)
		{
			
		}
		
		//source add
		//header add
		
		
		//clean
		//build --all/profile_name
		
		
	}
	
	public void checkout(String... args)
	{
		//checkout revision if version control system attached
		//vcs: git, zip for first implementaion
	}
	
	
	
}
