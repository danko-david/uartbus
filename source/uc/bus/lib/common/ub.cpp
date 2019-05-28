/**
 * UartBus is an extension for uart, makes possible to send packet between
 * multiple uC using only 1 dataline.
 *
 * TODO:
 *
 *
 *
 * Motivation: many microcontroller, embedded system, even desktop machines
 * has UART port, what makes possible to establish a point-to-point
 * communication between these devices.
 *
 * But the real reason is, I need a cheap, widely usable bus system.
 * As main goal: connect a bunch of microcontrollers at home, where are long
 * distances between units and connect this network to a small computer
 * (eg.: Raspberry pi), secondly, use few wires as possible.
 *
 * How to achieve that? Look up for a communication interface that available
 * in most of the chips and systems, study the best technics of each, then hack
 * them together:
 * uartbus made from:
 * 	- I2C: it's awesome that it's connects multiple uC together, but it's
 *	requires 2 line to communicate (data and clock)
 * (4 at least: GND, VCC, DATA, CLK)
 * 	And used for short distance.
 *
 * Uart: it's uses 2 wires for each direction. But i'ts designed to point-to
 * point communication
 *
 * */


#include "ub.h"

static void inline ub_update_last_activity_now(struct uartbus* bus)
{
	bus->last_bus_activity = bus->current_usec(bus);
}

uint8_t get_fairwait_conf_cycles(struct uartbus* bus)
{
	uint8_t ret = 0;
	if(bus->cfg & ub_cfg_fairwait_after_send_low)
	{
		ret = 0x1;
	}

	if(bus->cfg & ub_cfg_fairwait_after_send_high)
	{
		ret |= 0x2;
	}
	return 1+ret;
}

uint8_t ub_get_receive_time_padding(struct uartbus* bus)
{
	return get_fairwait_conf_cycles(bus);
}

static uint8_t get_packet_timeout_cycles(struct uartbus* bus)
{
	return bus->packet_timeout;
}

__attribute__((noinline)) static bool is_slice_exceed
(
	struct uartbus* bus,
	bool update
)
{
	uint32_t last = bus->last_bus_activity;
	uint32_t now = bus->current_usec(bus);

	if(update)
	{
		bus->last_bus_activity = now;
	}

	uint8_t mul = bus->packet_timeout;

	enum uartbus_status status = bus->status;

	/*if
	(
		ub_stat_receiving == status
	||
		ub_stat_sending == status
	||
		ub_stat_collision == status
	)
	{
		mul = get_packet_timeout_cycles(bus);
	}
	else */if(ub_stat_fairwait == status)
	{
		mul = bus->wi;
	}

	//check for overflow and calculate back
	uint32_t timeout = ((uint32_t)bus->byte_time_us)*mul;
	if(now < last)
	{
		return now + (UINT32_MAX - last) >= timeout;
	}
	return now-last >= timeout;
}

//https://www.ccsinfo.com/forum/viewtopic.php?t=37015
//x^8+x^5+x^4+x^0 //Dallas-Maxim CRC8
uint8_t crc8(uint8_t* data, uint8_t length)
{
	uint8_t crc = 0;
	uint8_t v;
	uint8_t i;
	for(i=0;i<length;++i)
	{
		v = (data[i] ^ crc) & 0xff; 
		crc = 0;
		if(v & 1)
			crc ^= 0x5e; 
		if(v & 2)
			crc ^= 0xbc; 
		if(v & 4)
			crc ^= 0x61; 
		if(v & 8)
			crc ^= 0xc2; 
		if(v & 0x10)
			crc ^= 0x9d; 
		if(v & 0x20)
			crc ^= 0x23; 
		if(v & 0x40)
			crc ^= 0x46; 
		if(v & 0x80)
			crc ^= 0x8c;
	}

	return crc;
}

