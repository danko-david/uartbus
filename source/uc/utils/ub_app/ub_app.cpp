
#include "ub_app.h"

static bool initialized = false;

__attribute__ ((weak)) void setup(){};

__attribute__ ((weak)) void loop(){};

//app_start section starts at 0xFFF
__attribute__((noinline, section(".app_start"))) int main() 
{
	if(!initialized)
	{
		init_ub_app();
		setup();
		initialized = true;
	}
	loop();
}

void (*register_packet_dispatch)(void (*)(int16_t, int16_t, uint8_t*, uint8_t));
bool (*may_send_packet)();
bool (*send_packet)(int16_t, uint8_t* , uint16_t);
uint8_t (*get_max_packet_size)();

//https://stackoverflow.com/questions/6686675/gcc-macro-expansion-arguments-inside-string
#define S(x) #x
#define SX(x) S(x)

__attribute__((noinline)) void*** getHostTableAddress()
{
	asm("jmp " SX(HOST_TABLE_ADDRESS));
	//asm("jmp " HOST_TABLE_ADDRESS);
	//asm("jmp %[addr]": [addr]"I"(HOST_TABLE_ADDRESS));
	//return asdf();
	//return ((void***(*)())0xCFE)();
}

void init_ub_app()
{
	//HOST_TABLE_ADDRESS
	void** fns = getHostTableAddress()[0];
	register_packet_dispatch = (void (*)(void (*)(int16_t, int16_t, uint8_t*, uint8_t))) fns[0];
	may_send_packet = (bool (*)()) fns[1];
	send_packet = (bool (*)(int16_t, uint8_t* , uint16_t)) fns[2];
	get_max_packet_size = (uint8_t (*)()) fns[3];
}

