
#include "ub.h"

#ifndef _UB_ARDUINO_H_
#define _UB_ARDUINO_H_

#ifdef ARDUINO

struct uartbus_arduino
{
	struct uartbus bus;
	uint8_t* receive_data;
	uint8_t receive_ep;
	uint8_t receive_size;
	Stream* stream;
	uint8_t* send_data;
	uint8_t send_size;
	void (*packet_received)(struct uartbus_arduino* bus, uint8_t* data, uint16_t size);
	void* user_data;
};

void arduino_ub_init
(
	struct uartbus_arduino* init,
	Stream* stream,
	uint32_t baudRate,
	uint8_t maxPacketSize,
	void (*onPacketReceived)(struct uartbus_arduino* bus, uint8_t* data, uint16_t size),
	void* user_data
);


bool arduino_ub_send_packet(struct uartbus_arduino* bus, uint8_t* data, uint8_t size);
bool arduino_ub_send_packet_crc(struct uartbus_arduino* bus, uint8_t* data, uint8_t size);
void arduino_ub_manage_bus(struct uartbus_arduino* bus);

#endif
#endif
