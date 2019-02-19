package eu.javaexperience.electronic.build;

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
}
