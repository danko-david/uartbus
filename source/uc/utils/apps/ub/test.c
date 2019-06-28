
#include <avr/io.h>
#include <avr/wdt.h>
#include <util/delay.h>

#include "ub_app_wrapper.h"
#include "rpc.h"
/********************************* App section ********************************/

void software_reset()
{
	asm ("jmp 0x00");
}

void hardware_reset()
{
	wdt_enable(WDTO_15MS);
	MCUSR = ~8;
	while(1){}
}

uint8_t val = 0;

void packet_received(struct rpc_request* req)
{
	++req->procPtr;
	uint8_t* data = req->payload + req->procPtr;
	uint8_t ep = req->size - req->procPtr;
	if(0 == ep || 32 != data[-1])
	{
		return;
	}

	if(0 == data[0])
	{
		PORTB = 0x0;
		il_reply(req, 1, 0);
	}
	else if(1 == data[0])
	{
		PORTB = 0xff;
		il_reply(req, 1, 0);
	}
	else if(2 == data[0])
	{
		software_reset();
	}
	else if(3 == data[0])
	{
		hardware_reset();
	}
	else if(4 == data[0])
	{
		val = data[1];
		il_reply(req, 1, 0);
	}
	else if(5 == data[0])
	{
		il_reply(req, 1, val);
	}
}

void setup()
{
	register_packet_dispatch(packet_received);
}

