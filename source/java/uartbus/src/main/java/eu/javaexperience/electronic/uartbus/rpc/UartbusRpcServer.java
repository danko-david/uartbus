package eu.javaexperience.electronic.uartbus.rpc;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

import eu.javaexperience.cli.CliEntry;
import eu.javaexperience.cli.CliTools;
import eu.javaexperience.collection.map.MapTools;
import eu.javaexperience.datareprez.DataObject;
import eu.javaexperience.electronic.SerialTools;
import eu.javaexperience.electronic.uartbus.UartbusEscapedStreamPacketConnector;
import eu.javaexperience.electronic.uartbus.UartbusPacketConnector;
import eu.javaexperience.electronic.uartbus.rpc.client.UartbusRpcClientTools;
import eu.javaexperience.interfaces.simple.SimpleGet;
import eu.javaexperience.interfaces.simple.getBy.GetBy1;
import eu.javaexperience.interfaces.simple.getBy.GetBy2;
import eu.javaexperience.interfaces.simple.publish.SimplePublish1;
import eu.javaexperience.io.IOStream;
import eu.javaexperience.io.IOTools;
import eu.javaexperience.io.fd.IOStreamFactory;
import eu.javaexperience.log.JavaExperienceLoggingFacility;
import eu.javaexperience.log.LogLevel;
import eu.javaexperience.log.Loggable;
import eu.javaexperience.log.Logger;
import eu.javaexperience.log.LoggingTools;
import eu.javaexperience.multithread.TaskExecutorPool;
import eu.javaexperience.reflect.Mirror;
import eu.javaexperience.rpc.JavaClassRpcUnboundFunctionsInstance;
import eu.javaexperience.rpc.RpcProtocolHandler;
import eu.javaexperience.rpc.RpcSession;
import eu.javaexperience.rpc.RpcSessionTools;
import eu.javaexperience.rpc.RpcTools;
import eu.javaexperience.rpc.SimpleRpcRequest;
import eu.javaexperience.rpc.SimpleRpcSession;
import eu.javaexperience.rpc.SocketRpcServer;
import eu.javaexperience.rpc.bidirectional.BidirectionalRpcDefaultProtocol;
import eu.javaexperience.semantic.references.MayNull;

import static eu.javaexperience.electronic.uartbus.rpc.UartbusCliTools.*;

/*
import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
*/

//import com.fazecast.jSerialComm.SerialPort;

public class UartbusRpcServer
{
	protected static final Logger LOG = JavaExperienceLoggingFacility.getLogger(new Loggable("UartbusRpcServer"));
	
	protected static final CliEntry<Integer> RPC_PORT = CliEntry.createFirstArgParserEntry
	(
		(e)->Integer.parseInt(e),
		"Rpc listen port",
		"p", "-port"
	);

	protected static final CliEntry<String> SERIAL_DEV = CliEntry.createFirstArgParserEntry
	(
		(e)->e,
		"Serial device path",
		"s", "-serial-device"
	);
	
	protected static final CliEntry<String> DIRECT_SERIAL_DEVICE = CliEntry.createFirstArgParserEntry
	(
		(e)->e,
		"Direct serial driver",
		"i", "-direct-serial-device"
	);
	
	//this makes possible to open an RPC server to a bus subnet, eg a whole network available trough device 12 with a specific namespace.
	public static final CliEntry<String> MODE = CliEntry.createFirstArgParserEntry
	(
		(e)->e,
		"Rpc backend mode (serial, dummy)",
		"m", "-mode"
	);

	protected static final CliEntry[] PROG_CLI_ENTRIES =
	{
		WORK_DIR,//-d
		RPC_PORT,//-p
		SERIAL_DEV,//-s
		SERIAL_BAUD,//-b
		RECONNECT,//-r
		DIRECT_SERIAL_DEVICE,//-i
		MODE//-m
	};
	
