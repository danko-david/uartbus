package eu.javaexperience.electronic.build;

import java.io.File;

import eu.javaexperience.collection.CollectionTools;
import eu.javaexperience.text.StringTools;

public class GccBuildBench
{

	/**
	 * TODO
	 * - Compile uartbus_connector.ino
	 * 
	 * 
	 * */
	public static void main(String[] args) throws Throwable
	{
		//GccBuilderContext ctx = ArduinoGccTools.createArduinoBuildContext("/usr/share/arduino/", "atmega328p", "16000000");
		GccPrograms progs = ArduinoGccTools.pickupArduinoPrograms("/usr/share/arduino/");
		String mcu = "atmega328p";
		String freq = "16000000";
		
		
		GccBuilderContext ctx = GccBuilderContext.acquireDirectory(new File("/tmp/compile/arduino/"));
		ctx.fetchedCtx.progs = progs;
		File root = new File("/usr/share/arduino/hardware/arduino/cores/arduino/");
		for(File f:root.listFiles())
		{
			String sf = f.toString();
			String fe = StringTools.getSubstringAfterFirstString(sf, "/usr/share/arduino/hardware/arduino/cores/arduino/");
			if(fe.endsWith(".cpp") || fe.endsWith(".c"))
			{
				ctx.copySource(false, f, fe);
			}
			else if(sf.endsWith(".h"))
			{
				ctx.copyHeader(false, f, fe);
			}
		}
		
		ctx.fetchedCtx.includeDirectories.add("/usr/share/arduino/hardware/arduino/variants/standard");
		
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
		
		ctx.save();
		
		/*"-ffunction-sections",
		"-fdata-sections",*/
	
		
	}
}
