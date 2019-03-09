
#include <ub_arduino.h>

#ifdef ARDUINO

static void arduino_ub_rec_byte(struct uartbus* bus, uint8_t data_byte)
{
	struct uartbus_arduino* a = (struct uartbus_arduino*) bus->user_data;

	if(a->receive_ep == a->receive_size)
	{
		//brake the packet manually
		a->receive_ep = 0;
	}
	else
	{
		a->receive_data[a->receive_ep++] = data_byte;
	}
}

static void arduino_ub_event(struct uartbus* bus, enum uartbus_event event)
{
	struct uartbus_arduino* a = (struct uartbus_arduino*) bus->user_data;
	if(ub_event_receive_end == event)
	{
		a->packet_received(a, a->receive_data, a->receive_ep);
		a->receive_ep = 0;
	}
}

static uint8_t arduino_ub_do_send_byte(struct uartbus* bus, uint8_t val)
{
	struct uartbus_arduino* a = (struct uartbus_arduino*) bus->user_data;
	a->stream->write(val);
	a->stream->flush();
	return 0;
}

static int16_t arduino_ub_receive_byte(struct uartbus* bus)
{
	struct uartbus_arduino* a = (struct uartbus_arduino*) bus->user_data;
	//while(0 == a->stream->available());
	return a->stream->read();
}

static uint8_t arduino_ub_send_on_idle(struct uartbus* bus, uint8_t** data, uint16_t* size)
{
	struct uartbus_arduino* a = (struct uartbus_arduino*) bus->user_data;
	if(a->send_size != 0)
	{
		*data = a->send_data;
		*size = a->send_size;
		a->send_size = 0;
		return 0;
	}
	return 1;
}

void arduino_ub_init
(
	struct uartbus_arduino* init,
	Stream* stream,
	uint32_t baudRate,
	uint8_t maxPacketSize,
	void (*onPacketReceived)(struct uartbus_arduino* bus, uint8_t* data, uint16_t size),
	void* user_data
)
{
	uint8_t receive_ep;
	uint8_t* send_data;
	uint8_t send_size;

	init->receive_size = maxPacketSize;
	init->receive_data =  (uint8_t*) malloc(maxPacketSize);
	init->stream = stream;
	init->send_size = 0;
	init->packet_received = onPacketReceived;
	init->user_data = user_data;

	struct uartbus* bus = &init->bus;
	bus->user_data = init;
	
	bus->serial_byte_received = arduino_ub_rec_byte;
	bus->serial_event = arduino_ub_event;
	bus->byte_time_us = ub_calc_baud_cycle_time(baudRate);
	bus->bus_idle_time = ub_calc_timeout(baudRate, 2);
	bus->do_send_byte = arduino_ub_do_send_byte;
	bus->do_receive_byte = arduino_ub_receive_byte;
	bus->cfg = 0
//		|	ub_cfg_fairwait_after_send_low
		|	ub_cfg_fairwait_after_send_high
	;
	ub_init(bus);
}


bool arduino_ub_send_packet(struct uartbus_arduino* bus, uint8_t* data, uint8_t size)
{
	if(bus->send_size != 0)
	{
		return false;
	}
	bus->send_data = data;
	bus->send_size = size;
	return true;
}

void arduino_ub_manage_bus(struct uartbus_arduino* bus)
{
	ub_manage_connection(&bus->bus, arduino_ub_send_on_idle);
}

#endif
