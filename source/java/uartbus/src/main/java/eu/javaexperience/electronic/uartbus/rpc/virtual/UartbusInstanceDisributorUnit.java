package eu.javaexperience.electronic.uartbus.rpc.virtual;

import java.io.IOException;

import eu.javaexperience.electronic.uartbus.rpc.UartbusConnection;
import eu.javaexperience.rpc.RpcSession;
import eu.javaexperience.rpc.RpcSessionTools;
import eu.javaexperience.rpc.SimpleRpcSession;
import eu.javaexperience.rpc.bidirectional.BidirectionalRpcDefaultProtocol;

public class UartbusInstanceDisributorUnit extends UartbusThreadLocalDistributor
{
	protected RpcSession session = new SimpleRpcSession(BidirectionalRpcDefaultProtocol.DEFAULT_PROTOCOL_HANDLER);
	
	public UartbusInstanceDisributorUnit(UartbusConnection conn)
	{
		super(conn);
	}
	
	@Override
	public void close() throws IOException
	{
		super.close();
		conn.close();
		session.getExtraDataMap().clear();
	}


	@Override
	protected void setBeforeCall()
	{
		RpcSessionTools.setCurrentRpcSession(session);
	}


	@Override
	protected void resetAfterCall()
	{
		RpcSessionTools.setCurrentRpcSession(null);
	}
}
