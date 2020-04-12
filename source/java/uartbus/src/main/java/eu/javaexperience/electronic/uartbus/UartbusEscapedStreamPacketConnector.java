package eu.javaexperience.electronic.uartbus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Arrays;

import eu.javaexperience.binary.FramedPacketCutter;
import eu.javaexperience.binary.PacketFramingTools;
import eu.javaexperience.electronic.SerialTools;
import eu.javaexperience.interfaces.simple.SimpleCall;
import eu.javaexperience.interfaces.simple.publish.SimplePublish1;
import eu.javaexperience.io.IOStream;
import eu.javaexperience.io.IOTools;
import eu.javaexperience.log.JavaExperienceLoggingFacility;
import eu.javaexperience.log.LogLevel;
import eu.javaexperience.log.Loggable;
import eu.javaexperience.log.Logger;
import eu.javaexperience.log.LoggingTools;
import eu.javaexperience.parse.ParsePrimitive;
import eu.javaexperience.text.StringTools;

public class UartbusEscapedStreamPacketConnector implements UartbusPacketConnector
{
	protected static final Logger LOG = JavaExperienceLoggingFacility.getLogger(new Loggable("UartbusPacketConnector"));
	protected IOStream io;
	protected byte packetEscape;
	protected SimplePublish1<byte[]> onPacketReceived;
	protected FramedPacketCutter cutter;
	
	protected volatile boolean run = false;
	
	public UartbusEscapedStreamPacketConnector(IOStream io, byte terminator)
	{
		this.io = io;
		cutter = new FramedPacketCutter
		(
			terminator,
			b->
			{
				if(null != onPacketReceived)
				{
					onPacketReceived.publish(b);
				}
			});
		this.packetEscape = terminator;
	}
	
	protected Thread receiver;
	protected SimpleCall onClosed;
	
	public synchronized void setPacketHook(SimplePublish1<byte[]> onPacketReceived)
	{
		this.onPacketReceived = onPacketReceived;
	}
	
	public synchronized void setSocketCloseListener(SimpleCall onClosed)
	{
		this.onClosed = onClosed;
	}
	
	protected static void commProcessClosed()
	{
		LoggingTools.tryLogFormat(LOG, LogLevel.WARNING, "Communication process closed and no reconnection was set.");
	}
	
	protected synchronized InputStream getInputStream()
	{
		while(true)
		{
			try
			{
				if(null == io)
				{
					break;
				}
				
				InputStream ret = io.getInputStream();
				if(LOG.mayLog(LogLevel.TRACE))
				{
					LoggingTools.tryLogFormat(LOG, LogLevel.TRACE, "`%s`.getInputStream() [io:`%s`] => `%s`", this, io, ret);
				}
				
				if(null == ret)
				{
					break;
				}
				
				return ret;
			}
			catch(Exception e)
			{
				manageClosedConnection(e, "Trying to read from the communication process");
			}
		}
		
		commProcessClosed();
		return null;
	}
	
	protected synchronized OutputStream getOutputStream()
	{
		while(true)
		{
			try
			{
				if(null == io)
				{
					break;
				}
				
				OutputStream ret = io.getOutputStream();
				if(LOG.mayLog(LogLevel.TRACE))
				{
					LoggingTools.tryLogFormat(LOG, LogLevel.TRACE, "`%s`.getOutputStream() [io:`%s`] => `%s`", this, io, ret);
				}
				
				if(null == ret)
				{
					break;
				}
				
				return ret;
			}
			catch(Exception e)
			{
				manageClosedConnection(e, "Trying to write to the communication process");
			}
		}
		
		commProcessClosed();
		return null;
	}
	
	public synchronized void setIoStream(IOStream io)
	{
		if(null != this.io)
		{
			IOTools.silentClose(this.io);
		}
		this.io = io;
	}
	
	protected void dispatchPacket(byte[] data)
	{
		if(LOG.mayLog(LogLevel.DEBUG))
		{
			LoggingTools.tryLogFormat(LOG, LogLevel.DEBUG, "dispaching packet: %s", UartbusTools.formatColonData(data));
		}
		
		if(null != onPacketReceived)
		{
			try
			{
				onPacketReceived.publish(data);
			}
			catch(Throwable t)
			{
				t.printStackTrace();
			}
		}
	}
	
