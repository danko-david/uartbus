
//TODO if you miss this file call "./scripts/get_c_deps.sh"
#include "../../../WD/toe/src/c/utils.c"

#include "lazyctest.h"

#include "ub.h"
#include "rpc.h"

#include "../bus/lib/common/ub.cpp"

enum testbus_event
{
	ubt_after_event,				//0
	ubt_uart_after_send_byte,		//1
	ubt_uart_after_receive_byte,	//2
	ubt_rand_before_read,			//3
	ubt_current_usec_before_read,	//4
};

struct testbus
{
	struct uartbus bus;
	uint32_t time;
	uint8_t random;

	int16_t uart_next_receive;
	int16_t uart_to_send;

	int8_t last_event;

	void (*on_event)(struct testbus*, enum testbus_event);
	void (*post_event_hook)(struct testbus*, enum testbus_event);
	array event_backlog;
};

struct ubt_state
{
	enum testbus_event event;
	struct testbus state;
};

struct ubt_state* ubt_get_state_index(struct testbus* bus, size_t index)
{
	return (struct ubt_state*) bus->event_backlog->arr[index];
}

void ubt_dump_events(struct testbus* bus)
{
	size_t len = array_size(bus->event_backlog);

	for(size_t i=0;i<len;++i)
	{
		printf
		(
			"bus (%p) event backlog %d. event: %d\n",
			bus,
			i,
			ubt_get_state_index(bus, i)->event
		);
	}
}

void ubt_on_event(struct testbus* bus, enum testbus_event event)
{
	struct ubt_state* add = malloc(sizeof(struct ubt_state));
	add->event = event;
	memcpy(&add->state, bus, sizeof(struct testbus));
	array_add_element(bus->event_backlog, add);
	if(NULL != bus->post_event_hook)
	{
		bus->post_event_hook(bus, event);
	}
}

void ubt_clear_event_backlog(struct testbus* bus)
{
	size_t len = array_size(bus->event_backlog);

	for(size_t i=0;i<len;++i)
	{
		free(bus->event_backlog->arr[i]);
		bus->event_backlog->arr[i] = NULL;
	}
}

void ubt_test_event(struct uartbus* bus, enum testbus_event event)
{
	struct testbus* tb = (struct testbus*) bus;
	if(NULL != tb->on_event)
	{
		tb->on_event(tb, event);
	}
}

uint8_t ubt_rand(struct uartbus* bus)
{
	ubt_test_event(bus, ubt_rand_before_read);
	return ((struct testbus*)bus)->random;
}

uint32_t ubt_current_usec(struct uartbus* bus)
{
	ubt_test_event(bus, ubt_current_usec_before_read);
	return ((struct testbus*)bus)->time;
}

uint8_t ubt_send_byte(struct uartbus* bus, uint8_t val)
{
	((struct testbus*)bus)->uart_to_send = val;
	ubt_test_event(bus, ubt_uart_after_send_byte);
	return 0;
}

void ubt_event(struct uartbus* bus, enum uartbus_event event)
{
	((struct testbus*)bus)->last_event = event;
	ubt_test_event(bus, ubt_after_event);
}

void ubt_serial_byte_received(struct uartbus* bus, uint8_t data_byte)
{
	((struct testbus*)bus)->uart_next_receive = data_byte;
	ubt_test_event(bus, ubt_uart_after_receive_byte);
}

void ubt_step_byte_time(struct testbus* bus)
{
	bus->time += bus->bus.byte_time_us;
}

void ubt_step_packet_timeout_time(struct testbus* bus)
{
	bus->time += bus->bus.byte_time_us * bus->bus.packet_timeout;
}

void ubt_manage_connection(struct testbus* bus)
{
	ub_manage_connection(&bus->bus, NULL);
}

void ubt_go_idle(struct testbus* bus)
{
	for(int i=0;i < 100 && ub_stat_idle != bus->bus.status;++i)
	{
		ub_out_update_state(&bus->bus);
		ubt_step_byte_time(bus);
	}

	if(ub_stat_idle != bus->bus.status)
	{
		TEST_ASSERT_EQUAL(bus->bus.status, "Bus won't go idle");
	}
}

struct testbus* new_bus(uint32_t baud, uint8_t timeout)
{
	struct testbus* ret = malloc(sizeof(struct testbus));

	memset(ret, 0, sizeof(struct testbus));

	ret->event_backlog = array_create();
	ret->on_event = ubt_on_event;

	struct uartbus* bus = &ret->bus;

