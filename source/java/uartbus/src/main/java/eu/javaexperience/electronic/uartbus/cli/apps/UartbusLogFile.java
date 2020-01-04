package eu.javaexperience.electronic.uartbus.cli.apps;

import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.*;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import eu.javaexperience.binary.PacketFramingTools;
import eu.javaexperience.cli.CliEntry;
import eu.javaexperience.cli.CliTools;
import eu.javaexperience.collection.map.KeyVal;
import eu.javaexperience.collection.map.SmallMap;
import eu.javaexperience.electronic.uartbus.UartbusTools;
import eu.javaexperience.electronic.uartbus.rpc.UartbusConnection;
import eu.javaexperience.electronic.uartbus.rpc.client.UartbusRpcClientTools;
import eu.javaexperience.electronic.uartbus.rpc.client.UartbusRpcClientTools.PacketStreamThread;
import eu.javaexperience.interfaces.simple.getBy.GetBy1;
import eu.javaexperience.interfaces.simple.publish.SimplePublish1;
import eu.javaexperience.interfaces.simple.publish.SimplePublish2;
import eu.javaexperience.io.file.FileTools;
import eu.javaexperience.log.JavaExperienceLoggingFacility;
import eu.javaexperience.log.LogLevel;
import eu.javaexperience.log.Loggable;
import eu.javaexperience.log.Logger;
import eu.javaexperience.log.LoggingTools;
import eu.javaexperience.parse.ParsePrimitive;
import eu.javaexperience.reflect.Mirror;
import eu.javaexperience.text.Format;
import eu.javaexperience.time.TimeCalc;

public class UartbusLogFile
{
	protected static final Logger LOG = JavaExperienceLoggingFacility.getLogger(new Loggable("UartbusLogFile"));
	
	protected static final CliEntry<String> LOG_PREFIX = CliEntry.createFirstArgParserEntry
	(
		e->e,
		"Log file prefix, it may contain path information",
		"b", "-prefix"
	);
	
	protected static final CliEntry<String> LOG_POSTFIX = CliEntry.createFirstArgParserEntry
	(
		e->e,
		"Log file postfix, it may contain path information. Default: `.log`",
		"a", "-postfix"
	);
	
	protected static final CliEntry<String> LOG_ROTA_UNIT = CliEntry.createFirstArgParserEntry
	(
		e->e,
		"Log rotation interval, valid units are: year, month, day (default), hour, minute, secound or number as secounds.",
		"r", "-rotate-interval"
	);
	
	protected static final CliEntry<String> LOG_FORMAT = CliEntry.createFirstArgParserEntry
	(
		e->e,
		"Logging file format: binary, plain (default)",
		"f", "-log-format"
	);
	
	protected static final CliEntry[] PROG_CLI_ENTRIES =
	{
		RPC_HOST,
		RPC_PORT,
		LOOPBACK,
		LOG_PREFIX,
		LOG_POSTFIX,
		LOG_ROTA_UNIT,
		LOG_FORMAT
	};
	
	//source: DayliLogrotaOutput and RotaLogOutput
	protected static class LogRotaOutput<T, L>
	{
		protected String prefix;
		protected String postfix;
		protected GetBy1<Long, Integer> getCutTimeByOffset;
		protected GetBy1<T, File> openLogFile;
		protected SimplePublish2<T, L> log;
		
		public LogRotaOutput
		(
			String prefix,
			String postfix,
			GetBy1<Long, Integer> getCutTimeByOffset,
			GetBy1<T, File> openLogFile,
			SimplePublish2<T, L> log
		)
		{
			this.prefix = prefix;
			this.postfix = postfix;
			this.getCutTimeByOffset = getCutTimeByOffset;
			this.openLogFile = openLogFile;
			this.log = log;
		}
		
		protected T currentLogOutput;
		protected long nextCutTime;
		
		protected synchronized boolean needCut()
		{
			if(System.currentTimeMillis() >= nextCutTime)
			{
				nextCutTime = getCutTimeByOffset.getBy(1);
				return true;
			}
			
			return false;
		}
		
		public synchronized void log(L entry) throws IOException
		{
			log.publish(getLogWriter(), entry);
		}
		
		public synchronized T getLogWriter() throws IOException
		{
			if(null == currentLogOutput)
			{
				currentLogOutput = openNextLogUnit();
				nextCutTime = getCutTimeByOffset.getBy(1);
			}
			else if(needCut())
			{
				if(currentLogOutput instanceof Flushable)
				{
					((Flushable)currentLogOutput).flush();
				}
				if(currentLogOutput instanceof Closeable)
				{
					((Closeable)currentLogOutput).close();
				}
				currentLogOutput = openNextLogUnit();
			}
			
			return currentLogOutput;
		}

		protected T openNextLogUnit()
		{
			File f = new File(prefix+""+Format.FILE_SQL_TIMESTAMP.format(new Date())+postfix);
			FileTools.createDirectoryForFile(f);
			return openLogFile.getBy(f);
		}
	}
	
