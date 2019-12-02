

/**
 * A node definition suitalble to:
 * 	- bind them together like a tree
 * 	- store RPC function pointer
 * 	- dispatch the requests
 * 	- dispatch the responses
 * 	- provide functionality to discover the tree
 *	- ability to describe function and return value parameters
 *
 *
 *
 *
 * */
#ifndef RPC_NODE_H_
#define RPC_NODE_H_

#include "rpc.h"

enum rpc_modifiers
{
	rpc_modifier_function = 1,
	rpc_modifier_namespace = 2,

	rpc_modifier_advanced_handler = 4,
};

enum rpc_data_type
{
	/**
	 * When a function ever returns like hardware_reset();
	 * */
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

	//TODO object/array?
	//TODO specify length {u,}int{8,16,32,64}_t
};

struct rpc_function_parameter
{
	enum rpc_data_type data_type;
	uint16_t data_size_bytes;
	const char* name;
	const char* descr;
	const char* meta;
};

struct rpc_function_descriptor
{
	struct rpc_function_parameter** return_values;
	struct rpc_function_parameter** parameters;
};

struct rpc_node
{
	uint8_t ns;
	
	uint8_t modifiers;
	
	const char* name;

	const char* description;

	const char* meta;

	struct rpc_function_descriptor* func_descriptor;

	union
	{
		void (*rpc_function)(struct rpc_request*);
		bool (*rpc_ext_function)(struct rpc_node*, struct rpc_request*, uint8_t req_mode);
		struct rpc_node** sub_nodes;
	};
	
	void* user_data;
	//TODO response function hook
};


bool rpc_dispatch_request(struct rpc_node* root, struct rpc_request*);

bool rpc_dispatch_response(struct rpc_node* root, struct rpc_request*);

bool rpc_dispatch_reflect_request(struct rpc_node* root, struct rpc_request*);


/**
 * Dispatches the request. If the first namespace is:
 *	- 255 we dispatch as a reflection request,
 *	- 0 we dispatch as a response packet (the registered response function
 *  	handles the packet)
 *  - otherwise dispatch as a request packet, the registered handler function
 *		will handle the packet.
 */
bool rpc_dispatch_root(struct rpc_node*, struct rpc_request*);

//struct rpc_node* rpc_create_namespace_node(int ns, const char* name, struct rpc_node** sub_nodes);

struct rpc_node* rpc_il_create_ns_node(int ns, const char* name, uint8_t subnodes, /*struct rpc_node* */ ...);

struct rpc_node* rpc_il_create_function_node
(
	int ns,
	const char* name,
	void (*rpc_function)(struct rpc_request*),
	uint8_t return_params,
	uint8_t request_params,
	/* struct rpc_function_parameter* */...
);

struct rpc_function_parameter* rpc_il_create_parameter
(
	enum rpc_data_type value_type,
	uint8_t data_size,
	const char* param_name,
	const char* param_description
);

enum rpc_dispatch_mode
{
	rpc_dispatch_mode_request,
	rpc_dispatch_mode_response,
	rpc_dispatch_mode_reflect,
};

bool rpc_dispatch_with_mode
(
	struct rpc_node*,
	struct rpc_request*,
	enum rpc_dispatch_mode
);

#endif