	bus->rand = ubt_rand;
	bus->current_usec = ubt_current_usec;
	bus->serial_byte_received = ubt_serial_byte_received;
	bus->serial_event = ubt_event;
	ub_init_baud(bus, baud, timeout);
	bus->do_send_byte = ubt_send_byte;
	bus->cfg = 0
		|	ub_cfg_fairwait_after_send_low
		|	ub_cfg_read_with_interrupt
		|	ub_cfg_skip_collision_data
	;
	ub_init(bus);

	return bus;
}

struct testbus* new_testbus()
{
	return new_bus(115200, 2);
}

void test_ub_init_baud_115200_times(void)
{
	struct testbus* bus = new_bus(115200, 3);
	//byte time should be 86 usec.
	TEST_ASSERT_EQUAL(86, bus->bus.byte_time_us);
	TEST_ASSERT_EQUAL(3, bus->bus.packet_timeout);
}

void test_ub_connecting_go_idle()
{
	struct testbus* bus = new_testbus();
	for(int i=0;i<bus->bus.packet_timeout;++i)
	{
		TEST_ASSERT_EQUAL(ub_stat_connecting, bus->bus.status);
		ubt_step_byte_time(bus);
		ubt_manage_connection(bus);
	}
	TEST_ASSERT_EQUAL(ub_stat_idle, bus->bus.status);
}

uint8_t SEND_BUFFER[250];
uint8_t send_size;

uint8_t ubt_loopback_notify(struct uartbus* bus, uint8_t val)
{
	((struct testbus*)bus)->uart_to_send = val;
	ubt_test_event(bus, ubt_uart_after_send_byte);
	ubt_step_byte_time(bus);
	ub_out_rec_byte(bus, val);
	return 0;
}

void ubt_set_successfull_loopback(struct testbus* bus)
{
	bus->bus.do_send_byte = ubt_loopback_notify;
}

/************************** send_on_idle_immedatly ****************************/

uint8_t ub_send_on_idle_immedatly
(
	struct uartbus* bus,
	uint8_t** data,
	uint16_t* size
)
{
	SEND_BUFFER[0] = 10;
	*data = SEND_BUFFER;
	*size = send_size;
	return 0;
}

void test_ub_send_on_idle_immedatly()
{
	struct testbus* bus = new_testbus();
	ubt_go_idle(bus);
	ubt_set_successfull_loopback(&bus->bus);

	send_size = 1;
	TEST_ASSERT_EQUAL(0, ub_manage_connection(&bus->bus, ub_send_on_idle_immedatly));
	TEST_ASSERT_EQUAL(10, bus->uart_to_send);
}


void test_ub_receive_cant_send()
{
	struct testbus* bus = new_testbus();

	send_size = 1;

	//go idle
	ubt_go_idle(bus);
	ubt_set_successfull_loopback(&bus->bus);

	//no event received yet and we are in idle
	TEST_ASSERT_EQUAL(ub_event_nothing, bus->last_event);
	TEST_ASSERT_EQUAL(ub_stat_idle, bus->bus.status);

	//we receive one byte
	ub_out_rec_byte(&bus->bus, 20);

	//we should notified about the new receive start.
	TEST_ASSERT_EQUAL(ub_event_receive_start, bus->last_event);
	bus->last_event = 0;//clear last event
	TEST_ASSERT_EQUAL(ub_stat_receiving, bus->bus.status);

	//in the receive state, we can't start transmission
	TEST_ASSERT_EQUAL(2, ub_manage_connection(&bus->bus, ub_send_on_idle_immedatly));
	//and it must not cause any event or other broken action
	TEST_ASSERT_EQUAL(ub_stat_receiving, bus->bus.status);
	TEST_ASSERT_EQUAL(0, bus->bus.wi);
	TEST_ASSERT_EQUAL(ub_event_nothing, bus->last_event);

	//after one byte time we get another byte and we are still in receive.
	//we don't get any other event under the transmission
	ubt_step_byte_time(bus);
	ub_out_rec_byte(&bus->bus, 15);
	TEST_ASSERT_EQUAL(ub_event_nothing, bus->last_event);
	TEST_ASSERT_EQUAL(ub_stat_receiving, bus->bus.status);

	//still can't start transmission
	TEST_ASSERT_EQUAL(2, ub_manage_connection(&bus->bus, ub_send_on_idle_immedatly));
	//and still it must not cause any event or other broken action
	TEST_ASSERT_EQUAL(ub_stat_receiving, bus->bus.status);
	TEST_ASSERT_EQUAL(0, bus->bus.wi);
	TEST_ASSERT_EQUAL(ub_event_nothing, bus->last_event);

	//we exceed the timeslice, therefore the packet frame is ended.
	ubt_step_packet_timeout_time(bus);
	ub_out_update_state(&bus->bus);

	//and we notified about the packet end.
	TEST_ASSERT_EQUAL(ub_event_receive_end, bus->last_event);
	bus->last_event = 0;

	//we enter the fairwait state
	TEST_ASSERT_EQUAL(ub_stat_fairwait, bus->bus.status);
	TEST_ASSERT_EQUAL(ub_get_receive_time_padding(&bus->bus), bus->bus.wi);

	//and still can't start transmission
	TEST_ASSERT_EQUAL(2, ub_manage_connection(&bus->bus, ub_send_on_idle_immedatly));
	//and still it must not cause any event or other broken action
	TEST_ASSERT_EQUAL(ub_stat_fairwait, bus->bus.status);
	TEST_ASSERT_EQUAL(ub_get_receive_time_padding(&bus->bus), bus->bus.wi);
	TEST_ASSERT_EQUAL(ub_event_nothing, bus->last_event);

	//but after more byte-time we should go idle
	int n = ub_get_receive_time_padding(&bus->bus);
	for(int i=0;i<n;++i)
	{
		ubt_step_byte_time(bus);
	}
	ub_out_update_state(bus);
	TEST_ASSERT_EQUAL(ub_stat_idle, bus->bus.status);
	TEST_ASSERT_EQUAL(ub_event_nothing, bus->last_event);

	//and now we may initiate transaction
	TEST_ASSERT_EQUAL(0, ub_manage_connection(&bus->bus, ub_send_on_idle_immedatly));
}