	public static void printHelpAndExit(int exit)
	{
		System.err.println("Usage of UarbusRpcServer:\n");
		System.err.println(CliTools.renderListAllOption(PROG_CLI_ENTRIES));
		System.exit(1);
	}

/*
	public static IOStream connect(String port, int baud) throws Exception
	{
		SerialPort ser = SerialPort.getCommPort(port);
		ser.setBaudRate(baud);
		ser.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);
		ser.setParity(SerialPort.ODD_PARITY);
		
		ser.openPort(200);
		InputStream in = ser.getInputStream();
		OutputStream out = ser.getOutputStream();
		return new IOStream()
		{
			protected boolean closed = false;
			@Override
			public void flush() throws IOException
			{
				out.flush();
			}
			
			@Override
			public String remoteAddress()
			{
				return "Serial: "+port;
			}
			
			@Override
			public String localAddress()
			{
				return remoteAddress();
			}
			
			@Override
			public boolean isClosed()
			{
				return closed;
			}
			
			@Override
			public OutputStream getOutputStream()
			{
				return out;
			}
			
			@Override
			public InputStream getInputStream()
			{
				return in;
			}
			
			@Override
			public void close()
			{
				this.closed = true;
			}
		};
	}
*/
	
/*
	public static IOStream connect(String port, int baud) throws Exception
	{
		CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(port);
//		if(portIdentifier.isCurrentlyOwned())
//		{
//			throw new RuntimeException("Error: Port is currently in use by: "+portIdentifier.getCurrentOwner());
//		}
//		else
		{
			CommPort commPort = portIdentifier.open(port, 6000);
			try
			{
				System.out.println(commPort.getClass()+": "+commPort.getName());
				
				if (commPort instanceof SerialPort)
				{
					SerialPort ser = (SerialPort) commPort;
					ser.setBaudBase(baud);
					ser.setSerialPortParams(baud, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
				}
				
//				if (commPort instanceof SerialPort)
//				{
//					System.out.println("Connect 2/2");
//					SerialPort serialPort = (SerialPort) commPort;
//					System.out.println("BaudRate: " + serialPort.getBaudRate());
//					System.out.println("DataBIts: " + serialPort.getDataBits());
//					System.out.println("StopBits: " + serialPort.getStopBits());
//					System.out.println("Parity: " + serialPort.getParity());
//					System.out.println("FlowControl: " + serialPort.getFlowControlMode());
//					serialPort.setSerialPortParams(4800,SerialPort.DATABITS_8,SerialPort.STOPBITS_1,SerialPort.PARITY_ODD);
//					serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_RTSCTS_IN);
//					System.out.println("BaudRate: " + serialPort.getBaudRate());
//					System.out.println("DataBIts: " + serialPort.getDataBits());
//					System.out.println("StopBits: " + serialPort.getStopBits());
//					System.out.println("Parity: " + serialPort.getParity());
//					System.out.println("FlowControl: " + serialPort.getFlowControlMode());
//				}
				
				InputStream in = commPort.getInputStream();
				OutputStream out = commPort.getOutputStream();
				
				return new IOStream()
				{
					protected boolean closed = false;
					@Override
					public void flush() throws IOException
					{
						out.flush();
					}
					
					@Override
					public String remoteAddress()
					{
						return "Serial: "+port;
					}
					
					@Override
					public String localAddress()
					{
						return remoteAddress();
					}
					
					@Override
					public boolean isClosed()
					{
						return closed;
					}
					
					@Override
					public OutputStream getOutputStream()
					{
						return out;
					}
					
					@Override
					public InputStream getInputStream()
					{
						return in;
					}
					
					@Override
					public void close()
					{
						this.closed = true;
					}
				};
			}
			catch(Exception e)
			{
				commPort.close();
				throw e;
			}
		}
	}
*/

	protected static UartbusPacketConnector createSerialPacketConnector(Map<String, List<String>> args)
	{
		String serial = SERIAL_DEV.tryParseOrDefault(args, null);
		int baud = SERIAL_BAUD.tryParseOrDefault(args, -1);
		String directCommand = DIRECT_SERIAL_DEVICE.tryParseOrDefault(args, null);
		
		boolean reconnect = false;
		
		if(RECONNECT.hasOption(args))
		{
			reconnect = true;
		}
		
		boolean error = false;
		if(null == serial)
		{
			System.err.println("No Serial port specified.");
			error = true;
		}
		
		if(-1 == baud)
		{
			System.err.println("No Serial baud rate specified.");
			error = true;
		}
		
		if(error)
		{
			return null;
		}
		
		IOStream[] prev = new IOStream[1];
		SimpleGet<IOStream> socketFactory = UartbusRpcClientTools.waitReconnect
		(
			()->
			{
				try
				{
					if(null != prev[0])
					{
						IOTools.silentClose(prev[0]);
					}
					
					if(null != directCommand)
					{
						prev[0] = SerialTools.openDirectSerial(directCommand, serial, baud);
					}
					else
					{
						//TODO
						prev[0] = SerialTools.openSerial(serial, baud);
						//prev[0] = connect(serial, baud);
						
						/**
						 * What is this wait:
						 * When serial device openend it causes arduino to
						 * restart and go bootloader mode. (This is the method
						 * for code upload process)
						 * 
						 * If we connect and beneath we send some data, the
						 * bootloader won't let to go in normal operation (to
						 * run the bus gateway application code), instead it's
						 * waiting for new lode to upload.
						 * 
						 * This extra wait, cause the bootloader a timeout.
						 * */
						Thread.sleep(2000);
					}
					prev[0].getInputStream();
					prev[0].getOutputStream();
					return prev[0];
				}
				catch (Exception e)
				{
					Mirror.propagateAnyway(e);
					return null;
				}
			},
			"serial connection to the bus gateway"
		);
		
		UartbusEscapedStreamPacketConnector conn = new UartbusEscapedStreamPacketConnector(socketFactory.get(), (byte)0xff);
		if(reconnect)
		{
			conn.setSocketCloseListener(()->
			{
				LoggingTools.tryLogFormat(LOG, LogLevel.ERROR, "Socket error, reconnecting");
				conn.setIoStream(socketFactory.get());
			});
		}
		else
		{
			conn.setSocketCloseListener(()->System.exit(5));
		}
		
		IOTools.closeOnExit(conn);
		
		return conn;
	}
	
