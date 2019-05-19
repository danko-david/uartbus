
#include "lazyctest.h"

#include "ub.h"
#include "rpc.h"

#include "../bus/lib/common/ub.cpp"

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
};

static uint8_t ubt_rand(struct uartbus* bus)
{
	return ((struct testbus*)bus)->random;
}

static uint32_t ubt_current_usec(struct uartbus* bus)
{
	return ((struct testbus*)bus)->time;
}

static uint8_t ubt_send_byte(struct uartbus* bus, uint8_t val)
{
	((struct testbus*)bus)->uart_to_send = val;
	return 0;
}

static void ubt_event(struct uartbus* bus, enum uartbus_event event)
{
	((struct testbus*)bus)->last_event = event;
}

static void ubt_serial_byte_received(struct uartbus* bus, uint8_t data_byte)
{
	((struct testbus*)bus)->sw_next_receive = data_byte;
}

struct testbus* new_bus(uint32_t baud, uint8_t timeout)
{
	struct testbus* ret = malloc(sizeof(struct testbus));
	struct uartbus* bus = &ret->bus;
	memset(bus, 0, sizeof(struct testbus));

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

void test_ub_init_baud_115200_times(void)
{
	struct testbus* bus = new_bus(115200, 3);
	//byte time should be 86 usec.
	TEST_ASSERT_EQUAL(86, bus->bus.byte_time_us);
	TEST_ASSERT_EQUAL(3, bus->bus.packet_timeout);
}


