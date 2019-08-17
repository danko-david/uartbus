

/**
 * A node definition suitalble to:
 * 	- bind them together like a tree
 * 	- store RPC function pointer
 * 	- dispatch the requests
 * 	- dispatch the responses
 * 	- provide functionality to discover the tree
 *	- ability to describe function and return value parameters
 *	- subscribe to group ids
 *
 *
 *
 *
 * */
#ifndef RPC_NODE_H_
#define RPC_NODE_H_

#include "rpc.h";

enum rpc_modifiers
{
	rpc_modifier_function = 1,
};

struct rpc_node
{
	uint8_t ns;
	
	uint8_t modifiers;
	
	char* name;

	union
	{
		bool (rpc_function*)(struct rpc_node*, struct rpc_request*);
		struct rpc_node** sub_nodes;
	}
	
	void* user_data;
	//TODO respone function hook
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
bool rpc_dispatch_root(struct rpc_node* root, struct rpc_request*);

struct rpc_node* rpc_create_node(int ns, const char* name, struct rpc_node** sub_nodes);


#endif
