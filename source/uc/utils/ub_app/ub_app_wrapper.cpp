
#include "ub_app_wrapper.h"

//https://stackoverflow.com/questions/6686675/gcc-macro-expansion-arguments-inside-string
#define S(x) #x
#define SX(x) S(x)

__attribute__((noinline)) void*** getHostTableAddress()
{
	asm("jmp " SX(HOST_TABLE_ADDRESS));
}

__attribute__ ((weak)) void setup(){};

__attribute__ ((weak)) void loop(){};

int main(){}

#include <avr/io.h>

__attribute__ ((weak)) void __do_copy_data(){};
__attribute__ ((weak)) void __do_clear_bss(){};

extern void __do_copy_data();
extern void __do_clear_bss();

static void init_app_section()
{
	__do_copy_data();
	__do_clear_bss();
}

//app_start section starts at 0x2000
__attribute__((noinline, section(".app_start"))) void ub_app(bool first)
{
	if(first)
	{
		init_ub_app();
		setup();
	}
	loop();
	asm ("ret");
}

/*
void (*register_packet_dispatch)(void (*addr)(struct rpc_request* req));
bool (*may_send_packet)();
bool (*send_packet)(int16_t, uint8_t* , uint16_t);
uint8_t (*get_max_packet_size)();
*/

bool send_packet(int16_t to, int NS, uint8_t* data, uint16_t size)
{
	void*** fns = (void***) getHostTableAddress()[0];
	
	bool (*reg)(int16_t, uint8_t, uint8_t*, uint16_t) =
		(bool (*)(int16_t, uint8_t, uint8_t*, uint16_t)) fns[2];
		
	reg(to, NS, data, size);
}

void register_packet_dispatch(void (*addr)(struct rpc_request* req))
{
	void*** fns = (void***) getHostTableAddress()[0];
	
	void (*reg)(void (*addr)(struct rpc_request* req)) =
		(void (*)(void (*addr)(struct rpc_request* req))) fns[0];
		
	reg(addr);
}

uint32_t micros()
{
	void*** fns = (void***) getHostTableAddress()[0];
	
	uint32_t (*f)() = (uint32_t (*)()) fns[4];
		
	return f();
}

void init_ub_app()
{
	init_app_section();
	//HOST_TABLE_ADDRESS
/*	void** fns = getHostTableAddress()[0];
	register_packet_dispatch = (void (*)(void (*)(int16_t, int16_t, uint8_t*, uint8_t))) fns[0];
	may_send_packet = (bool (*)()) fns[1];
	send_packet = (bool (*)(int16_t, uint8_t* , uint16_t)) fns[2];
	get_max_packet_size = (uint8_t (*)()) fns[3];*/
}