	public void startListen()
	{
		if(null != receiver)
		{
			throw new IllegalStateException("Listening already started");
		}
		
		run = true;
		
		receiver = new Thread()
		{
			@Override
			public void run()
			{
				int read = 0;
				byte[] read_buffer = new byte[10240];
				while(run /*&& null != io*/)
				{
					try
					{
						cutter.clear();
						InputStream is = getInputStream();
						if(null == is)
						{
							break;
						}
						
						while(0 < (read = is.read(read_buffer)))
						{
							cutter.feedBytes(read_buffer, read);
						}
					}
					catch(Exception e)
					{
						manageClosedConnection(e, "reading packet");
					}
					
					try
					{
						Thread.sleep(1000);
					}
					catch (InterruptedException e){}
				}
			}
		};
		receiver.setDaemon(true);
		receiver.start();
	}
	
	protected void manageClosedConnection(Exception caused, String whileOp)
	{
		LoggingTools.tryLogFormatException(LOG, LogLevel.WARNING, caused, "Communication process broken while: `%s` ", whileOp);
		if(null != onClosed)
		{
			synchronized(this)
			{
				for(int i=0;i<100;++i)
				{
					try
					{
						onClosed.call();
						
						if(null != io)
						{
							io.getInputStream();
							return;
						}
					}
					catch(Exception e) {}
					try
					{
						Thread.sleep(100);
					}
					catch (InterruptedException e)
					{
						e.printStackTrace();
					}
				}
				throw new RuntimeException("Stream still closed after 100 retry");
			}
		}
		else
		{
			io = null;
		}
	}
	
	public void sendPacket(byte[] data)
	{
		byte[] send = PacketFramingTools.frameBytes(data, packetEscape);
		while(true)
		{
			if(!run)
			{
				throw new RuntimeException("This UartbusPacketConnector is not running, can't send packet to the network. Call startListen() to run this instance.");
			}
			
			try
			{
				synchronized(this)
				{
					OutputStream os = getOutputStream();
					os.write(send);
					os.flush();
				}
				return;
			}
			catch(Exception e)
			{
				manageClosedConnection(e, "writing framed packet");
				try
				{
					Thread.sleep(100);
				}
				catch (InterruptedException asd){}
			}
		}
	}
	
	@Override
	public void close() throws IOException
	{
		io.close();
	}
	
	public void sendWithCrc8(byte[] data) throws IOException
	{
		byte[] send = Arrays.copyOf(data, data.length+1);
		send[data.length] = UartbusTools.crc8(data);
		sendPacket(send);
	}
	
	public void stop()
	{
		System.out.println("STOP");
		run = false;
	}
	
	public static void main(String[] args) throws Exception
	{
		IOStream ser = SerialTools.openSerial(args[0], Integer.parseInt(args[1]));
		Runtime.getRuntime().addShutdownHook(new Thread(ser::close));
		UartbusEscapedStreamPacketConnector conn = new UartbusEscapedStreamPacketConnector(ser, (byte)0xff);
		
		conn.setPacketHook((p)->System.out.println("Packet: "+Arrays.toString(p)));
		conn.startListen();
		
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String line = null;
		
		PacketAssembler pa = new PacketAssembler();
		
		out:while
			//(true)
			(null != (line = br.readLine()))
		{
			//line = "1:0";
			//Thread.sleep(100);
			
			pa.writeShort(1);
			pa.writeShort(12);

			String[] path = StringTools.plainSplit(line, ":");
			for(String s:path)
			{
				try
				{
					pa.writeByte(ParsePrimitive.tryParseInt(s));
				}
				catch(Exception e)
				{
					e.printStackTrace();
					pa.done();
					continue out;
				}
			}
			
			pa.appendCrc8();
			conn.sendPacket(pa.done());
		}
	}

	public void feedBytes(byte[] bs)
	{
		cutter.feedBytes(bs, bs.length);
	}
}