__attribute__((noinline)) void ub_out_update_state(struct uartbus* bus)
{
	enum uartbus_status status = bus->status;
	if
	(
		ub_stat_idle == status
	/*||
		bus->current_usec(bus) - bus->last_bus_activity < bus->byte_time_us *2*/
	)
	{
		return;
	}

	bool exceed = is_slice_exceed(bus, false);

	if(exceed)
	{
		//if we transmitted previously and cycles time exceed to go idle
		//we really go idle

		if(ub_stat_fairwait == status)
		{
			bus->status = ub_stat_idle;
			ub_update_last_activity_now(bus);

			//else we still in send (it's necessary to keep the bus busy for a
			//certain time to others can acquire the transmission line
			//and we can't send before this time exceeds) [fairwait feature]
		}
		else if(ub_stat_receiving == status)
		{
			//if there's no more byte to receive, of course we will not
			//notified by the ub_out_rec_byte function, so we use this function
			//to notify the client
			//bus->status = ub_stat_idle;

			//insert a short one byte wait to prevent packet time frame
			//concateration
			bus->wi = get_fairwait_conf_cycles(bus) + bus->rand(bus)%3;
			bus->status = ub_stat_fairwait;
			
			ub_update_last_activity_now(bus);
			bus->serial_event(bus, ub_event_receive_end);
		}
		else if(ub_stat_sending == status)
		{
			//a successfull send, so we might send the next packet
			bus->to_send_size = 0;
			bus->wi = get_fairwait_conf_cycles(bus);
			bus->status = ub_stat_fairwait;
			
			ub_update_last_activity_now(bus);
			bus->serial_event(bus, ub_event_send_end);
		}
		else if(ub_stat_collision == status)
		{
			bus->wi = get_fairwait_conf_cycles(bus);
			bus->status = ub_stat_fairwait;
			ub_update_last_activity_now(bus);
			bus->serial_event(bus, ub_event_collision_end);
		}
		else //if(ub_stat_connecting == status)
		{
			bus->status = ub_stat_idle;
		}
	}
}

/**
 * return true on collision
 */
static bool ub_check_and_handle_collision(struct uartbus* bus, uint16_t data)
{
	//handle collision
	//maybe we can insert random wait by modifing get_packet_timeout_cycles
	//to consider alternative wait cycle.
	if(data > 255 || (bus->wi != data))
	{
		enum uartbus_status prev = bus->status;
		bus->status = ub_stat_collision;
		
		ub_update_last_activity_now(bus);

		if(ub_stat_collision != prev)
		{
			bus->serial_event(bus, ub_event_collision_start);
		}

		if(ub_stat_sending == prev)
		{
			bus->serial_event(bus, ub_event_send_end);
		}

		if(!(bus->cfg & ub_cfg_skip_collision_data))
		{
			bus->serial_byte_received(bus, data);
		}

		return true;
	}
	else
	{
		bus->wi = ~0;
	}
	
	return false;
}

/**
 * Call this method from the outside to dispatch received byte (if it's come
 * from other device)
 * */
__attribute__((noinline)) void ub_out_rec_byte(struct uartbus* bus, uint16_t data)
{
	//ub_out_update_state(bus);
	enum uartbus_status status = bus->status;
	
	//we receive the data back that we sending now
	if(ub_stat_sending == status || ub_stat_collision == status)
	{
		if(bus->cfg & ub_cfg_read_with_interrupt)
		{
			//we return anyway but maybe we handle a collision
			ub_check_and_handle_collision(bus, data);
		}
		ub_update_last_activity_now(bus);
		return;
	}
	else if(ub_stat_receiving != status)
	{
		ub_predict_transmission_start(bus);
	}
	else
	{
		ub_update_last_activity_now(bus);
	}

	bus->serial_byte_received(bus, data);
}

/*
 * ret:
 * positive value: user defined error returned from do_send_byte, if value is
 * 	negative, returned with abs(retval)
 * 	0	success
 * 	-1	bus is busy
 *	-2	reread_mismatch
 * */
