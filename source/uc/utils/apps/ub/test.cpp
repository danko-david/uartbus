
#include <avr/io.h>
#include <avr/wdt.h>
#include <util/delay.h>

#include "ub_app_wrapper.h"
#include "rpc.h"
/********************************* App section ********************************/

void software_reset()
{
	//wdt_enable(WDTO_15MS);
	//MCUSR = ~8;
	//while(1){}
	asm ("jmp 0x00");
}

void packet_received(struct rpc_request* req)
{
	uint8_t* data = req->payload + req->procPtr;
	uint8_t ep = req->size - req->procPtr;
	if(0 == ep)
	{
		return;
	}

	if(0 == data[0])
	{
		PORTB = 0x0;
	}
	else if(1 == data[0])
	{
		PORTB = 0xff;
	}
	else if(2 == data[0])
	{
		software_reset();
	}
}

void setup()
{
	register_packet_dispatch(packet_received);
}

