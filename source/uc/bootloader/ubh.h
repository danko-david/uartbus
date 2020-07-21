
#include <stdlib.h>
#include <alloca.h>
#include "posix_errno.h"

#ifndef UB_EXCLUDE_UARTBUS_HEADER
	#include "ub.h"
#endif
#include "rpc.h"
#include "addr16.h"

#ifndef MAX_PACKET_SIZE
	#define MAX_PACKET_SIZE 48
#endif

//https://stackoverflow.com/questions/6686675/gcc-macro-expansion-arguments-inside-stringú
#ifndef SX
	#define S(x) #x
	#define SX(x) S(x)
#endif

extern int received_ep;
extern uint8_t received_data[MAX_PACKET_SIZE];

extern uint8_t send_size;
extern uint8_t send_data[MAX_PACKET_SIZE];
extern volatile bool received;

/**
 * Do the microcontoller specific initialisation: eg.:
 *	- condifures the timer registers
 *	- configures the main UART port
 *	- configures the interrupt input (RX)
 *	- configures the user led as output and turn off by default
 */
void ubh_impl_init();

/**
 * modify the user led state:
 * - 0 turn off
 * - 1 turn on
 * - 2 toggle current state
 * - 3 - 255: no effect
 * 
 * return: the state of the user led (0 off, 1 on) after apply.
 */
uint8_t ubh_impl_set_user_led(uint8_t);

/**
 * Is the device has uploaded application on board?
 */
bool ubh_impl_has_app();

/**
 * The current microsecounds eslaped since the device is turned on.
 */
uint32_t micros();

/**
 * A random number between 0 - 255.
 */
uint8_t rando();


void ubh_impl_enable_receive_detect_interrupt(bool enable);


/**
 * start the whatchdog timer.
 * param:	true: long period, used to time out application (1 secounds)
 * 			false: short period, the shortest possible to commit a WDT hardware
 * 				reset.
 */
void ubh_impl_wdt_start(bool);

/**
 * Allocates memory area to store program we receive from the network, and used
 *	to program device. The size of the area specified by your
 * uint8_t ubh_impl_get_program_page_size() return value
 *
 * It also does the necessary operations to the application code can be
 * 	overwritten eg: disable all interrupt handler not related to the uartbus
 * 	infrastructure
 *
 * This may erase userspace data, because device will be reseted anyway after
 * the code upload operation.
 */
uint8_t* ubh_impl_go_upload_and_allocate_program_tmp_storage();


/**
 * returns the size of the program page used to program the device.
 * 
 * TODO adjust size of the 
 */
uint8_t ubh_impl_get_program_page_size();

/**
 * Reset the watchdog timer, so we have one more period of time to execute code.
 */
void ubh_impl_wdt_checkpoint();

/**
 * reads the executable code of the device in the given area.
 *	- address: the start memory address to read 
 *	- length: the length of the code to read
 *	- buff: target buffer
 * 	return: the count of written bytes into the buffer.
 *
 *	TODO adjust address and length types to the target device
 */
uint8_t ubh_impl_read_code(uint16_t address, uint8_t length, uint8_t* buff);

/**
 * Writes the application memory page (usually the program flash memory) 
 *
 */
void ubh_impl_write_program_page(const uint32_t address,const uint8_t* data,const uint8_t length);




uint8_t ubh_impl_do_send_byte(struct uartbus* bus, uint8_t val);


/**
 * TODO document bits
 */
uint8_t ubh_impl_get_power_state();


void ubh_impl_call_app(bool first_call);


void ubh_provide_dispatch_interrupt(void*);


void init_bus();

void ubh_manage_bus();

