package eu.javaexperience.electronic.uartbus.cli.apps;

import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.*;

import java.util.List;
import java.util.Map;

import eu.javaexperience.cli.CliEntry;
import eu.javaexperience.cli.CliTools;
import eu.javaexperience.collection.map.SmallMap;
import eu.javaexperience.electronic.uartbus.rpc.UartbusRpcTools;
import eu.javaexperience.electronic.uartbus.rpc.UbRpcModifiers;
import eu.javaexperience.electronic.uartbus.rpc.client.UartBus;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UartBusDevice;
import eu.javaexperience.electronic.uartbus.rpc.client.device.fns.reflect.UbReflectNs;
import eu.javaexperience.electronic.uartbus.rpc.data_type.uint8_t;
import eu.javaexperience.log.JavaExperienceLoggingFacility;
import eu.javaexperience.reflect.Mirror;
import eu.javaexperience.text.StringTools;

public class UartbusReflect
{
	protected static final CliEntry[] PROG_CLI_ENTRIES =
	{
		RPC_HOST,
		RPC_PORT,
		FROM,
		TO,
		//NS
	};
	
	public static void main(String[] args) throws Exception
	{
		JavaExperienceLoggingFacility.addStdOut();
		Map<String, List<String>> pa = CliTools.parseCliOpts(args);
		String un = CliTools.getFirstUnknownParam(pa, PROG_CLI_ENTRIES);
		if(null != un)
		{
			CliTools.printHelpAndExit("UartbusReflect", 1, PROG_CLI_ENTRIES);
		}
		
		UartBus bus = UartBus.fromTcp
		(
			RPC_HOST.tryParseOrDefault(pa, "127.0.0.1"),
			RPC_PORT.tryParseOrDefault(pa, 2112),
			FROM.tryParseOrDefault(pa, 63)
		);
		
		Integer to = TO.tryParse(pa);
		
		if(null == to)
		{
			throw new RuntimeException("No target address specified.");
		}
		
		UartBusDevice dev = bus.device(to);
		dev.retryCount = 1;
		dev.timeout = 500;
		
		//this call ensures device online. (Throws exception if not.)
		dev.getRpcRoot().getBusFunctions().ping();
		
		UbReflectNs ref = dev.getRpcRoot().getReflectFunctions();
		
		Map<byte[], RpcNode> nss = new SmallMap<>();
		/*collectRpcTree(ref, nss);
		System.out.println(MapTools.toString(nss));*/
		printTree(ref, 0);
	}
	
	public static class RpcNode
	{
		
		
		
		
	}
	
	public static void collectRpcTree(UbReflectNs ref, Map<byte[], RpcNamespace> namespaces)
	{
		collectRpcTree(Mirror.emptyByteArray, ref, namespaces);
	}
	
	protected static void printTree(UbReflectNs ref, int in)
	{
		try
		{
			System.out.print(StringTools.repeatChar('\t', in));
			System.out.print(ref.getNamespaceIndex().value);
			System.out.print(": ");
			System.out.print(UartbusRpcTools.loadString(ref.getName()));
			
			uint8_t mods = ref.getModifiers();
			
			if(0 != (mods.value & UbRpcModifiers.rpc_modifier_function.mask()))
			{
				System.out.print("()");
			}
			System.out.println();
			
			int len = ref.getSubNodesCount().value;
			for(int i=0;i<len;++i)
			{
				uint8_t ns = ref.getNthSubNodeNamespace(new uint8_t(i));
				printTree(ref.getSubNode(ns), in+1);
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void collectRpcTree(byte[] path, UbReflectNs ref, Map<byte[], RpcNamespace> namespaces)
	{
		try
		{
			ref = ref.getSubNode(new uint8_t(32));
			uint8_t ns = ref.getNamespaceIndex();
			
			uint8_t mods = ref.getModifiers();
			
			String name = UartbusRpcTools.loadString(ref.getName());
			
			
			uint8_t subs = ref.getSubNodesCount();
			
			System.out.println(name+" "+ns.value+" "+mods.value+" "+subs.value);
			
			
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
}
