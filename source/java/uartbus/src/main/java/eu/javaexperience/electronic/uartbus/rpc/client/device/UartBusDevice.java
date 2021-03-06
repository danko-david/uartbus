package eu.javaexperience.electronic.uartbus.rpc.client.device;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import eu.javaexperience.datastorage.TransactionException;
import eu.javaexperience.electronic.uartbus.PacketAssembler;
import eu.javaexperience.electronic.uartbus.rpc.client.UartBus;
import eu.javaexperience.electronic.uartbus.rpc.client.UartbusTransaction;
import eu.javaexperience.electronic.uartbus.rpc.datatype.NoReturn;
import eu.javaexperience.pdw.ProxyHelpedLazyImplementation;
import eu.javaexperience.reflect.Mirror;

public class UartBusDevice
{
	protected UartBus bus;
	protected int address;
	
	public long timeout = 100;
	public TimeUnit timeoutUnit = TimeUnit.MILLISECONDS;
	public int retryCount = 5;

	protected final ProxyHelpedLazyImplementation<UbDeviceNs, UbDeviceNsLazyImpl, UbDevStdNsRoot> handler;
	
	public UbDevStdNsRoot getRpcRoot()
	{
		return handler.getRoot();
	}

	public UartBusDevice(UartBus bus, int address)
	{
		this.bus = bus;
		this.address = address;
		handler = createNsHandler(this, UbDevStdNsRoot.class);
	}
	
	public int getAddress()
	{
		return address;
	}
	
	public static <R extends UbDeviceNs> ProxyHelpedLazyImplementation<UbDeviceNs, UbDeviceNsLazyImpl, R> createNsHandler(UartBusDevice device, Class<R> rootCls)
	{
		try
		{
			return new ProxyHelpedLazyImplementation<UbDeviceNs, UbDeviceNsLazyImpl, R>(UbDeviceNs.class, new UbDeviceNsLazyImpl(device), rootCls)
			{
				@Override
				public Object handleInterfaceCall
				(
					UbDeviceNsLazyImpl root,
					Method method,
					Object[] params
				)
					throws Throwable
				{
					if(UbDeviceNs.class.isAssignableFrom(method.getReturnType()))
					{
						//wrapping the NS
						if("cast".equals(method.getName()))
						{
							return wrapWithClass((Class) params[0], root);
						}
						else if("customNs".equals(method.getName()))
						{
							return stepPath(root, (Class) params[0], true, (short) params[1]);
						}
						
						UbIndex ui = method.getAnnotation(UbIndex.class);
						/*if(null == ui)
						{
							throw new RuntimeException("No namespace index specified for "+method);
						}*/
						return stepPath(root, (Class<R>) method.getReturnType(), null != ui, null == ui?-1:ui.ns(), params);
					}
					
					return device.handleDeviceRpcCall(root.path, method, params);
				}
				
				public Object stepPath(UbDeviceNsLazyImpl root, Class<R> cls, boolean hasNs, short ns, Object... objs) throws Exception
				{
					if(null == objs || 0 == objs.length)
					{
						byte[] p = Arrays.copyOf(root.path, root.path.length+1);
						p[root.path.length] = (byte) ns;
						return wrapWithClass(cls, new UbDeviceNsLazyImpl(root.dev, p));
					}
					
					try(PacketAssembler pa = new PacketAssembler())
					{
						pa.write(root.path);
						if(hasNs)
						{
							pa.writeByte(ns);
						}
						
						UbRpcTools.appendElements(pa, objs);
						
						return wrapWithClass(cls, new UbDeviceNsLazyImpl(root.dev, pa.done()));
					}
				}
				
				@Override
				protected void handleException(Throwable e)
				{
					Mirror.throwCheckedExceptionAsUnchecked(e);
				}
			};
		}
		catch(Exception e)
		{
			Mirror.propagateAnyway(e);
			return null;
		}
	}

	protected Object handleDeviceRpcCall(byte[] path, Method method, Object[] params) throws Exception
	{
		PacketAssembler pa = new PacketAssembler();
		
		UbIndex ns = method.getAnnotation(UbIndex.class);
		if(null == ns)
		{
			throw new IllegalStateException("Uartbus RPC function doesn't have @UbIndex annotation used to identify RPC function index: "+method);
		}
		
		path = Arrays.copyOf(path, path.length+1);
		path[path.length-1] = (byte) ns.ns();
		UbRpcTools.appendElements(pa, path);
		if(null != params && params.length > 0)
		{
			UbRpcTools.appendElements(pa, params);
		}
		
		Type ret = method.getReturnType();
		TransactionException trex = null;
		byte[] data = pa.done();
		boolean mayRetransmit = null != method.getAnnotation(UbRetransmittable.class);
		for(int i=0;i<retryCount;++i)
		{
			try(UartbusTransaction tr = bus.newTransaction(address, path, data, true))
			{
				if(NoReturn.class == ret)
				{
					return null;
				}
				
				try
				{
					byte[] res = tr.ensureResponse(timeout, timeoutUnit, method.toString());
					
					return UbRpcTools.extractOrThrowResult
					(
						method,
						Arrays.copyOfRange(res, path.length+1, res.length)
					);
				}
				catch(TransactionException ex)
				{
					if(!mayRetransmit)
					{
						throw ex;
					}
					trex = ex;
				}
			}
		}
		TransactionException te = new TransactionException("Device not responded after "+retryCount+" attempt.");
		te.addSuppressed(trex);
		throw te;
	}

	public void setTimeout(long timeout, TimeUnit unit)
	{
		this.timeout = timeout;
		this.timeoutUnit = unit;
	}

	public UartBus getBus()
	{
		return bus;
	}
}