int8_t ub_send_packet(struct uartbus* bus, uint8_t* addr, uint16_t size)
{
	if(bus->status != ub_stat_idle)
	{
		return 1;
	}

	bus->status = ub_stat_sending;
	ub_update_last_activity_now(bus);
	bus->serial_event(bus, ub_event_send_start);
	bus->wi = ~0;

	uint8_t (*send)(struct uartbus* bus, uint8_t) = bus->do_send_byte;
	int16_t (*rec)(struct uartbus* bus) = bus->do_receive_byte;

	uint8_t stat;
	bool interrupt = bus->cfg & ub_cfg_read_with_interrupt;
	for(uint16_t i=0;i<size;++i)
	{
		if(interrupt)
		{
			while(ub_stat_sending == bus->status && bus->wi != ~0)
			{
				ub_out_update_state(bus);
			}

			if(ub_stat_sending != bus->status)
			{
				return -2;
			}
		}

		uint8_t val = addr[i];
		bus->wi = val;
		stat = send(bus, val);

		if(0 != stat)
		{
			if(stat < 0)
			{
				return -stat;
			}
			return stat;
		}

		//update the last acitivity time
		//ub_update_last_activity_now(bus);

		if(!interrupt)
		{
			//read value back in blocking mode if configured.
			//TODO test this case with the arduino bindings which uses blocking
			//read
			bus->do_receive_byte(bus);
		}
	}
	
	if(interrupt)
	{
		while(ub_stat_sending == bus->status && bus->wi != ~0)
		{
			ub_out_update_state(bus);
		}
		
		if(ub_stat_sending != bus->status)
		{
			return -2;
		}
	}

	//fairwait;
	//REJECTED IDEA: enter fairwait now, this prevent over-waiting on the bus.
	//enter to fairwait and set wait, if we keep in sending state we might miss
	//the start of the packet we gonna receive from other device
	
	//we can even collide with other transmission if other device start sending
	//beneath the send state. In this case we - using the previous
	//implementation - think we succeed the sending, BUT actually the other
	//device broke THIS transmission. This results this device don't gonna
	//retransmit and the package are broken => packet lost.
	
	//bus->status = ub_stat_sending_fairwait;
	//bus->wi = get_packet_timeout_cycles(bus)+get_fairwait_conf_cycles(bus)+1;

	//if we get another byte while still in "sedning" state this cause interruption
	//bus->wi = 256;

	//bus->serial_event(bus, ub_event _ send_end);
	return 0;
}

void ub_init(struct uartbus* bus)
{
	//we are "connecting" (getting synchronized to the bus) and we go busy
	//'till we ensure, there's no transmission on the bus or we capture one.
	bus->status = ub_stat_connecting;
	bus->to_send = NULL;
	bus->to_send_size = 0;
	ub_update_last_activity_now(bus);
}

enum uartbus_status ub_get_bus_state(struct uartbus* bus)
{
	return bus->status;
}

uint16_t ub_calc_baud_cycle_time(uint32_t baud)
{
	return 10000000/baud;
}

uint32_t ub_calc_timeout(uint32_t baud, uint8_t cycles)
{
	return (10000000/baud)*cycles;
}

void ub_init_baud
(
	struct uartbus* bus,
	uint32_t baud,
	uint8_t timeoutCycle
)
{
	bus->byte_time_us = ub_calc_baud_cycle_time(baud);
	bus->packet_timeout = timeoutCycle;
}

void ub_try_receive(struct uartbus* bus)
{
	int16_t re = 0;
	while(true)
	{
		re = bus->do_receive_byte(bus);
		if(re < 0 || re > 255)
		{
			break;
		}
		else
		{
			ub_out_rec_byte(bus, (uint8_t) re);
		}
	}
}

__attribute__((noinline)) void ub_predict_transmission_start(struct uartbus* bus)
{
	if(ub_stat_idle == bus->status || ub_stat_fairwait == bus->status)
	{
		bus->status = ub_stat_receiving;
		ub_update_last_activity_now(bus);
		//if we idle we notifies the start only
		bus->serial_event(bus, ub_event_receive_start);
	}
}

__attribute__((noinline)) bool ub_prewait(struct uartbus* bus, uint8_t cycles)
{
	if(ub_stat_idle == bus->status || ub_stat_fairwait == bus->status)
	{
		bus->wi = cycles;
		bus->status = ub_stat_fairwait;
	}
}

/**
 * 2 - bus is not idle
 * 1 - noting to send (and pick up)
 * 0 - successful send
 * -1 - bus is busy when try to send
 * -2 - possible collision or noise when sending
 * */
__attribute__((noinline)) int8_t ub_manage_connection
(
	struct uartbus* bus,
	uint8_t (*send_on_idle)(struct uartbus*, uint8_t** data, uint16_t* size)
)
{
	//TODO test on a uc: receive is really timed out and this returns here?
	if(!(bus->cfg & ub_cfg_read_with_interrupt))
	{
		ub_try_receive(bus);
	}

	ub_out_update_state(bus);

	if(ub_stat_idle == bus->status)
	{
		if(0 == bus->to_send_size)
		{
			if(NULL != send_on_idle)
			{
				if(0 != send_on_idle(bus, &bus->to_send, (uint16_t*)&bus->to_send_size))
				{
					return 1;
				}
			}
		}

		if(0 != bus->to_send_size)
		{
			return ub_send_packet(bus, bus->to_send, bus->to_send_size);
		}
	}

	return 2;
}

