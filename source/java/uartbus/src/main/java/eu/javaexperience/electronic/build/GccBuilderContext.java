package eu.javaexperience.electronic.build;

import java.util.HashSet;
import java.util.Set;

import eu.javaexperience.collection.CollectionTools;

public class GccBuilderContext implements Cloneable 
{
	protected GccPrograms progs;
	
	protected Set<String> flags = new HashSet<>();
	
	protected Set<String> includeDirectories = new HashSet<>();
	
	
	
	public class CompiledObjectFile
	{
		protected GccBuilderContext ctx;
		protected String file;
	}
	
	/*public CompiledObjectFile getCompiledObjectFile(String file)
	{
		
	}*/

/*
	make able to build:
		- C file set with environment and result an archive file  
		
		- compiler use 
	
*/
	
/*
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
*/
	
	public static void main(String[] args)
	{
		GccBuilderContext ctx = ArduinoGccTools.createArduinoBuildContext("/usr/share/arduino/", "atmega328p", "16000000");
		
		
	}
}
