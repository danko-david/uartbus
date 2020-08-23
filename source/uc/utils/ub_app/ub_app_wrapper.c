
#include "ub_app_wrapper.h"
#include <avr/pgmspace.h>

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

extern uint8_t __data_start;
extern uint8_t __data_end;
extern uint8_t __data_load_start;

__attribute__((noinline)) void ub__do_copy_data()
{
	//__data_end __data_start __data_load_start
	uint8_t* p = &__data_start;
	while(p < &__data_end)
	{
		//*p = *(__data_load_start + (p-__data_start));
		//*(&__data_load_start+(p-&__data_start)) = *p;
		*p = pgm_read_byte(&__data_load_start + (p-&__data_start));
		++p;
	}
}

extern unsigned int __bss_start;
extern unsigned int __bss_end;

//void __do_copy_data(){};
__attribute__((noinline)) void ub__do_clear_bss()
{
	unsigned int* p = &__bss_start;
	while(p < &__bss_end)
	{
		*p++ = 0;
	}
};


__attribute__ ((weak)) void init(){};

extern void init();

//TODO if target AVR

//https://arduino.stackexchange.com/questions/75604/avr-relocated-code-calling-global-constructors


/*
extern "C"
{
	extern void* __ctors_start;
	extern void* __ctors_end;

	__attribute__((noinline)) void call_global_ctors()
	{
		for
		(
			uint16_t i = (uint16_t) __ctors_start;
			i < (uint16_t) __ctors_end;
			i += 2
		)
		{
			uint16_t addr = pgm_read_word_near(i);
			asm("icall":: "z" (addr));
		}
	}
}
*/

/*
extern "C"
{
	extern void (*__ctors_start)();
	extern void (*__ctors_end)();

	__attribute__((noinline)) void call_global_ctors()
	{
//		for
//		(
			void (*i)() = (void (*)()) 0x20E4;//__ctors_start;
//			i < __ctors_end;
//			++i
//		)
		{
			uint16_t addr = pgm_read_word_near(i);
			asm("icall":: "z" (addr));
		}
	}
}
*/

#include <avr/interrupt.h>
extern void (*__ctors_start)();
extern void (*__ctors_end)();

//__attribute__((noinline)) void call_global_ctors()
ISR(call_global_ctors)
{
	for
	(
		register uint16_t i asm ("r24") = (uint16_t) &__ctors_start;
		i < (uint16_t) &__ctors_end;
		i+= 2
	)
	{
		uint16_t addr = pgm_read_word_near(i);
		asm("push r24\n");
		asm("push r25\n");
		
		asm("icall":: "z" (addr));
		
		asm("pop r25\n");
		asm("pop r24\n");
	}
}

//TODO end AVR

static void init_app_section()
{
	ub__do_copy_data();
	ub__do_clear_bss();
	
	call_global_ctors();
	init();
}

//app_start section starts at 0x2000
__attribute__((weak, noinline, section(".app_start"))) void ub_app(bool first)
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
__attribute__ ((weak)) int main(){ ub_app(true);}

/*
void (*register_packet_dispatch)(void (*addr)(struct rpc_request* req));
bool (*may_send_packet)();
bool (*send_packet)(int16_t, uint8_t* , uint16_t);
uint8_t (*get_max_packet_size)();
*/

void register_packet_dispatch(void (*addr)(struct rpc_request* req))
{
	void*** fns = (void***) getHostTableAddress();
	
	void (*reg)(void (*addr)(struct rpc_request* req)) =
		(void (*)(void (*addr)(struct rpc_request* req))) fns[0];
		
	reg(addr);
}

bool may_send_packet()
{
	void*** fns = (void***) getHostTableAddress();
	
	bool (*may)() =
		(bool (*)()) fns[1];
		
	return may();
}

bool send_packet(int16_t to, int NS, uint8_t* data, uint16_t size)
{
	void*** fns = (void***) getHostTableAddress();
	
	bool (*reg)(int16_t, uint8_t, uint8_t*, uint16_t) =
		(bool (*)(int16_t, uint8_t, uint8_t*, uint16_t)) fns[2];
		
	return reg(to, NS, data, size);
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

//TODO flush pending packet
//TODO manage bus
//TODO soft/hardware reset
//TODO exit => send exit status and do a hardware reboot

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
