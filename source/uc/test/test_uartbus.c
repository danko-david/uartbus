
//TODO if you miss this file call "./scripts/get_c_deps.sh"
#include "../../../WD/toe/src/c/utils.c"

#include "lazyctest.h"

#include "ub.h"
#include "rpc.h"

#include "../bus/lib/common/ub.cpp"

enum testbus_event
{
	ubt_after_event,
	ubt_uart_after_send_byte,
	ubt_uart_after_receive_byte,
	ubt_rand_before_read,
	ubt_current_usec_before_read,
};

struct testbus
{
	struct uartbus bus;
	uint32_t time;
	uint8_t random;

	int16_t uart_next_receive;
	int16_t uart_to_send;

	int16_t sw_next_receive;
	int16_t sw_to_send;

	int8_t last_event;

	void (*on_event)(struct testbus*, enum testbus_event);
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
	((struct testbus*)bus)->sw_next_receive = data_byte;
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
	ubt_step_packet_timeout_time(bus);
	ubt_manage_connection(bus);
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
		|	ub_cfg_fairwait_after_send_high
		|	ub_cfg_read_with_interrupt
	;
	ub_init(bus);

	return bus;
}

struct testbus* new_testbus()
{
	return new_bus(115200, 3);
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

uint8_t ubt_loopback_notify(struct uartbus* bus, uint8_t val)
{
	ub_out_rec_byte(bus, val);
	((struct testbus*)bus)->uart_to_send = val;
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
	*size = 1;
	return 0;
}

void test_ub_send_on_idle_immedatly()
{
	struct testbus* bus = new_testbus();
	ubt_go_idle(bus);
	ubt_set_successfull_loopback(&bus->bus);

	TEST_ASSERT_EQUAL(0, ub_manage_connection(&bus->bus, ub_send_on_idle_immedatly));
	TEST_ASSERT_EQUAL(10, bus->uart_to_send);
}


void test_ub_receive_cant_send()
{
	struct testbus* bus = new_testbus();

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
	ub_out_update_state(bus);
	//and we notified about the packet end.
	TEST_ASSERT_EQUAL(ub_event_receive_end, bus->last_event);
	bus->last_event = 0;

	//we enter the one byte-time fairwait state
	TEST_ASSERT_EQUAL(ub_stat_sending_fairwait, bus->bus.status);
	TEST_ASSERT_EQUAL(1, bus->bus.wi);

	//and still can't start transmission
	TEST_ASSERT_EQUAL(2, ub_manage_connection(&bus->bus, ub_send_on_idle_immedatly));
	//and still it must not cause any event or other broken action
	TEST_ASSERT_EQUAL(ub_stat_sending_fairwait, bus->bus.status);
	TEST_ASSERT_EQUAL(1, bus->bus.wi);
	TEST_ASSERT_EQUAL(ub_event_nothing, bus->last_event);

	//but after one more byte-time we should go idle
	ubt_step_byte_time(bus);
	ub_out_update_state(bus);
	TEST_ASSERT_EQUAL(ub_stat_idle, bus->bus.status);
	TEST_ASSERT_EQUAL(ub_event_nothing, bus->last_event);

	//and now we may initiate transaction
	TEST_ASSERT_EQUAL(0, ub_manage_connection(&bus->bus, ub_send_on_idle_immedatly));
}

void test_send_events()
{
	struct testbus* bus = new_testbus();

	ubt_go_idle(bus);
	ubt_set_successfull_loopback(&bus->bus);

	ubt_clear_event_backlog(bus);
	TEST_ASSERT_EQUAL(ub_event_nothing, bus->last_event);
	TEST_ASSERT_EQUAL(0, ub_manage_connection(&bus->bus, ub_send_on_idle_immedatly));

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

