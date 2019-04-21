/*
 * ub.h
 *
 *  Created on: 2017.05.08.
 *      Author: Dankó Dávid
 */

#ifndef UB_H_
#define UB_H_

#include "stdint.h"
#include <stdbool.h>
#include <stddef.h>
#include <stdlib.h>

//TODO this arduino dependency MUST be removed.
#ifdef ARDUINO
	#if ARDUINO >= 100
		#include "Arduino.h"
	#else
		#include "WProgram.h"
	#endif
#endif

#ifndef UINT32_MAX
	#define UINT32_MAX 0xffffffff
#endif

/* Endianess detection
 * http://forum.arduino.cc/index.php?topic=254143.0
 **/
/*
static const union { unsigned char bytes[4]; uint32_t value; } o32_host_order =
    { { 0, 1, 2, 3 } };

enum
{
    O32_LITTLE_ENDIAN = 0x03020100ul,
    O32_BIG_ENDIAN = 0x00010203ul,
    O32_PDP_ENDIAN = 0x01000302ul
};

#define O32_HOST_ORDER (o32_host_order.value)
*****/

enum uartbus_status
{
	ub_stat_disconnected,		//0
	ub_stat_connecting,			//1
	ub_stat_idle,				//2
	ub_stat_sending,			//3
	ub_stat_sending_fairwait,	//4
	ub_stat_receiving			//5
};

enum uartbus_event
{
	ub_event_nothing,

	ub_event_receive_start,
	ub_event_receive_end,
};

enum uartbus_cfg
{
	/**
	 * After sending a packet, connection stays in ub_stat_sending_fairwait
	 * state to avoid a talkative device to lock the bus for too long time.
	 * */
	ub_cfg_fairwait_after_send_low	= 1,
	ub_cfg_fairwait_after_send_high = 2,
	
	//TODO invalidated: use or not the external read but we must get the sent values
	//back anyway
	ub_cfg_read_with_interrupt		= 4,
	//TODO invalidated: we handle the collision anyway
};

struct uartbus
{
	/**
	 * receive one byte (0-255) on error: any negative value or higher than 255
	 * */
	int16_t (*do_receive_byte)(struct uartbus* bus);


	/**
	 * function called when a byte received from serial line.
	 * */
	void (*serial_byte_received)
		(struct uartbus* bus, uint8_t data_byte);

	void (*serial_event)
		(struct uartbus* bus, enum uartbus_event event);

	uint8_t (*do_send_byte)
		(struct uartbus* bus, uint8_t);

	uint32_t (*currentUsec)
		();

	int8_t (*rand)
		();

	void* user_data;
	
	uint8_t* to_send;
	
	volatile uint16_t to_send_size;

	volatile enum uartbus_status status;

	//time to transmit 1 byte in microsec, 11 byte reqires 11 bit to transmit
	//in microsecounds
	// 1 start + 8 payload bits + 1 parity + 1 stop bit = 11 bit
	//
	//	baud	=> us
	// 	300		=> 36666
	//	9600	=> 1145
	//	57600	=> 190
	//	115200	=> 96
	//not used by the library
	uint16_t byte_time_us;
	
	uint8_t packet_timeout;
	
	uint8_t cfg;

	//time to the bus go idle (time to no activity)
	//uint32_t bus_idle_time;

	//last bus activity in millisec (overflow may occurs)
	uint32_t last_bus_activity;

	//in the state `sending` it contains the last transmitted byte
	//in case of collision (enter to fairwait) it constains the extra wait time.
	volatile int16_t wi;
};

uint16_t ub_calc_baud_cycle_time(uint32_t baud);

uint32_t ub_calc_timeout(uint32_t baud, uint8_t cycles);

//uint32_t ub_impl_get_current_usec();

uint8_t crc8(uint8_t* data, uint8_t length);


/**
 * Call this method from the outside to
 *
 * */
void ub_out_rec_byte(struct uartbus* bus, uint8_t data);

void ub_init(struct uartbus*);

void ub_init_infrastructure();

void ub_predict_transmission_start(struct uartbus* bus);

enum uartbus_status ub_get_bus_state(struct uartbus* bus);

int8_t ub_send_packet
	(struct uartbus* bus, uint8_t* addr, uint16_t size);

uint8_t ub_manage_connection
(
	struct uartbus* bus,
	uint8_t (*send_on_idle)(struct uartbus*, uint8_t** data, uint16_t* size)
);

void ub_init_baud
(
	struct uartbus* bus,
	uint16_t baud,
	uint8_t timeoutCycles
);

/*#define static_ub_calc_baud_cycle_time(baud) ((uint16_t) (11000000.0/baud))

#define static_ub_calc_timeout(baud, cycles) ((uint32_t) ((11000000.0/baud)*cycles))*/

#endif /* UB_H_ */
