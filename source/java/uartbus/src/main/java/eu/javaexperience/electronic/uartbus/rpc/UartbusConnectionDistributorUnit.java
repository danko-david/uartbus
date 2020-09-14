package eu.javaexperience.electronic.uartbus.rpc;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

import eu.javaexperience.electronic.uartbus.UartbusTools;
import eu.javaexperience.electronic.uartbus.rpc.UartbusConnection;
import eu.javaexperience.electronic.uartbus.rpc.client.UartbusRpcClientTools;
import eu.javaexperience.io.IOTools;
import eu.javaexperience.log.JavaExperienceLoggingFacility;
import eu.javaexperience.log.LogLevel;
import eu.javaexperience.log.Loggable;
import eu.javaexperience.log.Logger;
import eu.javaexperience.log.LoggingTools;
import eu.javaexperience.rpc.javaclient.JavaRpcParallelClient;

public class UartbusConnectionDistributorUnit implements Closeable
{
	protected static final Logger LOG = JavaExperienceLoggingFacility.getLogger(new Loggable("UartbusConnectionDistributorUnit"));
	
	protected final UartbusConnectionDistributor dist;
	protected final Thread receiver;
	protected final UartbusConnection connection;
	
	public UartbusConnectionDistributorUnit(UartbusConnection conn) throws IOException
	{
		conn.setAttribute("loopback_send_packets", "true");
		this.connection = conn;
		this.dist = new UartbusConnectionDistributor((data)->{
			try
			{
				conn.sendPacket(data);
			}
			catch (IOException e)
			{
				LoggingTools.tryLogFormatException(LOG, LogLevel.ERROR, e, "Error while distributing packet");
			}
		});
		
		receiver = new Thread()
		{
			@Override
			public void run()
			{
				try
				{
					while(true)
					{
						dist.feedPacketToDistribute(conn.getNextPacket());
					}
				}
				catch(Exception e)
				{
					LoggingTools.tryLogFormatException(LOG, LogLevel.ERROR, e, "Error while polling packet");
				}
			}
		};
	}
	
	public UartbusConnectionDistributor getDistributor()
	{
		return dist;
	}
	
	public static UartbusConnectionDistributorUnit connectTcp(String ip, int port) throws IOException
	{
		JavaRpcParallelClient cli = UartbusRpcClientTools.openIpParallelClient(ip, port);
		UartbusConnection api = cli.createApiObject(UartbusConnection.class, "uartbus");
		return wrapConnection
		(
			()->
			{
				cli.stopPacketRead();
				cli.shudown();
			},
			api
		);
	}
	
	public static UartbusConnectionDistributorUnit wrapConnection(Closeable onClose, UartbusConnection conn) throws IOException
	{
		return new UartbusConnectionDistributorUnit(new UartbusConnection()
		{
			@Override
			public void close() throws IOException
			{
				IOTools.silentClose(onClose);
			}
			
			@Override
			public void setAttribute(String key, String value) throws IOException
			{
				conn.setAttribute(key, value);
			}
			
			@Override
			public void sendPacket(byte[] data) throws IOException
			{
				conn.sendPacket(data);
			}
			
			@Override
			public Map<String, String> listAttributes()
			{
				return conn.listAttributes();
			}
			
			@Override
			public byte[] getNextPacket() throws IOException
			{
				return conn.getNextPacket();
			}
			
			@Override
			public String getAttribute(String key) throws IOException
			{
				return conn.getAttribute(key);
			}

			@Override
			public void init()
			{
				UartbusTools.initConnection(conn);
			}
		});
	}
	
	public void start()
	{
		receiver.start();
	}
	
	@Override
	public void close() throws IOException
	{
		IOTools.silentClose(connection);
		receiver.interrupt();
	}
}