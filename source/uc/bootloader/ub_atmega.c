
#include "ubh.h"
#include <avr/io.h>
#include <avr/boot.h>
#include <avr/pgmspace.h>
#include <util/delay.h>
#include <avr/interrupt.h>
#include <avr/power.h>
#include <avr/wdt.h>

extern struct uartbus bus;

#ifndef  ARDUINO
	#include <avr/interrupt.h>
	#ifndef sbi
		#define sbi(sfr, bit) (_SFR_BYTE(sfr) |= _BV(bit))
	#endif

	volatile unsigned long timer0_overflow_count = 0;

	#if defined(__AVR_ATtiny24__) || defined(__AVR_ATtiny44__) || defined(__AVR_ATtiny84__)
	ISR(TIM0_OVF_vect)
	#else
	ISR(TIMER0_OVF_vect)
	#endif
	{
		++timer0_overflow_count;
	}

	//avr
	//extracted from: /usr/share/arduino/hardware/arduino/cores/arduino/wiring.c
	uint32_t micros()
	{
		unsigned long m;
		uint8_t oldSREG = SREG, t;

		cli();
		m = timer0_overflow_count;
	#if defined(TCNT0)
		t = TCNT0;
	#elif defined(TCNT0L)
		t = TCNT0L;
	#else
		#error TIMER 0 not defined
	#endif

	#ifdef TIFR0
		if ((TIFR0 & _BV(TOV0)) && (t < 255))
			m++;
	#else
		if ((TIFR & _BV(TOV0)) && (t < 255))
			m++;
	#endif

		SREG = oldSREG;

//		#if F_CPU % 1000000 == 0
			// If the clock frequency is whole of 1 MHz 
			return (uint32_t)
			(
				((m << 8) | t)
			*
				(64 / (F_CPU/1000000L))
			);
/*		#else
			// If the clock frequency is not whole of 1 MHz. eg.: if we use 
			// baud rate compatible cristals like 1.8432 MHz
			return (uint32_t)
			(
				((m << 8) | t)
			*
				(64.0 / (((float)F_CPU)/1000000.0))
			);
		#endif
*/	}

	void ub_init_infrastructure()
	{
		// this needs to be called before setup() or some functions won't
		// work there
		sei();

		// on the ATmega168, timer 0 is also used for fast hardware pwm
		// (using phase-correct PWM would mean that timer 0 overflowed half as often
		// resulting in different millis() behavior on the ATmega8 and ATmega168)
	#if defined(TCCR0A) && defined(WGM01)
		sbi(TCCR0A, WGM01);
		sbi(TCCR0A, WGM00);
	#endif  

		// set timer 0 prescale factor to 64
	#if defined(__AVR_ATmega128__)
		// CPU specific: different values for the ATmega128
		sbi(TCCR0, CS02);
	#elif defined(TCCR0) && defined(CS01) && defined(CS00)
		// this combination is for the standard atmega8
		sbi(TCCR0, CS01);
		sbi(TCCR0, CS00);
	#elif defined(TCCR0B) && defined(CS01) && defined(CS00)
		// this combination is for the standard 168/328/1280/2560
		sbi(TCCR0B, CS01);
		sbi(TCCR0B, CS00);
	#elif defined(TCCR0A) && defined(CS01) && defined(CS00)
		// this combination is for the __AVR_ATmega645__ series
		sbi(TCCR0A, CS01);
		sbi(TCCR0A, CS00);
	#else
		#error Timer 0 prescale factor 64 not set correctly
	#endif

		// enable timer 0 overflow interrupt
	#if defined(TIMSK) && defined(TOIE0)
		sbi(TIMSK, TOIE0);
	#elif defined(TIMSK0) && defined(TOIE0)
		sbi(TIMSK0, TOIE0);
	#else
		#error	Timer 0 overflow interrupt not set correctly
	#endif
		
	}
#else
	void ub_init_infrastructure(){}
#endif
//arduino
__attribute__((noinline))  uint32_t ub_impl_get_current_usec()
{
	return micros();
}

#define REG_INT(X) ISR(X){ubh_provide_dispatch_interrupt((void*)X);}

/***************************** USART functions ********************************/

void USART_Init(void)
{
	#ifdef __AVR_ATmega8__
	  UCSRA = _BV(U2X); //Double speed mode USART
	  UCSRB = _BV(RXEN) | _BV(TXEN) | _BV(RXCIE0);  // enable Rx & Tx
	  UCSRC = _BV(URSEL) | _BV(UCSZ1) | _BV(UCSZ0);  // config USART; 8N1
	  UBRRL = (uint8_t)( (F_CPU + BAUD_RATE * 4L) / (BAUD_RATE * 8L) - 1 );
	#else
	  UCSR0A = _BV(U2X0); //Double speed mode USART0
	  UCSR0B = _BV(RXEN0) | _BV(TXEN0) | _BV(RXCIE0);
	  UCSR0C = _BV(UCSZ00) | _BV(UCSZ01);
	  UBRR0L = (uint8_t)( (F_CPU + BAUD_RATE * 4L) / (BAUD_RATE * 8L) - 1 );
	#endif
}

