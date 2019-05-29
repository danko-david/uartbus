

#ifndef UB_APP_H
#define UB_APP_H

#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdlib.h>


void register_packet_dispatch(void (*addr)(struct rpc_request* req));

/*
extern void (*register_packet_dispatch)(void (*)(int16_t, int16_t, uint8_t*, uint8_t));
extern bool (*may_send_packet)();
extern bool (*send_packet)(int16_t, uint8_t* , uint16_t);
extern uint8_t (*get_max_packet_size)();
*/

bool send_packet(int16_t to, int NS, uint8_t* data, uint16_t size);

uint32_t micros();

void init_ub_app();

__attribute__ ((weak)) void setup();

__attribute__ ((weak)) void loop();

#endif
