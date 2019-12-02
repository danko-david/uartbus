
#include "rpc_node.h"
#include <stdlib.h>

struct rpc_function_parameter* rpc_il_create_parameter
(
	enum rpc_data_type value_type,
	uint8_t data_size,
	const char* param_name,
	const char* param_description
)
{
	struct rpc_function_parameter* ret = calloc(1, sizeof(struct rpc_function_parameter));
	ret->data_type = value_type;
	ret->data_size_bytes = data_size;
	ret->name = param_name;
	ret->descr = param_description;
	return ret;
}

struct rpc_node* rpc_il_create_ns_node
(
	int ns,
	const char* name,
	uint8_t subnodes,
	/*struct rpc_node* */ ...
)
{
	struct rpc_node* ret = calloc(1, sizeof(struct rpc_node));
	ret->ns = ns,
	ret->name = name;
	ret->modifiers = rpc_modifier_namespace;
	ret->name = name;
	ret->sub_nodes = malloc((subnodes+1)*sizeof(void*));
	ret->sub_nodes[subnodes] = NULL;

	va_list v;
	va_start(v, subnodes);
	for(uint8_t i=0;i<subnodes;++i)
	{
		ret->sub_nodes[i] = va_arg(v, struct rpc_node*);
	}
	va_end(v);

	return ret;
}

struct rpc_node* rpc_il_create_function_node
(
	int ns,
	const char* name,
	void (*rpc_function)(struct rpc_request*),
	uint8_t return_params,
	uint8_t request_params,
	/* struct rpc_function_parameter* */...
)
{
	struct rpc_node* ret = calloc(1, sizeof(struct rpc_node));
	ret->ns = ns,
	ret->name = name;
	ret->modifiers = rpc_modifier_function;
	ret->name = name;

	if(0 != return_params + request_params)
	{
		ret->func_descriptor = calloc(1, sizeof(struct rpc_function_descriptor));
		va_list v;
		va_start(v, request_params);
		if(0 != return_params)
		{
			ret->func_descriptor->return_values = malloc((return_params+1)*sizeof(void));
			ret->func_descriptor->return_values[return_params] = NULL;
			for(uint8_t i=0;i<return_params;++i)
			{
				ret->func_descriptor->return_values[i] = va_arg(v, struct rpc_function_parameter*);
			}
		}

		if(0 != request_params)
		{
			ret->func_descriptor->parameters = malloc((request_params+1)*sizeof(void));
			ret->func_descriptor->parameters[request_params] = NULL;
			for(uint8_t i=0;i<request_params;++i)
			{
				ret->func_descriptor->parameters[i] = va_arg(v, struct rpc_function_parameter*);
			}
			va_end(v);
		}
	}

	return ret;
}

struct rpc_node* rpc_get_ns(struct rpc_node* node, uint8_t ns)
{
	if(NULL != node->sub_nodes)
	{
		for(uint16_t i = 0;i<256;++i)
		{
			struct rpc_node* at = node->sub_nodes[i];
			if(NULL == at)
			{
				return NULL;
			}
			else if(ns == at->ns)
			{
				return at;
			}
		}
	}

	return NULL;
}

/*





*/


bool rpc_dispatch_with_mode
(
	struct rpc_node* node,
	struct rpc_request* req,
	enum rpc_dispatch_mode mode
)
{
	//walk the namespaces

	struct rpc_node* crnt = node;
	//while(node->modifiers & rpc_modifier_namespace)
	if(node->modifiers & rpc_modifier_namespace)
	{
		if()
		{

		}
	}

}

bool rpc_dispatch_root(struct rpc_node* root, struct rpc_request* req)
{
	if(req->procPtr >= req->size)
	{
		return false;
	}

	int ns = req->payload[req->procPtr];

	switch(ns)
	{
	case 0:
		//dispatch response
		++req->procPtr;
		return rpc_dispatch_with_mode(root, req, rpc_dispatch_mode_response);


	case 255:
		//dispatch reflect request

		//TODO handle reflect other way
		++req->procPtr;
		return rpc_dispatch_with_mode(root, req, rpc_dispatch_mode_reflect);

	default:
		//dispatch request
		return rpc_dispatch_with_mode(root, req, rpc_dispatch_mode_request);
	}
}