void USART_SendByte(uint8_t u8Data)
{
	// Wait until last byte has been transmitted
	while (!(UCSR0A & _BV(UDRE0))){}

	// Transmit data
	UDR0 = u8Data;
}



bool ubh_impl_has_app()
{
	return 0xff != pgm_read_byte(APP_START_ADDRESS);
}


__attribute__((section(".bootloader"))) void bootloader_main()
{
	asm ("jmp 0x0");
}

//modified version of:
//https://www.nongnu.org/avr-libc/user-manual/group__avr__boot.html#gaeb0dba1dd9d338516a94c0bd8a8db78a
__attribute__((section(".bootloader")))  void ubh_impl_write_program_page(const uint32_t page,const  uint8_t *buf,const uint8_t size)
{
	if(page < 0x2000 || page > 0x7900l || size > SPM_PAGESIZE)
	{
		return;
	}

    uint16_t i;
    uint8_t sreg;
    // Disable interrupts.
    sreg = SREG;
    cli();
    eeprom_busy_wait ();
    boot_page_erase (page);
    boot_spm_busy_wait ();      // Wait until the memory is erased.
    for (i=0; i<size; i+=2)
    {
        // Set up little-endian word.
        uint16_t w = *buf++;
        w += (*buf++) << 8;
    
        boot_page_fill (page + i, w);
    }
    boot_page_write (page);     // Store buffer in flash page.
    boot_spm_busy_wait();       // Wait until the memory is written.
    // Reenable RWW-section again. We need this if we want to jump back
    // to the application after bootloading.
    boot_rww_enable ();
    // Re-enable interrupts (if they were ever enabled).
    SREG = sreg;
}

uint8_t ubh_impl_do_send_byte(struct uartbus* bus, uint8_t val)
{
	USART_SendByte(val);
	return 0;
}

void ubh_impl_enable_receive_detect_interrupt(bool enable)
{
	if(enable)
	{
		PCMSK2 |= _BV(PCINT16);
	}
	else
	{
		PCMSK2 &= ~_BV(PCINT16);
	}
}

ISR(USART_RX_vect)
{
	//check for framing error
	bool error = (UCSR0A & _BV(FE0));
	uint8_t data = UDR0;
	if(error)
	{
		//ub_out_rec_byte(&bus, ~0);
	}
	else
	{
		ub_out_rec_byte(&bus, data);
	}
}

void ubh_impl_init()
{
	USART_Init();
	
	#ifdef UB_COLLISION_INT
		EICRA= 0;//((1 << ISC21) | (1 << ISC20)); // set sense bits for rising edge
		EIMSK= (1 << 2);//(1 << INT2); // set intrupt #2 enable mask bits
		PCICR=(1 << PCIE2); // set intrupt #2 pin change bits
		PCMSK2=(1 << PCINT16); // set port k/pin 0 change mask bit
	#endif

	DDRB = _BV(PB5);
}

#ifdef UB_COLLISION_INT
ISR(PCINT2_vect)
{
	ub_predict_transmission_start(&bus);
}
#endif

uint8_t ubh_impl_set_user_led(uint8_t val)
{	
	switch(val)
	{
		case 0: PORTB &= ~_BV(PB5); break;
		case 1: PORTB |= _BV(PB5); break;
		case 2: PORTB ^= _BV(PB5); break;
	}
	return PORTB &_BV(PB5)?1:0;
}

void ubh_impl_wdt_start(bool longPeriod)
{
	wdt_enable(longPeriod?WDTO_1S:WDTO_15MS);
}

void ubh_impl_wdt_checkpoint()
{
	wdt_reset();
}

uint8_t ubh_impl_read_code(uint16_t address, uint8_t length, uint8_t* buff)
{
	uint8_t i;
	for(i = 0;i<length;++i)
	{
		buff[i] = pgm_read_byte(address+i);
	}
	
	return length;
}

uint8_t ubh_impl_get_program_page_size()
{
	return SPM_PAGESIZE;
}

uint8_t* ubh_impl_go_upload_and_allocate_program_tmp_storage()
{
	//TODO disable all unrelated ISRs
	//ok at this point i realized it is a fragile and messy solution to
	//collect all the Interrupt flags and reset them (with making special care
	//eg PCICR input masking). I don't do, because:
	//1) Collecting is a manual thing to do, and can be difficult, especially
	//	with point 2.
	//2) Fragile, because if you miss one interrupt or later you just use this
	// code with other microcontrollers with an interrupt not managed here, you
	// might face with random restart during try to upload a new code after
	// using this unlisted interrupt.
	//
	// The solution lies on the other side: before we stating to upload a new
	// code. First we reset and grab the device to not enter into the
	// application mode. If we do so, no interrupt will be enabled except the
	// defaults the the UHB uses, exactly what we need.

	return (uint8_t*) RAMSTART;//(uint8_t*) malloc(SPM_PAGESIZE);
}

uint8_t ubh_impl_get_power_state()
{
	return MCUSR;
}

__attribute__((noinline)) void ubh_impl_call_app(bool first)
{
/*	void (*app)(bool) = (void (*)(bool)) APP_START_ADDRESS;
	app(first);*/
	//asm("call " SX(APP_START_ADDRESS));
	asm("jmp " SX(APP_START_ADDRESS));
	asm ("ret");
}


