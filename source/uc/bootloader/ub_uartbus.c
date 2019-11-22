
#define USART_BAUDRATE BAUD_RATE

#define BAUD USART_BAUDRATE
#define BAUD_PRESCALE (((F_CPU / (USART_BAUDRATE * 16UL))) - 1)

#include "ubh.h"

#define UCSZ1   2
#define UCSZ0   1

struct uartbus bus;

void try_dispatch_received_packet();

void ub_rec_byte(struct uartbus* a, uint8_t data_byte)
{
	if(received_ep == MAX_PACKET_SIZE)
	{
		//brake the package manually
		received_ep = 0;
	}
	else
	{
		received_data[received_ep++] = data_byte;
	}
}

#define UB_COLLISION_INT

static void ub_event(struct uartbus* a, enum uartbus_event event)
{
	if
	(
			ub_event_collision_start == event
		||
			ub_event_receive_start == event
		||
			ub_event_send_end == event
	)
	{
		received_ep = 0;
		received = false;
	}

	if(ub_event_receive_end == event)
	{
		received = 0 != received_ep;
	}
	
#ifdef UB_COLLISION_INT

	//listen for collision
	if
	(
			event == ub_event_receive_end
		||
			event == ub_event_send_end
		||
			event == ub_event_collision_end
	)
	{
		ubh_impl_enable_receive_detect_interrupt(true);
	}
	
	//disable listen for collision
	if
	(
		ub_event_receive_start == event
	||
		ub_event_send_start == event
	||
		ub_event_collision_start == event
	)
	{
		ubh_impl_enable_receive_detect_interrupt(false);
	}
#endif
}

uint8_t rando()
{
	return (rand() %256);
}

void init_bus()
{
	ub_init_infrastructure();
	
	received_ep = 0;

	bus.rand = (uint8_t (*)(struct uartbus*)) rando;
	bus.current_usec = (uint32_t (*)(struct uartbus* bus)) micros;
	bus.serial_byte_received = ub_rec_byte;
	bus.serial_event = ub_event;
	ub_init_baud(&bus, BAUD_RATE, 2);
	bus.do_send_byte = ubh_impl_do_send_byte;
	bus.cfg = 0
//		|	ub_cfg_fairwait_after_send_high
		|	ub_cfg_fairwait_after_send_low
		|	ub_cfg_read_with_interrupt
		|	ub_cfg_skip_collision_data
	;
	ub_init(&bus);
}

uint8_t send_on_idle(struct uartbus* bus, uint8_t** data, uint16_t* size)
{
	if(send_size != 0)
	{
		*data = send_data;
		*size = send_size;
		send_size = 0;
		return 0;
	}
	return 1;
}

void ubh_manage_bus()
{
	ub_manage_connection(&bus, send_on_idle);
	try_dispatch_received_packet();
}