	protected static <T,L> Map<String, Entry<GetBy1<T, File>,SimplePublish2<T, L>>> getLogFormats()
	{
		Map<String, Entry<GetBy1<T, File>,SimplePublish2<T, L>>> ret = new SmallMap<>();
		
		((Map<String, Entry<GetBy1<PrintWriter, File>,SimplePublish2<PrintWriter, byte[]>>>) (Map) ret).put
		(
			"plain",
			new KeyVal<>
			(
				f->
				{
					try
					{
						if(f.exists())
						{
							return new PrintWriter(new FileOutputStream(f, true), true);
						}
						else
						{
							return new PrintWriter(f);
						}
						
					}
					catch(Exception e)
					{
						Mirror.propagateAnyway(e);
						return null;
					}
				},
				(f, d)->
				{
					f.println(Format.UTC_SQL_TIMESTAMP_MS.format(new Date())+"|"+UartbusTools.formatColonData(d));
					f.flush();
				}
			)
		);
		
		((Map<String, Entry<GetBy1<FileOutputStream, File>,SimplePublish2<FileOutputStream, byte[]>>>) (Map) ret).put
		(
			"binary",
			new KeyVal<>
			(
				f->
				{
					try
					{
						return new FileOutputStream(f, f.exists());
					}
					catch(Exception e)
					{
						Mirror.propagateAnyway(e);
						return null;
					}
				},
				(f, d)->
				{
					byte[] data = new byte[8+d.length];
					long time = System.currentTimeMillis();
					
					data[0] = (byte) ((time >> 56) & 0xff);
					data[1] = (byte) ((time >> 48) & 0xff);
					data[2] = (byte) ((time >> 40) & 0xff);
					data[3] = (byte) ((time >> 32) & 0xff);
					data[4] = (byte) ((time >> 24) & 0xff);
					data[5] = (byte) ((time >> 16) & 0xff);
					data[6] = (byte) ((time >> 8) & 0xff);
					data[7] = (byte) (time & 0xff);
					
					for(int i=0;i<d.length;++i)
					{
						data[8+i] = d[i];
					}
					data = PacketFramingTools.frameBytes(data, (byte) 0);
					try
					{
						f.flush();
						f.write(data);
					}
					catch (IOException e)
					{
						Mirror.propagateAnyway(e);
					}
				}
			)
		);
		
		return ret;
	}
	
	protected static <F> GetBy1<Long, Integer> parseCutter(String format)
	{
		switch(format)
		{
		case "year":
			return i->
			{
				Date base = TimeCalc.setToDate(new Date(), -1, 1, 1, 0, 0, 0, 0);
				return TimeCalc.addToDate(base, i, 0, 0, 0, 0, 0, 0).getTime();
			};
		
		case "month":
			return i->
			{
				Date base = TimeCalc.setToDate(new Date(), -1, -1, 1, 0, 0, 0, 0);
				return TimeCalc.addToDate(base, 0, i, 0, 0, 0, 0, 0).getTime();
			};
		
		case "day":
			return i->
			{
				Date base = TimeCalc.setToDate(new Date(), -1, -1, -1, 0, 0, 0, 0);
				return TimeCalc.addToDate(base, 0, 0, i, 0, 0, 0, 0).getTime();
			};
		
		case "hour":
			return i->
			{
				Date base = TimeCalc.setToDate(new Date(), -1, -1, -1, -1, 0, 0, 0);
				return TimeCalc.addToDate(base, 0, 0, 0, i, 0, 0, 0).getTime();
			};
		
		case "minute":
			return i->
			{
				Date base = TimeCalc.setToDate(new Date(), -1, -1, -1, -1, -1, 0, 0);
				return TimeCalc.addToDate(base, 0, 0, 0, 0, i, 0, 0).getTime();
			};
			
		case "secound":
			return i->
			{
				Date base = TimeCalc.setToDate(new Date(), -1, -1, -1, -1, -1, -1, 0);
				return TimeCalc.addToDate(base, 0, 0, 0, 0, 0, i, 0).getTime();
			};
			
		default:
			
			Integer val = ParsePrimitive.tryParseInt(format);
			if(null == val)
			{
				throw new RuntimeException("Can't parse rotation interval: "+format);
			}
			
			if(val < 1)
			{
				throw new RuntimeException("Invalid rotation interval: "+format);
			}
			
			int v = val*1000;
			
			return i->
			{
				long t = System.currentTimeMillis();
				t /= v;
				return t*v+ i*v;
			};
		}
	}
	
	public static <F> void main(String[] args) throws Throwable
	{
		JavaExperienceLoggingFacility.addStdOut();
		Map<String, List<String>> pa = CliTools.parseCliOpts(args);
		
		String reqFormat = LOG_FORMAT.tryParseOrDefault(pa, "plain");
		
		Entry<GetBy1<Object, File>, SimplePublish2<Object, Object>> format = getLogFormats().get(reqFormat);
		
		String un = CliTools.getFirstUnknownParam(pa, PROG_CLI_ENTRIES);
		if(null != un || null == format)
		{
			if(null == format)
			{
				System.err.println("Unknown log format: "+reqFormat);
			}
			CliTools.printHelpAndExit("UartbusLogFile", 1, PROG_CLI_ENTRIES);
		}
		
		LogRotaOutput<F, byte[]> logger = new LogRotaOutput
		(
			LOG_PREFIX.tryParseOrDefault(pa, ""),
			LOG_POSTFIX.tryParseOrDefault(pa, ".log"),
			parseCutter(LOG_ROTA_UNIT.tryParseOrDefault(pa, "day")),
			format.getKey(),
			format.getValue()
		);
		
		PacketStreamThread stream = UartbusRpcClientTools.streamPackets
		(
			RPC_HOST.tryParseOrDefault(pa, "127.0.0.1"),
			RPC_PORT.tryParseOrDefault(pa, 2112),
			(e) ->
			{
				try
				{
					logger.log(e);
				}
				catch(Exception e1)
				{
					LoggingTools.tryLogFormatException(LOG, LogLevel.ERROR, e1, "Exception occurred while saving packet");
				}
			},
			(SimplePublish1<UartbusConnection>)(connection)->
			{
				if(LOOPBACK.hasOption(pa))
				{
					try
					{
						connection.setAttribute("loopback_send_packets", "true");
					}
					catch (IOException e1)
					{
						e1.printStackTrace();
					}
				}
			}
		);
		
		while(true)
		{
			Thread.sleep(Long.MAX_VALUE);
		}
	}
}
