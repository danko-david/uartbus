
#include "ub_app_wrapper.h"
#include <avr/pgmspace.h>

#ifdef __cplusplus
extern "C"{
#endif

//https://stackoverflow.com/questions/6686675/gcc-macro-expansion-arguments-inside-string
#define S(x) #x
#define SX(x) S(x)

__attribute__((noinline, optimize("O1"), noipa)) void** getHostTableAddress()
{
	asm("jmp " SX(HOST_TABLE_ADDRESS));
}

__attribute__ ((weak)) void setup(){};
__attribute__ ((weak)) void loop(){};

extern uint8_t __data_start;
extern uint8_t __data_end;
extern uint8_t __data_load_start;

void ub__do_copy_data()
{
	uint8_t* p = &__data_start;
	while(p < &__data_end)
	{
		*p = pgm_read_byte(&__data_load_start + (p-&__data_start));
		++p;
	}
}

extern unsigned int __bss_start;
extern unsigned int __bss_end;

void ub__do_clear_bss()
{
	unsigned int* p = &__bss_start;
	while(p < &__bss_end)
	{
		*p++ = 0;
	}
};

__attribute__ ((weak)) void init(){};

extern void init();

#include <avr/interrupt.h>
extern void (*__ctors_start)();
extern void (*__ctors_end)();

__attribute__((noinline)) void call_global_ctors()
{
	uint16_t start = (uint16_t)&__ctors_start;
	uint16_t end = (uint16_t)&__ctors_end;
	
	for (uint16_t i = start; i < end; i += 2)
	{
		uint16_t addr = pgm_read_word_near(i);
		asm volatile(
			"icall \n\t" 
			: 
			: "z" (addr) 
			: "r0", "r18", "r19", "r20", "r21", "r22", "r23", "r24", "r25", "r26", "r27"
		);
	}
}

void init_ub_app()
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
}

/**
 * I use this way to prevent -Wl,--gc-sections to optimise out ub_app.
 * main() function is required by the compilation but normally this main
 * function will never be called and so it's saves the ub_app function,
 * but never calls.
 */
__attribute__((weak)) int main(){ ub_app(true);}

void register_packet_dispatch(void (*addr)(struct rpc_request* req))
{
	void** fns = getHostTableAddress();
	asm volatile("" : "+r"(fns));
	((void (*)(void (*addr)(struct rpc_request* req))) fns[0])(addr);
}

bool may_send_packet()
{
	void** fns = getHostTableAddress();
	asm volatile("" : "+r"(fns));
	bool (*may)() = (bool (*)()) fns[1];
	return may();
}

bool send_packet(int16_t to, int NS, uint8_t* data, uint16_t size)
{
	void** fns = getHostTableAddress();
	asm volatile("" : "+r"(fns));
	bool (*reg)(int16_t, uint8_t, uint8_t*, uint16_t) =
		(bool (*)(int16_t, uint8_t, uint8_t*, uint16_t)) fns[2];
	return reg(to, NS, data, size);
}

__attribute__((weak)) uint32_t micros()
{
	void** fns = getHostTableAddress();
	void* volatile* volatile_fns = (void* volatile*)fns;
	uint32_t (*f)() = (uint32_t (*)()) volatile_fns[4];
	return f();
}

__attribute__((weak)) uint32_t millis()
{
	return micros()/1000;
}

#ifdef __cplusplus
} // extern "C"
#endif