void test_send_events()
{
	struct testbus* bus = new_testbus();
	send_size = 1;

	ubt_go_idle(bus);
	ubt_set_successfull_loopback(&bus->bus);

	ubt_clear_event_backlog(bus);
	TEST_ASSERT_EQUAL(ub_event_nothing, bus->last_event);
	TEST_ASSERT_EQUAL(0, ub_manage_connection(&bus->bus, ub_send_on_idle_immedatly));

	ubt_go_idle(bus);

	{
		bool send_start = false;
		bool send_end = false;
		size_t len = array_size(bus->event_backlog);
		for(size_t i =0;i<len;++i)
		{
			struct ubt_state* state = ubt_get_state_index(bus, i);
			if(ubt_after_event == state->event)
			{
				if(ub_event_send_start == state->state.last_event)
				{
					TEST_ASSERT_FALSE(send_end);
					send_start = true;
				}
				else if(ub_event_send_end == state->state.last_event)
				{
					TEST_ASSERT_FALSE(send_end);
					TEST_ASSERT_TRUE(send_start);
					send_end = true;
				}
				else
				{
					char buff[50];
					sprintf(buff, "Unexcepted event %d beneath sending.", state->state.last_event);
					TEST_ASSERT_STR_EQUAL("", buff);
				}
			}
		}
	}

	TEST_ASSERT_NOT_EQUAL(ubt_uart_after_send_byte, ubt_get_state_index(bus, 0)->event);

	TEST_ASSERT_EQUAL(ub_event_send_end, bus->last_event);
}

void test_receive()
{
	struct testbus* bus = new_testbus();
	ubt_go_idle(bus);
	ubt_set_successfull_loopback(&bus->bus);

	ubt_clear_event_backlog(bus);
	TEST_ASSERT_EQUAL(ub_event_nothing, bus->last_event);

	//send 10:15:20:25:30 package
	for(int i=10;i<=30;i+=5)
	{
		ub_out_rec_byte(&bus->bus, i);
		ubt_step_byte_time(&bus->bus);
	}

	ubt_go_idle(bus);

	size_t len = array_size(bus->event_backlog);
	TEST_ASSERT_TRUE(len >= 7);
	uint8_t val = 10;
	bool start = false;
	bool end = false;

	for(size_t i=0;i<len;++i)
	{
		struct ubt_state* event = ubt_get_state_index(bus, i);
		enum testbus_event type = event->event;
		if(ubt_after_event == type)
		{
			if(ub_event_receive_start == event->state.last_event)
			{
				TEST_ASSERT_FALSE(start);
				TEST_ASSERT_FALSE(end);
				TEST_ASSERT_EQUAL(10, val);
				start = true;
			}
			else if(ub_event_receive_end == event->state.last_event)
			{
				TEST_ASSERT_TRUE(start);
				TEST_ASSERT_FALSE(end);
				end = true;
			}
		}
		else if(ubt_uart_after_receive_byte == type)
		{
			TEST_ASSERT_TRUE(start);
			TEST_ASSERT_FALSE(end);
			TEST_ASSERT_EQUAL(val, event->state.uart_next_receive);
			val += 5;
		}
	}

	TEST_ASSERT_EQUAL(35, val);
	TEST_ASSERT_TRUE(start);
	TEST_ASSERT_TRUE(end);
}

