
#include "rpc_node.h"
#include <stdlib.h>

#define DEBUG_PRINT_STDERR

#ifdef DEBUG_PRINT_STDERR
#include <stdio.h>
#endif

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
	ret->rpc_function = rpc_function;
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

bool rpc_dispatch_with_mode
(
	struct rpc_node* node,
	struct rpc_request* req,
	enum rpc_dispatch_mode mode
)
{
	if(mode <= rpc_dispatch_mode_response)
	{
		//walk the namespaces
		struct rpc_node* crnt = node;
		while(req->procPtr < req->size && NULL != crnt)
		{
			if(crnt->modifiers & rpc_modifier_namespace)
			{
				crnt = rpc_get_ns(crnt, req->payload[req->procPtr++]);
			}

			if(NULL != crnt && (crnt->modifiers & rpc_modifier_function))
			{
				if(crnt->modifiers & rpc_modifier_advanced_handler)
				{
					return crnt->rpc_ext_function(crnt, req, mode);
				}
				else if(mode == rpc_dispatch_mode_request)
				{
					crnt->rpc_function(req);
					return true;
				}
				return false;
			}
		}
		return false;
	}
	return false;
}

bool rpc_handle_remote_string
(
	struct rpc_request* req,
	char* str
)
{
#ifdef DEBUG_PRINT_STDERR
	fprintf(stderr, "rpc_handle_remote_string(req, \"%s\"): f: %d\n", str, req->payload[req->procPtr]);
#endif
	if(req->procPtr >= req->size)
	{
		return false;
	}

	switch(req->payload[req->procPtr++])
	{
		case 1: il_reply(req, 1, strlen(str)); return true;
		case 2:
			if(req->size < req->procPtr+1)
			{
				return false;
			}
			uint8_t from = req->payload[req->procPtr];
			uint8_t len = strlen(str);
			if(from >= len)
			{
				il_reply(req, 0);
				return true;
			}
			len -= from;
			if(len > req->payload[req->procPtr+1])
			{
				len = req->payload[req->procPtr+1];
			}
			il_reply_arr(req, str+from, len);
			return true;
		default: return false;
	}
}

uint8_t rpc_get_subnode_count(struct rpc_node* node)
{
	if(0 == (node->modifiers & rpc_modifier_namespace))
	{
		return 0;
	}

	uint8_t ret = 0;
	while(NULL != node->sub_nodes[ret])
	{
		++ret;
	}
#ifdef DEBUG_PRINT_STDERR
	fprintf(stderr, "sub_nodes(%p): %d\n", node, ret);
#endif
	return ret;
}

bool rpc_handle_node_reflection_request
(
	struct rpc_node* node,
	struct rpc_request* req
)
{
#ifdef DEBUG_PRINT_STDERR
	fprintf(stderr, "rpc_handle_node_reflection_request(%p, ...): req: %d, ns: %d\n", node, req->payload[req->procPtr], node->ns);
#endif

	switch(req->payload[req->procPtr++])
	{
	case 1: il_reply(req, 2, 0, node->ns);			return true;
	case 2: il_reply(req, 1, node->modifiers);		return true;
	case 3:	return rpc_handle_remote_string(req, node->name);
	case 4: return rpc_handle_remote_string(req, node->description);
	case 5: return rpc_handle_remote_string(req, node->meta);

	case 9: il_reply(req, 1, rpc_get_subnode_count(node)); return true;

	case 11:
	{
		uint8_t rns = req->payload[req->procPtr];
		#ifdef DEBUG_PRINT_STDERR
			fprintf(stderr, "reflect: nth_subnode_ns(%d) subnodes: %d\n", rns, rpc_get_subnode_count(node));
		#endif
		il_reply(req, 1, rpc_get_subnode_count(node) <= rns ?0:node->sub_nodes[rns]->ns);
		return true;
	}
	default: return false;
	}
}

bool rpc_dispatch_reflect_request
(
	struct rpc_node* node,
	struct rpc_request* req
)
{
#ifdef DEBUG_PRINT_STDERR
	fprintf(stderr, "REQ %d\n", req->payload[req->procPtr]);
#endif

	struct rpc_node* crnt = node;
	//go and final node, process 10:x pattern
	if(10 == req->payload[req->procPtr])
	{
		while(req->procPtr < req->size && NULL != crnt)
		{
			++req->procPtr;
			if(crnt->modifiers & rpc_modifier_namespace)
			{
				crnt = rpc_get_ns(crnt, req->payload[req->procPtr++]);
				#ifdef DEBUG_PRINT_STDERR
					fprintf(stderr, "SEL NODE: %p, %d\n", crnt, NULL == crnt?-1:crnt->ns);
				#endif
			}

			if(10 != req->payload[req->procPtr])
			{
				break;
			}
		}
	}

	if(NULL != crnt)
	{
		return rpc_handle_node_reflection_request(crnt, req);
	}

	return false;
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
		++req->procPtr;
		return rpc_dispatch_reflect_request(root, req);
	default:
		//dispatch request
		return rpc_dispatch_with_mode(root, req, rpc_dispatch_mode_request);
	}
}
