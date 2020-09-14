package eu.javaexperience.electronic.uartbus.rpc.service;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

import eu.javaexperience.electronic.uartbus.PacketAssembler;
import eu.javaexperience.electronic.uartbus.PacketReader;
import eu.javaexperience.electronic.uartbus.rpc.client.ParsedUartBusPacket;
import eu.javaexperience.electronic.uartbus.rpc.client.UartBus;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbIndex;
import eu.javaexperience.electronic.uartbus.rpc.client.device.UbRpcTools;
import eu.javaexperience.electronic.uartbus.rpc.datatype.NoReturn;
import eu.javaexperience.electronic.uartbus.rpc.datatype.uint8_t;
import eu.javaexperience.interfaces.simple.getBy.GetBy1;
import eu.javaexperience.interfaces.simple.publish.SimplePublish1;
import eu.javaexperience.io.IOTools;
import eu.javaexperience.log.JavaExperienceLoggingFacility;
import eu.javaexperience.log.LogLevel;
import eu.javaexperience.log.Loggable;
import eu.javaexperience.log.Logger;
import eu.javaexperience.log.LoggingTools;
import eu.javaexperience.nativ.posix.PosixErrnoException;
import eu.javaexperience.reflect.Mirror;
import eu.javaexperience.reflect.Mirror.ClassData;
import eu.javaexperience.semantic.references.MayNull;

public class UartbusServiceBridge implements Closeable
{
	protected static Logger LOG = JavaExperienceLoggingFacility.getLogger(new Loggable("UartbusServiceBridge"));
	
	protected UartBus bus;
	protected Object serviceObject;
	
	protected SimplePublish1<byte[]> responseHandler;
	protected GetBy1<Boolean, UartbusPacketDispatch> packetAcceptor;
	
	protected List<AutoCloseable> onClose = new ArrayList<>();
	
	public UartbusServiceBridge
	(
		Object serviceObject,
		SimplePublish1<byte[]> responseHandler,
		GetBy1<Boolean, UartbusPacketDispatch> packetAcceptor
	)
	{
		this.serviceObject = serviceObject;
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
	
	public void addReleasableResource(AutoCloseable cl)
	{
		onClose.add(cl);
	}
	
	public void feedPacket(ParsedUartBusPacket packet)
	{
		UartbusPacketDispatch disp = new UartbusPacketDispatch(packet);
		if(Boolean.TRUE == packetAcceptor.getBy(disp))
		{
			service(disp);
		}
	}
	
	protected static ThreadLocal<UartbusServiceRequest> REQUESTS = new ThreadLocal<>();
	
	public static UartbusServiceRequest getCurrentRequest()
	{
		return REQUESTS.get();
	}
	
	protected void service(UartbusPacketDispatch disp)
	{
		UartbusServiceRequest req = new UartbusServiceRequest();
		req.bridge = this;
		req.request = disp;
		
		//thread local: set request object and remove after handled, like in RpcSessionTools
		
		REQUESTS.set(req);
		try
		{
			serviceRequest(req, serviceObject);
		}
		catch(Throwable t)
		{
			LoggingTools.tryLogFormatException
			(
				LOG,
				LogLevel.WARNING,
				t,
				"Exception occured while servicing uartbus request :  `%s`",
				req
			);
		}
		finally
		{
			REQUESTS.set(null);
		}
	}
	
	protected static void serviceRequest(UartbusServiceRequest req, Object servObj) throws Throwable
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
				handleCall(req, servObj, m);
			}
		}
	}
	
	protected static void handleCall
	(
		UartbusServiceRequest req,
		Object subject,
		Method m
	)
	throws Throwable
	{
		int dix = req.request.getDispatchByteIndex()+1;
		boolean canThrowPosix = canThrowPosixErrno(m);
		boolean noRet = NoReturn.class == m.getReturnType();
		
		Throwable invokeEx = null;
		try
		{
			Object ret = applyCall(req, subject, m);
			if(noRet)
			{
				return;
			}
			
			if(null == ret)
			{
				if(canThrowPosix)
				{
					responsePacket(req, dix, 0);
					return;
				}
				else
				{
					responsePacket(req, dix);
					return;
				}
			}
			
			//if ret type is primitive send answer, if object returned, continue the dispatch
			if(UbRpcTools.isUartbusDataType(ret))
			{
				if(canThrowPosix)
				{
					responsePacket(req, dix,  new uint8_t(0), ret);
					return;
				}
				else
				{
					responsePacket(req, dix, ret);
					return;
				}
			}
			
			serviceRequest(req, ret);
			//handleCall(req, ret, m);
		}
		catch(Throwable t)
		{
			invokeEx = t;
			if(t instanceof InvocationTargetException)
			{
				Throwable tmp = ((InvocationTargetException)t).getCause();
				if(null != tmp)
				{
					invokeEx = tmp;
				}
			}
			
			if(invokeEx instanceof PosixErrnoException)
			{
				if(canThrowPosix)
				{
					responsePacket(req, dix, new uint8_t(((PosixErrnoException)invokeEx).getErrno().value));
				}
				else
				{
					throw invokeEx;
				}
			}
			else
			{
				Mirror.propagateAnyway(invokeEx);
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
		Parameter[] params = m.getParameters();
		//extract parameters
		Object[] args = new Object[params.length];
		PacketReader pr = req.request.createReaderCurrentPoz();
		for(int i=0;i<args.length;++i)
		{
			args[i] = UbRpcTools.readType(pr, params[i].getType(), params[i].getParameterizedType());
		}
		req.request.moveDispatch(pr.getParseStateIndex());
		return m.invoke(subject, args);
	}
	
	protected static void responsePacket(UartbusServiceRequest req, int dix, Object... response)
	{
		try
		{
			PacketAssembler pa = req.createResponseBuilder(dix);
			pa.writeObjects(response);
			pa.appendCrc8();
			req.bridge.responseHandler.publish(pa.done());
		}
		catch(Exception e)
		{
			Mirror.propagateAnyway(e);
		}
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
	
	protected UartBus getBus()
	{
		return bus;
	}
	protected Object getServiceObject()
	{
		return serviceObject;
	}
	
	public static UartbusServiceBridge attachServiceTo
	(
		Object service,
		UartBus bus,
		Integer serviceAddress,
		byte... path
	)
	{
		UartbusServiceBridge ret = new UartbusServiceBridge
		(
			service,
			bus::sendRawPacket,
			UbRpcTools.createRequestPacketAcceptor(serviceAddress, path)
		);
		ret.setAssociatedBus(bus);
		SimplePublish1<byte[]> feed = ret::feedPacket;
		bus.getUnrelatedPacketListener().addEventListener(feed);
		ret.onClose.add(new Closeable()
		{
			@Override
			public void close() throws IOException
			{
				bus.getUnrelatedPacketListener().removeEventListener(feed);
			}
		});
		return ret;
	}

	@Override
	public void close() throws IOException
	{
		for(AutoCloseable c:onClose)
		{
			IOTools.silentClose(c);
		}
	}
}