uint8_t ubt_fail_when;

uint8_t ubt_fail_loopback_when(struct uartbus* bus, uint8_t val)
{
	((struct testbus*)bus)->uart_to_send = val;
	if(val == ubt_fail_when)
	{
		val = ~val;
	}

	ubt_test_event(bus, ubt_uart_after_send_byte);
	ubt_step_byte_time(bus);
	ub_out_rec_byte(bus, val);
	return 0;
}

void ubt_funct_test_send_step(struct testbus* bus, enum testbus_event event)
{
	if(ubt_uart_after_send_byte == event)
	{
		ubt_step_byte_time(bus);
	}
}

void print_state(struct ubt_state* state)
{
	printf
	(
		"print_state: event: %d, last_event: %d, uart_send: %d, last_rec: %d\n",
		state->event,
		state->state.last_event,
		state->state.uart_to_send,
		state->state.uart_next_receive
	);
}

struct ubt_state* ubt_ignore_call_events(struct testbus* bus, size_t* index)
{
	size_t size = array_size(bus->event_backlog);
	while(*index < size)
	{
		struct ubt_state* event = ubt_get_state_index(bus, *index);
		enum testbus_event e = event->event;
		if
		(
			ubt_rand_before_read != e && ubt_current_usec_before_read != e
		)
		{
			return event;
		}
		++(*index);
	}

	return NULL;
}

