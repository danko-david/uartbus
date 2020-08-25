package eu.javaexperience.electronic.uartbus.rpc;

public enum UbRpcDataType
{
	rpc_data_type_none,
	rpc_data_type_void,
	rpc_data_type_errno,
	rpc_data_type_int,
	rpc_data_type_uint,
	rpc_data_type_vint,
	rpc_data_type_vuint,
	rpc_data_type_string,
	rpc_data_type_float,
	rpc_data_type_blob,//0 escaped byte sequence
}
