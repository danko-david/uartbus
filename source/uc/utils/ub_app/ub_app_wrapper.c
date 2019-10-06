
#include "ub_app_wrapper.h"

#ifdef __cplusplus
extern "C"{
#endif

//https://stackoverflow.com/questions/6686675/gcc-macro-expansion-arguments-inside-string
#define S(x) #x
#define SX(x) S(x)

__attribute__((noinline)) void*** getHostTableAddress()
{
	asm("jmp " SX(HOST_TABLE_ADDRESS));
	return NULL;//keep the compiler happy
}

__attribute__ ((weak)) void setup(){};

__attribute__ ((weak)) void loop(){};

__attribute__ ((weak)) void __do_copy_data(){};
__attribute__ ((weak)) void __do_clear_bss(){};

extern void __do_copy_data();
extern void __do_clear_bss();

__attribute__ ((weak)) void init(){};

extern void init();

//TODO extern void __do_global_ctors() when the application is in C++

static void init_app_section()
{
	__do_copy_data();
	__do_clear_bss();
	init();
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

/**
 * I use this way to prevent -Wl,--gc-sections to optimise out ub_app.
 * main() function is required by the compilation but normally this main
 * function will never be called and so it's saves the ub_app function,
 * but never calls.
 */
int main(){ ub_app(true);}

/*
void (*register_packet_dispatch)(void (*addr)(struct rpc_request* req));
bool (*may_send_packet)();
bool (*send_packet)(int16_t, uint8_t* , uint16_t);
uint8_t (*get_max_packet_size)();
*/

bool send_packet(int16_t to, int NS, uint8_t* data, uint16_t size)
{
	void*** fns = (void***) getHostTableAddress();
	
	bool (*reg)(int16_t, uint8_t, uint8_t*, uint16_t) =
		(bool (*)(int16_t, uint8_t, uint8_t*, uint16_t)) fns[2];
		
	return reg(to, NS, data, size);
}

void register_packet_dispatch(void (*addr)(struct rpc_request* req))
{
	void*** fns = (void***) getHostTableAddress();
	
	void (*reg)(void (*addr)(struct rpc_request* req)) =
		(void (*)(void (*addr)(struct rpc_request* req))) fns[0];
		
	reg(addr);
}

__attribute__((weak)) uint32_t micros()
{
	void*** fns = (void***) getHostTableAddress();
	
	uint32_t (*f)() = (uint32_t (*)()) fns[4];
		
	return f();
}

__attribute__((weak)) uint32_t millis()
{
	return micros()/1000;
}

void init_ub_app()
{
	init_app_section();
	//HOST_TABLE_ADDRESS
/*	void** fns = getHostTableAddress();
	register_packet_dispatch = (void (*)(void (*)(int16_t, int16_t, uint8_t*, uint8_t))) fns[0];
	may_send_packet = (bool (*)()) fns[1];
	send_packet = (bool (*)(int16_t, uint8_t* , uint16_t)) fns[2];
	get_max_packet_size = (uint8_t (*)()) fns[3];*/
}

#ifdef __cplusplus
} // extern "C"
#endif