void test_bad_send_retransmit()
{
	struct testbus* bus = new_testbus();
	ubt_go_idle(bus);

	ubt_clear_event_backlog(bus);
	TEST_ASSERT_EQUAL(ub_event_nothing, bus->last_event);

	SEND_BUFFER[0] = 10;
	SEND_BUFFER[1] = 15;
	SEND_BUFFER[2] = 20;
	SEND_BUFFER[3] = 25;
	SEND_BUFFER[4] = 30;

	send_size = 5;

	ubt_fail_when = 25;

	bus->bus.do_send_byte = ubt_fail_loopback_when;
//	bus->post_event_hook = ubt_funct_test_send_step;

	TEST_ASSERT_EQUAL(-2, ub_manage_connection(&bus->bus, ub_send_on_idle_immedatly));

	ubt_set_successfull_loopback(bus);

	ubt_go_idle(bus);

	TEST_ASSERT_EQUAL(0, ub_manage_connection(&bus->bus, ub_send_on_idle_immedatly));

	ubt_go_idle(bus);


	size_t len = array_size(bus->event_backlog);

	size_t index = 0;
	struct ubt_state* event;

	//event:start send
	TEST_ASSERT_NOT_NULL(event = ubt_ignore_call_events(bus, &index));
	TEST_ASSERT_EQUAL(ubt_after_event, event->event);
	TEST_ASSERT_EQUAL(ub_event_send_start, event->state.last_event);
	++index;

	//NOTICE: we don't get notified the bytes we sent (only the event when
	//collision occurred)

	//send byte: 10
	TEST_ASSERT_NOT_NULL(event = ubt_ignore_call_events(bus, &index));
	TEST_ASSERT_EQUAL(ubt_uart_after_send_byte, event->event);
	TEST_ASSERT_EQUAL(10, event->state.uart_to_send);
	++index;

	//send byte: 15
	TEST_ASSERT_NOT_NULL(event = ubt_ignore_call_events(bus, &index));
	TEST_ASSERT_EQUAL(ubt_uart_after_send_byte, event->event);
	TEST_ASSERT_EQUAL(15, event->state.uart_to_send);
	++index;

	//send byte: 20
	TEST_ASSERT_NOT_NULL(event = ubt_ignore_call_events(bus, &index));
	TEST_ASSERT_EQUAL(ubt_uart_after_send_byte, event->event);
	TEST_ASSERT_EQUAL(20, event->state.uart_to_send);
	++index;

	//send byte: 25
	TEST_ASSERT_NOT_NULL(event = ubt_ignore_call_events(bus, &index));
	TEST_ASSERT_EQUAL(ubt_uart_after_send_byte, event->event);
	TEST_ASSERT_EQUAL(25, event->state.uart_to_send);
	++index;

	//collision event
	TEST_ASSERT_NOT_NULL(event = ubt_ignore_call_events(bus, &index));
	TEST_ASSERT_EQUAL(ubt_after_event, event->event);
	TEST_ASSERT_EQUAL(ub_event_collision_start, event->state.last_event);
	++index;

	//event end because of collision
	TEST_ASSERT_NOT_NULL(event = ubt_ignore_call_events(bus, &index));
	TEST_ASSERT_EQUAL(ubt_after_event, event->event);
	TEST_ASSERT_EQUAL(ub_event_send_end, event->state.last_event);
	++index;

	//event:start send (retrying send)
	TEST_ASSERT_NOT_NULL(event = ubt_ignore_call_events(bus, &index));
	TEST_ASSERT_EQUAL(ubt_after_event, event->event);
	TEST_ASSERT_EQUAL(ub_event_collision_end, event->state.last_event);
	++index;

	//event:start send (retrying send)
	TEST_ASSERT_NOT_NULL(event = ubt_ignore_call_events(bus, &index));
	TEST_ASSERT_EQUAL(ubt_after_event, event->event);
	TEST_ASSERT_EQUAL(ub_event_send_start, event->state.last_event);
	++index;


	//send byte: 10
	TEST_ASSERT_NOT_NULL(event = ubt_ignore_call_events(bus, &index));
	TEST_ASSERT_EQUAL(ubt_uart_after_send_byte, event->event);
	TEST_ASSERT_EQUAL(10, event->state.uart_to_send);
	++index;

	//send byte: 15
	TEST_ASSERT_NOT_NULL(event = ubt_ignore_call_events(bus, &index));
	TEST_ASSERT_EQUAL(ubt_uart_after_send_byte, event->event);
	TEST_ASSERT_EQUAL(15, event->state.uart_to_send);
	++index;

	//send byte: 20
	TEST_ASSERT_NOT_NULL(event = ubt_ignore_call_events(bus, &index));
	TEST_ASSERT_EQUAL(ubt_uart_after_send_byte, event->event);
	TEST_ASSERT_EQUAL(20, event->state.uart_to_send);
	++index;

	//send byte: 25
	TEST_ASSERT_NOT_NULL(event = ubt_ignore_call_events(bus, &index));
	TEST_ASSERT_EQUAL(ubt_uart_after_send_byte, event->event);
	TEST_ASSERT_EQUAL(25, event->state.uart_to_send);
	++index;

	//send byte: 30
	TEST_ASSERT_NOT_NULL(event = ubt_ignore_call_events(bus, &index));
	TEST_ASSERT_EQUAL(ubt_uart_after_send_byte, event->event);
	TEST_ASSERT_EQUAL(30, event->state.uart_to_send);
	++index;

	//event:end (successfull send)
	TEST_ASSERT_NOT_NULL(event = ubt_ignore_call_events(bus, &index));
	TEST_ASSERT_EQUAL(ubt_after_event, event->event);
	TEST_ASSERT_EQUAL(ub_event_send_end, event->state.last_event);
	++index;
}

