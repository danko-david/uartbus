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

struct response_part
{
	uint8_t size;
	uint8_t* data;
};

struct rpc_request
{
	int16_t from;
	int16_t to;
	uint8_t* payload;
	uint8_t size;
	uint8_t procPtr;
	
	int16_t (*reply)(struct rpc_request* req, uint8_t args, struct response_part** parts);
};

uint8_t rpc_append_size(uint8_t args, struct response_part** parts);
int16_t rpc_append_arr(uint8_t* dst, uint8_t size, uint8_t args, struct response_part** parts);

bool il_reply_arr(struct rpc_request* req, uint8_t* data, uint8_t size);
bool il_reply(struct rpc_request* req, uint8_t size, ...);

void dispatch_function_chain(void** chain, struct rpc_request* req);
void dispatch_descriptor_chain(void** chain, struct rpc_request* req);


#endif /* RPC_H_ */
