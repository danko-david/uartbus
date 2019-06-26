
#include <stdlib.h>
#include <alloca.h>
#include "posix_errno.h"


#include "ub.h"
#include "rpc.h"

/**
 * This variable is provided by the uart_bootloader main component.
 */
extern struct uartbus bus;

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
 * This may erase userspace data, because device will be resetted anyway after
 * the code upload operation.
 */
uint8_t* ubh_impl_allocate_program_tmp_storage();


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
void ubh_impl_write_program_page(uint32_t address, uint8_t* data, uint8_t length);




uint8_t ubh_impl_do_send_byte(struct uartbus* bus, uint8_t val);


/**
 * TODO document bits
 */
uint8_t ubh_impl_get_power_state();


void ubh_impl_call_app(bool first_call);