void test_concateration()
{
	struct testbus* bus = new_testbus();
	ubt_go_idle(bus);

	ubt_clear_event_backlog(bus);
	TEST_ASSERT_EQUAL(ub_event_nothing, bus->last_event);

	SEND_BUFFER[0] = 10;
	SEND_BUFFER[1] = 15;
	SEND_BUFFER[2] = 20;
	SEND_BUFFER[3] = 25;
	SEND_BUFFER[4] = 30;

	send_size = 5;

	ubt_set_successfull_loopback(bus);

	TEST_ASSERT_EQUAL(0, ub_manage_connection(&bus->bus, ub_send_on_idle_immedatly));

	//we still should be in "sending" state
	TEST_ASSERT_EQUAL(ub_stat_sending, bus->bus.status);

	//receive two bytes
	ub_out_rec_byte(&bus->bus, 60);
	ubt_step_byte_time(bus);
	ub_out_rec_byte(&bus->bus, 65);
	ubt_step_byte_time(bus);

	ubt_go_idle(bus);

	size_t len = array_size(bus->event_backlog);

	size_t index = 0;
	struct ubt_state* event;

	//event:start send
	TEST_ASSERT_NOT_NULL(event = ubt_ignore_call_events(bus, &index));
	TEST_ASSERT_EQUAL(ubt_after_event, event->event);
	TEST_ASSERT_EQUAL(ub_event_send_start, event->state.last_event);
	++index;

	//NOTICE: we don't get notified the bytes we sent (only the event when
	//collision occurred)

	//send byte: 10
	TEST_ASSERT_NOT_NULL(event = ubt_ignore_call_events(bus, &index));
	TEST_ASSERT_EQUAL(ubt_uart_after_send_byte, event->event);
	TEST_ASSERT_EQUAL(10, event->state.uart_to_send);
	++index;

	//send byte: 15
	TEST_ASSERT_NOT_NULL(event = ubt_ignore_call_events(bus, &index));
	TEST_ASSERT_EQUAL(ubt_uart_after_send_byte, event->event);
	TEST_ASSERT_EQUAL(15, event->state.uart_to_send);
	++index;

	//send byte: 20
	TEST_ASSERT_NOT_NULL(event = ubt_ignore_call_events(bus, &index));
	TEST_ASSERT_EQUAL(ubt_uart_after_send_byte, event->event);
	TEST_ASSERT_EQUAL(20, event->state.uart_to_send);
	++index;

	//send byte: 25
	TEST_ASSERT_NOT_NULL(event = ubt_ignore_call_events(bus, &index));
	TEST_ASSERT_EQUAL(ubt_uart_after_send_byte, event->event);
	TEST_ASSERT_EQUAL(25, event->state.uart_to_send);
	++index;

	//send byte: 30
	TEST_ASSERT_NOT_NULL(event = ubt_ignore_call_events(bus, &index));
	TEST_ASSERT_EQUAL(ubt_uart_after_send_byte, event->event);
	TEST_ASSERT_EQUAL(30, event->state.uart_to_send);
	++index;

	//collision event
	TEST_ASSERT_NOT_NULL(event = ubt_ignore_call_events(bus, &index));
	TEST_ASSERT_EQUAL(ubt_after_event, event->event);
	TEST_ASSERT_EQUAL(ub_event_collision_start, event->state.last_event);
	++index;

	//end of transmission
	TEST_ASSERT_NOT_NULL(event = ubt_ignore_call_events(bus, &index));
	TEST_ASSERT_EQUAL(ubt_after_event, event->event);
	TEST_ASSERT_EQUAL(ub_event_send_end, event->state.last_event);
	++index;

	TEST_ASSERT_NOT_NULL(event = ubt_ignore_call_events(bus, &index));
	TEST_ASSERT_EQUAL(ubt_after_event, event->event);
	TEST_ASSERT_EQUAL(ub_event_collision_end, event->state.last_event);
	++index;

	//packet should still be the last packet to send
	TEST_ASSERT_EQUAL(5, bus->bus.to_send_size);
}


void test_dont_give_up_retry__on_early_collision()
{
	struct testbus* bus = new_testbus();
	ubt_go_idle(bus);

	ubt_clear_event_backlog(bus);
	TEST_ASSERT_EQUAL(ub_event_nothing, bus->last_event);

	SEND_BUFFER[0] = 10;
	SEND_BUFFER[1] = 15;
	SEND_BUFFER[2] = 20;
	SEND_BUFFER[3] = 25;
	SEND_BUFFER[4] = 30;

	send_size = 5;

	ubt_set_successfull_loopback(bus);

	//well... maybe a max_retry feature gonna be good.
	for(int i=0;i < 20;++i)
	{
		TEST_ASSERT_EQUAL(0, ub_manage_connection(&bus->bus, ub_send_on_idle_immedatly));
		//we still should be in "sending" state
		TEST_ASSERT_EQUAL(ub_stat_sending, bus->bus.status);

		ub_out_rec_byte(&bus->bus, 99);

		TEST_ASSERT_EQUAL(ub_stat_collision, bus->bus.status);

		TEST_ASSERT_EQUAL(5, bus->bus.to_send_size);

		ubt_go_idle(bus);
	}

	TEST_ASSERT_EQUAL(0, ub_manage_connection(&bus->bus, ub_send_on_idle_immedatly));
	ubt_go_idle(bus);

	TEST_ASSERT_EQUAL(0, bus->bus.to_send_size);

	TEST_ASSERT_EQUAL(ub_stat_idle, bus->bus.status);
}


struct test_job* LCT_CURRENT_TEST_JOB;

int main(int argc, char **argv)
{
	test_bad_send_retransmit();
}
