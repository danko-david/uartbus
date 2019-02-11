package eu.javaexperience.electronic.build;

import eu.javaexperience.collection.CollectionTools;
import eu.javaexperience.text.StringTools;

public class ArduinoGccTools
{
	private ArduinoGccTools() {}
	
	public static GccPrograms pickupArduinoPrograms(String arduinoDir)
	{
		String root = StringTools.ensureEndsWith(arduinoDir, "/");
		GccPrograms ret = new GccPrograms
		(
			root+"hardware/tools/avr/bin/avr-gcc",
			root+"hardware/tools/avr/bin/avr-g++",
			root+"hardware/tools/avr/bin/avr-ar",
			root+"hardware/tools/avr/bin/avr-objcopy"
		);
		
		ret.ensureProgramsExists();
		return ret;
	}
	
	public static GccBuilderContext createArduinoBuildContext
	(
		String arduinoDir,
		String mcu,
		String frequency
	)
	{
		GccBuilderContext ctx = new GccBuilderContext();
		arduinoDir = StringTools.ensureEndsWith(arduinoDir, "/");
		ctx.progs = ArduinoGccTools.pickupArduinoPrograms(arduinoDir);
		
		CollectionTools.inlineAdd
		(
			ctx.flags,
			
			"-c",
			"-g",
			"-Os",
			"-Wall",
			"-ffunction-sections",
			"-fdata-sections",
			"-mmcu="+mcu,
			"-DF_CPU="+frequency,
			"-MMD",
			"-DUSB_VID=null",
			"-DUSB_PID=null",
			"-DARDUINO=106"
		);
		
		ctx.includeDirectories.add(arduinoDir+"hardware/arduino/cores/arduino");
		ctx.includeDirectories.add(arduinoDir+"hardware/arduino/variants/standard");
		
		//TODO add library C files
		//TODO place a well known destination place
		//TODO need to translate paths between source and compiled files
		
		return ctx;
	}
	
	
}
