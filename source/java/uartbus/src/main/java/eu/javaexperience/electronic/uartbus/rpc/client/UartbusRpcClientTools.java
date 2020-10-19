package eu.javaexperience.electronic.uartbus.rpc.client;

import java.io.IOException;

import eu.javaexperience.electronic.uartbus.rpc.UartbusConnection;
import eu.javaexperience.interfaces.simple.SimpleGet;
import eu.javaexperience.interfaces.simple.publish.SimplePublish1;
import eu.javaexperience.log.JavaExperienceLoggingFacility;
import eu.javaexperience.log.LogLevel;
import eu.javaexperience.log.Loggable;
import eu.javaexperience.log.Logger;
import eu.javaexperience.log.LoggingTools;
import eu.javaexperience.reflect.Mirror;
import eu.javaexperience.retry.RetryTools;
import eu.javaexperience.rpc.bidirectional.BidirectionalRpcDefaultProtocol;
import eu.javaexperience.rpc.javaclient.JavaRpcClientTools;
import eu.javaexperience.rpc.javaclient.JavaRpcParallelClient;

public class UartbusRpcClientTools
{
	private UartbusRpcClientTools(){}
	
	protected static final Logger LOG = JavaExperienceLoggingFacility.getLogger(new Loggable("UartbusRpcClientTools"));
	
	public static JavaRpcParallelClient openIpParallelClient(String ip, int port) throws IOException
	{
		LoggingTools.tryLogFormat(LOG, LogLevel.DEBUG, "openIpParallelClient(%s, %s)", ip,port);
		JavaRpcParallelClient ret = JavaRpcClientTools.createClientWithIpPort(ip, port, BidirectionalRpcDefaultProtocol.DEFAULT_PROTOCOL_HANDLER_WITH_CLASS);
		ret.startPacketRead();
		return ret;
	}
	
	public static UartbusConnection createApi(JavaRpcParallelClient cli)
	{
		return cli.createApiObject(UartbusConnection.class, "uartbus");
	}
	
	public static UartbusStreamerEndpoint openIpEndpoint(String ip, int port, SimplePublish1<UartbusConnection> connectionInitializer, boolean reconnect)
	{
		if(reconnect)
		{
			return openIpEndpoint(ip, port, connectionInitializer, RetryTools.getDefaultReconnectTimeMillisecs());
		}
		else
		{
			return new UartbusStreamerEndpoint(()->
			{
				try
				{
					return createApi(openIpParallelClient(ip, port));
				}
				catch (IOException e)
				{
					Mirror.propagateAnyway(e);
					return null;
				}
			});
		}
	}
	
	public static <T> SimpleGet<T> waitReconnect(SimpleGet<T> get, String entity, int... reconn)
	{
		return RetryTools.waitReconnect(get, entity, LOG, LogLevel.WARNING, reconn);
	}
	
	public static UartbusStreamerEndpoint openIpEndpoint(String ip, int port, SimplePublish1<UartbusConnection> connectionInitializer, int... reconnectRetryDelays)
	{
		SimpleGet<JavaRpcParallelClient> apiReconnect = waitReconnect
		(
			()->
			{
				try
				{
					JavaRpcParallelClient ret = openIpParallelClient(ip, port);
					ret.getServerEventMediator().addEventListener((e)->System.err.println("UNHANDLED: "+e));
					if(null != connectionInitializer)
					{
						connectionInitializer.publish(createApi(ret));
					}
					return ret;
				}
				catch(IOException e)
				{
					Mirror.propagateAnyway(e);
					return null;
				}
			},
			"Uartbus API connection to "+ip+":"+port,
			reconnectRetryDelays
		);
		
		return new UartbusStreamerEndpoint(()->createApi(apiReconnect.get()));
	}
}
