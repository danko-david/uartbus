package eu.javaexperience.electronic.uartbus;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Arrays;

import eu.javaexperience.electronic.SerialTools;
import eu.javaexperience.interfaces.simple.publish.SimplePublish1;
import eu.javaexperience.io.IOStream;
import eu.javaexperience.parse.ParsePrimitive;
import eu.javaexperience.text.StringTools;

public class UartbusPacketConnector implements Closeable
{
	protected IOStream io;
	protected byte packetEscape;
	protected SimplePublish1<byte[]> onPacketReceived;
	
	public UartbusPacketConnector(IOStream io, byte terminator)
	{
		this.io = io;
		this.packetEscape = terminator;
	}
	
	protected Thread receiver;
	
	public void setPacketHook(SimplePublish1<byte[]> onPacketReceived)
	{
		synchronized (this)
		{
			this.onPacketReceived = onPacketReceived;
		}
	}
	
	protected void dispatchPacket(byte[] data)
	{
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
		
		final InputStream in = io.getInputStream();
		
		receiver = new Thread()
		{
			@Override
			public void run()
			{
				int read = 0;
				try
				{
					while(0 < (read = in.read(buffer)))
					{
						feedBytes(buffer, read);
					}
				}
				catch(IOException e)
				{
					e.printStackTrace();
				}
			}
		};
		receiver.start();
	}
	
	int ep = 0;
	byte[] buffer = new byte[10240];
	boolean mayCut = false;
	
	protected void feedBytes(byte[] data, int length)
	{
		try
		{
			for(int i=0;i<length;++i)
			{
				byte b = data[i];
				System.out.print(b+":");
				if(mayCut)
				{
					if(b == packetEscape)
					{
						if(b != packetEscape)
						{
							System.out.println("BAD PACKET ESCAPE: "+b);
						}
						buffer[ep++] = packetEscape;
					}
					else
					{
						System.out.println();
						dispatchPacket(Arrays.copyOf(buffer, ep));
						ep = 0;
					}
					mayCut = false;
				}
				else
				{
					if(b == packetEscape)
					{
						mayCut = true;
					}
					else
					{
						buffer[ep++] = b;
					}
				}
			}
		}
		catch(IndexOutOfBoundsException ex)
		{
			//just break the packet, it was way toooo long.
			ep = 0;
			ex.printStackTrace();
		}
	}
	
	public void sendPacket(byte[] data) throws IOException
	{
		synchronized(this)
		{
			byte[] send = frameBytes(data, packetEscape);
			OutputStream os = io.getOutputStream();
			os.write(send);
			os.flush();
		}
	}
	
	public static byte[] frameBytes(byte[] data, byte terminator)
	{
		int w = 0;
		for(int i=0;i<data.length;++i)
		{
			if(data[i] == terminator)
			{
				++w;
			}
		}
		
		byte[] ret = new byte[data.length+w+2];
		
		w = 0;
		
		for(int i=0;i<data.length;++i)
		{
			ret[w++] = data[i];
			if(terminator == data[i])
			{
				ret[w++] = terminator;
			}			
		}
		
		ret[w++] = terminator;
		ret[w] = (byte) ~terminator;
		
		return ret;
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
	
	public static void main(String[] args) throws Exception
	{
		IOStream ser = SerialTools.openSerial(args[0], Integer.parseInt(args[1]));
		Runtime.getRuntime().addShutdownHook(new Thread(ser::close));
		UartbusPacketConnector conn = new UartbusPacketConnector(ser, (byte)0xff);
		
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
}
