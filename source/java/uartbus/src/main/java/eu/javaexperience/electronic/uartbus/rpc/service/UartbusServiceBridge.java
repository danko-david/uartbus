package eu.javaexperience.electronic.uartbus.rpc.service;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

import eu.javaexperience.electronic.uartbus.PacketAssembler;
import eu.javaexperience.electronic.uartbus.rpc.client.ParsedUartBusPacket;
import eu.javaexperience.electronic.uartbus.rpc.client.UartBus;
import eu.javaexperience.electronic.uartbus.rpc.client.device.NoReturn;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbIndex;
import eu.javaexperience.interfaces.simple.getBy.GetBy1;
import eu.javaexperience.interfaces.simple.publish.SimplePublish1;
import eu.javaexperience.nativ.posix.PosixErrnoException;
import eu.javaexperience.reflect.Mirror;
import eu.javaexperience.reflect.Mirror.ClassData;
import eu.javaexperience.semantic.references.MayNull;

public class UartbusServiceBridge
{
	protected UartBus bus;
	protected Object serviceObject;
	
	protected SimplePublish1<byte[]> responseHandler;
	protected GetBy1<Boolean, UartbusPacketDispatch> packetAcceptor;
	
	public UartbusServiceBridge
	(
		SimplePublish1<byte[]> responseHandler,
		GetBy1<Boolean, UartbusPacketDispatch> packetAcceptor
	)
	{
		this.responseHandler = responseHandler;
		this.packetAcceptor = packetAcceptor;
	}
	
	public void setAssociatedBus(UartBus bus)
	{
		this.bus = bus;
	}
	
	public @MayNull UartBus getAssociatedBus()
	{
		return bus;
	}
	
	public void feedPacket(byte[] rawData)
	{
		feedPacket(new ParsedUartBusPacket(rawData));
	}
	
	public void feedPacket(ParsedUartBusPacket packet)
	{
		UartbusPacketDispatch disp = new UartbusPacketDispatch(packet);
		if(Boolean.TRUE == packetAcceptor.getBy(disp))
		{
			service(disp);
		}
	}
	
	protected void service(UartbusPacketDispatch disp)
	{
		UartbusServiceRequest req = new UartbusServiceRequest();
		req.bridge = this;
		req.request = disp;
		
		//TODO thread local: set request object and remove after handled
		
		serviceRequest(req, serviceObject);
	}
	
	protected static void serviceRequest(UartbusServiceRequest req, Object servObj)
	{
		ClassData cd = Mirror.getClassData(servObj.getClass());
		int index = 0xff & req.request.popNextByte();
		for(Method m:cd.getAllMethods())
		{
			//accept only public non-static methods
			int mod = m.getModifiers();
			if
			(
				!Modifier.isPublic(mod)
			|| 
				Modifier.isStatic(mod)
			)
			{
				continue;
			}
			
			UbIndex ubi = m.getAnnotation(UbIndex.class);
			if(null == ubi)
			{
				continue;
			}
			
			if(ubi.ns() == index)
			{
				boolean canThrowPosix = canThrowPosixErrno(m);
				boolean noRet = NoReturn.class == m.getReturnType();
				
				try
				{
					Object ret = applyCall(req, servObj, m);
				}
				catch(PosixErrnoException ex)
				{
					if(canThrowPosix)
					{
						
					}
					else
					{
						Mirror.propagateAnyway(ex);
					}
				}
				catch(Throwable t)
				{
					
				}
				/* if it can throw posix exception add a zero to response, or
				 * send the actual value
				 */
				
				//if ret type is primitive
				
				
				
			}
		}
	}
	
	protected static Object applyCall
	(
		UartbusServiceRequest req,
		Object subject,
		Method m
	)
	throws Throwable
	{
		//TODO
		return null;
	}
	
	protected static void responsePacket(UartbusServiceRequest req, Object... response)
	{
		PacketAssembler pa = req.createResponseBuilder();
		pa.writeObjects(response);
		pa.appendCrc8();
		
	}
	
	protected static boolean canThrowPosixErrno(Method m)
	{
		for(Class<?> ex:m.getExceptionTypes())
		{
			if(PosixErrnoException.class.isAssignableFrom(ex))
			{
				return true;
			}
		}
		return false;
	}

	public int getFromAddress(int to)
	{
		return to;
	}
}