	public static UartbusPacketConnector createDummyConnector()
	{
		return createDummyConnector(null);
	}
	
	public static UartbusPacketConnector createDummyConnector(Map<String, List<String>> args)
	{
		return new UartbusPacketConnector()
		{
			@Override
			public void close() throws IOException {}
			
			@Override
			public void startListen() {}
			
			@Override
			public void setPacketHook(SimplePublish1<byte[]> process){}
			
			@Override
			public void sendPacket(byte[] data){}
		};
	}
	
	protected static String getMode(Map<String, List<String>> args)
	{
		return MODE.tryParseOrDefault(args, "serial");
	}
	
	protected static UartbusPacketConnector createPacketConnector(Map<String, List<String>> args)
	{
		String mode = getMode(args);
		
		switch(mode)
		{
		case "serial":
			return createSerialPacketConnector(args);
		
		case "dummy":
			return createDummyConnector(args);
			
		default:
			return null;
		}
	}
	
	public static void main(String[] args) throws Throwable
	{
		JavaExperienceLoggingFacility.addStdOut();
		Map<String, List<String>> pa = CliTools.parseCliOpts(args);
		String un = CliTools.getFirstUnknownParam(pa, PROG_CLI_ENTRIES);
		if(null != un)
		{
			CliTools.printHelpAndExit("UarbusRpcServer", 1, PROG_CLI_ENTRIES);
		}
		
		String wd = WORK_DIR.tryParseOrDefault(pa, ".");
		int port = RPC_PORT.tryParseOrDefault(pa, 2112);
		
		UartbusPacketConnector conn = createPacketConnector(pa);
		
		if(null == conn)
		{
			CliTools.printHelpAndExit("UarbusRpcServer", 2, PROG_CLI_ENTRIES);
		}
		
		//JavaExperienceLoggingFacility.startLoggingIntoDirectory(new File(wd+"/log/"), "uartbus-rpc-server-");
		
		
		UartbusRpcEndpoint bus = new UartbusRpcEndpoint(conn);
		boolean dummy = "dummy".equals(getMode(pa));
		bus.default_loopback_send_packets = dummy;
		bus.default_echo_loopback = !dummy;
		
		GetBy1<DataObject, SimpleRpcRequest> dispatcher = RpcTools.createSimpleNamespaceDispatcherWithDiscoverApi
		(
			new JavaClassRpcUnboundFunctionsInstance<>("uartbus", bus, UartbusConnection.class)
		);
		
		GetBy2<SimpleRpcSession, IOStream, RpcProtocolHandler> sessionCreator = RpcTools.getSimpleSessionCreator();
		
		Executor exec =
			//new ThreadPoolExecutor(30, 300, 60, TimeUnit.SECONDS, new LinkedBlockingDeque<>());
			new TaskExecutorPool();
		
		AtomicLong ref = new AtomicLong();
/*		ConcurrentHashMap<Long, Boolean> missed = new ConcurrentHashMap<>();
		
		exec.execute(()->
		{
			while(true)
			{
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				System.out.println(MapTools.toStringMultiline(missed));
			}
		});
*/		
		SocketRpcServer<IOStream, RpcSession> srv = new SocketRpcServer<IOStream, RpcSession>
		(
			IOStreamFactory.fromServerSocket(new ServerSocket(port)),
			5,
			BidirectionalRpcDefaultProtocol.DEFAULT_PROTOCOL_HANDLER_WITH_CLASS
		)
		{
			@Override
			protected RpcSession init(IOStream socket)
			{
				return sessionCreator.getBy(socket, handler);
			}
			
			@Override
			protected void responseRequest(SimplePublish1<DataObject> response, RpcSession sess, DataObject request, Object extraCtx)
			{
				long val = ref.incrementAndGet();
				//missed.put(val, Boolean.TRUE);
				
				//new Thread
				exec.execute
				(
					()->
					{
						RpcSessionTools.setCurrentRpcSession(sess);
						try
						{
							DataObject resp = handleRequest(sess, request);
							if(null != resp)
							{
								response.publish(resp);
							}
							else
								
							System.out.println("NULL response");
						}
						catch(Throwable t)
						{
							t.printStackTrace();
						}
						finally
						{
							RpcSessionTools.setCurrentRpcSession(null);
						}
						//missed.remove(val);
					}
				)
				
				//.start()
				;
			}

			@Override
			protected @MayNull DataObject handleRequest(RpcSession sess, DataObject request)
			{
				return dispatcher.getBy(new SimpleRpcRequest(sess, request));
			}
		};
		
		srv.start();
	}
	
	
	public static UartbusRpcEndpoint createInProcessDummyServer()
	{
		UartbusPacketConnector conn = createDummyConnector(null);
		UartbusRpcEndpoint bus = new UartbusRpcEndpoint(conn);
		bus.default_loopback_send_packets = true;
		bus.default_echo_loopback = false;
		return bus;
	}
}
