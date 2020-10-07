
#include "rpc.h"

__attribute__((noinline)) uint8_t arr_size(void** arr)
{
	return (uint8_t) ((uint16_t) arr[0]);
}

uint8_t rpc_append_size(uint8_t args, struct response_part** parts)
{
	int ds = 0;
	for(uint8_t i=0;i<args;++i)
	{
		ds += parts[i]->size;
	}
	
	return ds;
}

/**
 * returns the copied size,on error returns:
 *  - -EMSGSIZE:size is too long for the buffer.
 */
int16_t rpc_append_arr(uint8_t* dst, uint8_t size, uint8_t args, struct response_part** parts)
{
	int ds = 0;
	for(uint8_t i=0;i<args;++i)
	{
		struct response_part* data = parts[i];
		if(size > data->size)
		{
			return -EMSGSIZE;
		}
		uint8_t bs = data->size;
		for(uint8_t i=0;i<bs;++i)
		{
			dst[i] = data->data[i];
		}
		
		dst += bs;
		size -= bs;
		ds += bs;
	}
	
	return ds;
}

bool il_reply_arr(struct rpc_request* req, uint8_t* data, uint8_t size)
{
	struct response_part e;
	e.size = size;
	e.data = data;
	
	struct response_part* arr[1];
	arr[0] = &e;
	
	return req->reply(req, 1, arr);
}

bool il_reply(struct rpc_request* req, uint8_t size, ...)
{
	uint8_t* d = (uint8_t*) alloca(size);
	
	va_list v;
	va_start(v, size);
	for(uint8_t i=0;i<size;++i)
	{
		d[i] = (uint8_t) va_arg(v, int);
	}
	va_end(v);
	
	struct response_part e;
	e.size = size;
	e.data = d;
	
	struct response_part* arr[1];
	arr[0] = &e;
	return req->reply(req, 1, arr);
}


bool try_consume_path(struct rpc_request* req, uint8_t size, ...)
{
	va_list v;
	va_start(v, size);
	
	if(req->size - req->procPtr < size)
	{
		return false;
	}
	
	for(uint8_t i=0;i<size;++i)
	{
		if(req->payload[req->procPtr+i] != (uint8_t) va_arg(v, int))
		{
			return false;
		}
	}
	va_end(v);
	
	req->procPtr += size;
	
	return true;
}

/****************************** Dispatch utils ********************************/

void dispatch_function_chain(void** chain, struct rpc_request* req)
{
	if((req->size - req->procPtr) > 0)
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
	if((req->size - req->procPtr) > 0)
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

