/*
 * ub.h
 *
 *  Created on: 2019.03.02.
 *      Author: Dankó Dávid
 */

#ifndef RPC_H_
#define RPC_H_

#include "stdint.h"
#include <stdbool.h>
#include <stddef.h>
#include <stdarg.h>
#include <alloca.h>
#include "posix_errno.h"

struct rpc_request
{
	int16_t from;
	int16_t to;
	uint8_t* payload;
	uint8_t size;
	uint8_t procPtr;
};

extern bool send_packet_priv(int16_t to, uint8_t NS, uint8_t* data, uint8_t size);

bool il_reply_arr(struct rpc_request* req, uint8_t* data, uint8_t size);
bool il_reply(struct rpc_request* req, uint8_t size, ...);

//bool il_send(int16_t to, uint8_t ns, uint8_t size, ...);

void dispatch_function_chain(void** chain, struct rpc_request* req);
void dispatch_descriptor_chain(void** chain, struct rpc_request* req);


#endif /* RPC_H_ */
