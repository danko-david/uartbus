package eu.javaexperience.electronic.build;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import eu.javaexperience.collection.enumerations.EnumTools;
import eu.javaexperience.collection.map.MultiMap;
import eu.javaexperience.collection.map.SmallMap;
import eu.javaexperience.datareprez.DataCommon;
import eu.javaexperience.datareprez.DataObject;
import eu.javaexperience.interfaces.simple.getBy.GetBy2;
import eu.javaexperience.reflect.Mirror;
import eu.javaexperience.text.StringTools;

public class VariableFetch
{
	public enum VariableSourceType
	{
		ENV((var, ctx)->System.getenv(var)),
 		PROP((var, ctx)->ctx.getProperty(var)),
 		PARENT
 		(
 			(var, ctx)->
			{
				for(GccBuilderContext c:ctx.getParentContextes())
				{
					String ret = c.fetchVariable(var);
					if(null != ret)
					{
						return ret;
					}
				}
				return null;
			}
		),
 		PROP_FILE
 		(
 			(var, ctx)->
 			{
 				String[] vs = var.split(":");
 				if(vs.length < 2)
 				{
 					throw new RuntimeException("Illegal PROP_FILE varaible declaration: `"+var+"` proper format: ./file1.prop:./file2.prop:VARIABLE");
 				}
 				
 				var = vs[vs.length-1];
 				for(int i=0;i<vs.length-1;++i)
 				{
 					Properties p = new Properties();
 					try(FileInputStream fis = new FileInputStream(ctx.relFile(vs[i])))
 					{
 						p.load(fis);
 						String ret = p.getProperty(var);
 						if(null != ret)
 						{
 							return ret;
 						}
 					}
 					catch (IOException e)
 					{
						Mirror.propagateAnyway(e);
					}
 				}
 				
 				return null;
 			}
 		),
 		EXIST_FILE
 		(
 			(var, ctx)->
 			{
 				File f = ctx.relFile(var);
 				if(f.exists())
 				{
 					return f.toString();
 				}
 				return null;
 			}
 		),
 		VALUE((var, ctx)->var)
 		;
		
		protected GetBy2<String, String, GccBuilderContext> get;
		
		private VariableSourceType(GetBy2<String, String, GccBuilderContext> get)
		{
			this.get = get;
		}
	}
	
	protected MultiMap<String, VariableEntry> variableFetch = new MultiMap<>();

	public static class VariableEntry
	{
		protected VariableSourceType type;
		protected String content;
		
		public VariableSourceType getType()
		{
			return type;
		}
		
		public String getContent()
		{
			return content;
		}
		
		public static VariableEntry parse(String key, DataObject src)
		{
			VariableEntry ret = new VariableEntry();
			
			ret.type = EnumTools.recogniseSymbol(VariableSourceType.class, src.get("type"));
			if(null != ret.type)
			{
				throw new RuntimeException("Unknown variable type: "+src.get("type")+" at variable: "+key);
			}
			ret.content = src.getString("content");
			
			return ret;
		}

		public DataObject toObject(DataCommon proto)
		{
			DataObject ret = proto.newObjectInstance();
			ret.putString("content", content);
			ret.putString("type", type.name());
			return ret;
		}
	}
	
	public String passTroughtValue(String variable, GccBuilderContext ctx)
	{
		if(variable.startsWith("$"))
		{
			return fetch(StringTools.getSubstringAfterFirstString(variable, "$"), ctx);
		}
		return variable;
	}
	
	public String fetch(String variable, GccBuilderContext ctx)
	{
		List<VariableEntry> fs = variableFetch.getList(variable);
		if(null == fs)
		{
			throw new RuntimeException("Varaible not found: $"+variable);
		}
		
		for(VariableEntry f:fs)
		{
			String ret = f.type.get.getBy(f.content, ctx);
			if(null != ret)
			{
				return ret;
			}
		}
		
		throw new RuntimeException("Can't fetch variable: $"+variable);
	}
	
	public Map<String, VariableEntry> getVariableFetchers()
	{
		return new SmallMap<>(variableFetch);
	}
	
	public static VariableFetch parse(DataObject src)
	{
		VariableFetch ret = new VariableFetch();
		
		for(String k:src.keys())
		{
			ret.variableFetch.put(k, VariableEntry.parse(k, src.getObject(k)));
		}
		
		return ret;
	}
	
	public DataObject toObject(DataCommon proto)
	{
		DataObject ret = proto.newObjectInstance();
		
		for(Entry<String, VariableEntry> kv:variableFetch.entrySet())
		{
			ret.putObject(kv.getKey(), kv.getValue().toObject(proto));
		}
		
		return ret;
	}
}
