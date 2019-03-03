
#include "rpc.h"

__attribute__((noinline)) uint8_t arr_size(void** arr)
{
	return (uint8_t) (((uint16_t) arr[0]));
}

bool il_reply_arr(struct rpc_request* req, uint8_t* data, uint8_t size)
{
	int neg = req->procPtr-1;
	uint8_t* d = (uint8_t*) alloca(size+neg);

	for(uint8_t i=0;i<neg;++i)
	{
		d[i] = req->payload[i+1];
	}

	for(uint8_t i=0;i<size;++i)
	{
		d[neg+i] = data[i];
	}

	return send_packet_priv(req->from, req->payload[0], d, size+neg);
}

bool il_reply(struct rpc_request* req, uint8_t size, ...)
{
	int neg = req->procPtr-1;
	uint8_t* d = (uint8_t*) alloca(size+neg);
	
	for(uint8_t i=0;i<neg;++i)
	{
		d[i] = req->payload[i+1];
	}
	
	va_list v;
	va_start(v, size);
	for(uint8_t i=0;i<size;++i)
	{
		d[neg+i] = (uint8_t) va_arg(v, int);
	}
	va_end(v);
	return send_packet_priv(req->from, req->payload[0], d, size+neg);
}

/****************************** Dispatch utils ********************************/

void dispatch_function_chain(void** chain, struct rpc_request* req)
{
	if(0 != (req->size - req->procPtr))
	{
		uint8_t cs = arr_size(chain);
		if(req->payload[req->procPtr] < cs)
		{
			void (*func)(struct rpc_request* req)
			= (void (*)(struct rpc_request* req))
			chain[req->payload[req->procPtr]+1];
			if(NULL != func)
			{
				++req->procPtr;
				func(req);
				return;
			}
		}
		il_reply(req, 1, ENOSYS);
		return;
	}
	il_reply(req, 1, EBADF);
}

void dispatch_descriptor_chain(void** chain, struct rpc_request* req)
{
	if(0 != (req->size - req->procPtr))
	{
		uint8_t cs = arr_size(chain);
		if(req->payload[req->procPtr] < cs)
		{
			void* fc = chain[req->payload[req->procPtr]+1];
			if(NULL != fc)
			{
				++req->procPtr;
				dispatch_function_chain((void**) fc, req);
				return;
			}
		}
		il_reply(req, 1, ENOSYS);
		return;
	}
	il_reply(req, 1, EBADF);
}


//TODO minimize these functions

/*
bool il_send(int16_t to, uint8_t ns, uint8_t size, ...)
{
	uint8_t* d = (uint8_t*) alloca(size);
	va_list v;
	va_start(v, size);
	for(uint8_t i=0;i<size;++i)
	{
		d[i] = (uint8_t) va_arg(v, int);
	}
	va_end(v);
	return send_packet_priv(to, ns, d, size);
}*/



