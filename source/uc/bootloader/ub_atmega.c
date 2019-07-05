
#include "ubh.h"
#include <avr/io.h>
#include <avr/boot.h>
#include <avr/pgmspace.h>
#include <util/delay.h>
#include <avr/interrupt.h>
#include <avr/power.h>
#include <avr/wdt.h>

//https://stackoverflow.com/questions/6686675/gcc-macro-expansion-arguments-inside-string
#define S(x) #x
#define SX(x) S(x)


#ifndef  ARDUINO
	#include <avr/interrupt.h>
	#ifndef sbi
		#define sbi(sfr, bit) (_SFR_BYTE(sfr) |= _BV(bit))
	#endif
	#define clockCyclesPerMicrosecond() ( F_CPU / 1000000L )
	#define clockCyclesToMicroseconds(a) ( (a) / clockCyclesPerMicrosecond() )
	#define microsecondsToClockCycles(a) ( (a) * clockCyclesPerMicrosecond() )
	#define MICROSECONDS_PER_TIMER0_OVERFLOW (clockCyclesToMicroseconds(64 * 256))
	#define MILLIS_INC (MICROSECONDS_PER_TIMER0_OVERFLOW / 1000)
	#define FRACT_INC ((MICROSECONDS_PER_TIMER0_OVERFLOW % 1000) >> 3)
	#define FRACT_MAX (1000 >> 3)

	volatile unsigned long timer0_overflow_count = 0;
	volatile unsigned long timer0_millis = 0;
	static unsigned char timer0_fract = 0;

	#if defined(__AVR_ATtiny24__) || defined(__AVR_ATtiny44__) || defined(__AVR_ATtiny84__)
	ISR(TIM0_OVF_vect)
	#else
	ISR(TIMER0_OVF_vect)
	#endif
	{
		// copy these to local variables so they can be stored in registers
		// (volatile variables must be read from memory on every access)
		unsigned long m = timer0_millis;
		unsigned char f = timer0_fract;

		m += MILLIS_INC;
		f += FRACT_INC;
		if (f >= FRACT_MAX) {
			f -= FRACT_MAX;
			m += 1;
		}

		timer0_fract = f;
		timer0_millis = m;
		timer0_overflow_count++;
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

		return ((m << 8) + t) * (64 / clockCyclesPerMicrosecond());
	}

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


/**
 * https://www.nongnu.org/avr-libc/user-manual/group__avr__interrupts.html
 *
 * v = document.querySelectorAll("table")[5].querySelectorAll("tr");
 * for(var i=0;i<v.length;++i){console.log(v[0].querySelector("td").textContent);}
 *
 *
 *	#ifdef (.*_vect
	REG_INT((.*_vect)
#endif
) => #ifdef $1\n\tREG_INT($1)\n#endif\n
 *
 * #ifdef TWI_vect REG_INT(TWI_vect); #endif
 *
 *
 * Don't panic only the defined handler gonna be compiled and placed into
 * the assemly
 *
 * */


/*
 used by the bootloader
#ifdef TIMER0_OVF_vect
	REG_INT(TIMER0_OVF_vect)
#endif
*/

/*
USART_RX used by the bootloader
#ifdef USART_RX_vect
	REG_INT(USART_RX_vect)
#endif
*/

/*


#ifdef ADC_vect
	REG_INT(ADC_vect)
#endif

#ifdef ANALOG_COMP_0_vect
	REG_INT(ANALOG_COMP_0_vect)
#endif

#ifdef ANALOG_COMP_1_vect
	REG_INT(ANALOG_COMP_1_vect)
#endif

#ifdef ANALOG_COMP_2_vect
	REG_INT(ANALOG_COMP_2_vect)
#endif

#ifdef ANALOG_COMP_vect
	REG_INT(ANALOG_COMP_vect)
#endif

#ifdef ANA_COMP_vect
	REG_INT(ANA_COMP_vect)
#endif

#ifdef CANIT_vect
	REG_INT(CANIT_vect)
#endif

#ifdef EEPROM_READY_vect
	REG_INT(EEPROM_READY_vect)
#endif

#ifdef EE_RDY_vect
	REG_INT(EE_RDY_vect)
#endif

#ifdef EE_READY_vect
	REG_INT(EE_READY_vect)
#endif

#ifdef EXT_INT0_vect
	REG_INT(EXT_INT0_vect)
#endif

#ifdef INT0_vect
	REG_INT(INT0_vect)
#endif

#ifdef INT1_vect
	REG_INT(INT1_vect)
#endif

#ifdef INT2_vect
	REG_INT(INT2_vect)
#endif

#ifdef INT3_vect
	REG_INT(INT3_vect)
#endif

#ifdef INT4_vect
	REG_INT(INT4_vect)
#endif

#ifdef INT5_vect
	REG_INT(INT5_vect)
#endif

#ifdef INT6_vect
	REG_INT(INT6_vect)
#endif

#ifdef INT7_vect
	REG_INT(INT7_vect)
#endif

#ifdef IO_PINS_vect
	REG_INT(IO_PINS_vect)
#endif

#ifdef LCD_vect
	REG_INT(LCD_vect)
#endif

#ifdef LOWLEVEL_IO_PINS_vect
	REG_INT(LOWLEVEL_IO_PINS_vect)
#endif

#ifdef OVRIT_vect
	REG_INT(OVRIT_vect)
#endif

#ifdef PCINT0_vect
	REG_INT(PCINT0_vect)
#endif

#ifdef PCINT1_vect
	REG_INT(PCINT1_vect)
#endif

#ifdef PCINT2_vect
	REG_INT(PCINT2_vect)
#endif

#ifdef PCINT3_vect
	REG_INT(PCINT3_vect)
#endif

#ifdef PCINT_vect
	REG_INT(PCINT_vect)
#endif

#ifdef PSC0_CAPT_vect
	REG_INT(PSC0_CAPT_vect)
#endif

#ifdef PSC0_EC_vect
	REG_INT(PSC0_EC_vect)
#endif

#ifdef PSC1_CAPT_vect
	REG_INT(PSC1_CAPT_vect)
#endif

#ifdef PSC1_EC_vect
	REG_INT(PSC1_EC_vect)
#endif

#ifdef PSC2_CAPT_vect
	REG_INT(PSC2_CAPT_vect)
#endif

#ifdef PSC2_EC_vect
	REG_INT(PSC2_EC_vect)
#endif

#ifdef SPI_STC_vect
	REG_INT(SPI_STC_vect)
#endif

#ifdef SPM_RDY_vect
	REG_INT(SPM_RDY_vect)
#endif

#ifdef SPM_READY_vect
	REG_INT(SPM_READY_vect)
#endif

#ifdef TIM0_COMPA_vect
	REG_INT(TIM0_COMPA_vect)
#endif

#ifdef TIM0_COMPB_vect
	REG_INT(TIM0_COMPB_vect)
#endif

#ifdef TIM0_OVF_vect
	REG_INT(TIM0_OVF_vect)
#endif

#ifdef TIM1_CAPT_vect
	REG_INT(TIM1_CAPT_vect)
#endif

#ifdef TIM1_COMPA_vect
	REG_INT(TIM1_COMPA_vect)
#endif

#ifdef TIM1_COMPB_vect
	REG_INT(TIM1_COMPB_vect)
#endif

#ifdef TIM1_OVF_vect
	REG_INT(TIM1_OVF_vect)
#endif

#ifdef TIMER0_CAPT_vect
	REG_INT(TIMER0_CAPT_vect)
#endif

#ifdef TIMER0_COMPA_vect
	REG_INT(TIMER0_COMPA_vect)
#endif

#ifdef TIMER0_COMPB_vect
	REG_INT(TIMER0_COMPB_vect)
#endif

#ifdef TIMER0_COMP_A_vect
	REG_INT(TIMER0_COMP_A_vect)
#endif

#ifdef TIMER0_COMP_vect
	REG_INT(TIMER0_COMP_vect)
#endif

#ifdef TIMER0_OVF0_vect
	REG_INT(TIMER0_OVF0_vect)
#endif


#ifdef TIMER1_CAPT1_vect
	REG_INT(TIMER1_CAPT1_vect)
#endif

#ifdef TIMER1_CAPT_vect
	REG_INT(TIMER1_CAPT_vect)
#endif

#ifdef TIMER1_CMPA_vect
	REG_INT(TIMER1_CMPA_vect)
#endif

#ifdef TIMER1_CMPB_vect
	REG_INT(TIMER1_CMPB_vect)
#endif

#ifdef TIMER1_COMP1_vect
	REG_INT(TIMER1_COMP1_vect)
#endif

#ifdef TIMER1_COMPA_vect
	REG_INT(TIMER1_COMPA_vect)
#endif

#ifdef TIMER1_COMPB_vect
	REG_INT(TIMER1_COMPB_vect)
#endif

#ifdef TIMER1_COMPC_vect
	REG_INT(TIMER1_COMPC_vect)
#endif

#ifdef TIMER1_COMPD_vect
	REG_INT(TIMER1_COMPD_vect)
#endif

#ifdef TIMER1_COMP_vect
	REG_INT(TIMER1_COMP_vect)
#endif

#ifdef TIMER1_OVF1_vect
	REG_INT(TIMER1_OVF1_vect)
#endif

#ifdef TIMER1_OVF_vect
	REG_INT(TIMER1_OVF_vect)
#endif

#ifdef TIMER2_COMPA_vect
	REG_INT(TIMER2_COMPA_vect)
#endif

#ifdef TIMER2_COMPB_vect
	REG_INT(TIMER2_COMPB_vect)
#endif

#ifdef TIMER2_COMP_vect
	REG_INT(TIMER2_COMP_vect)
#endif

#ifdef TIMER2_OVF_vect
	REG_INT(TIMER2_OVF_vect)
#endif

#ifdef TIMER3_CAPT_vect
	REG_INT(TIMER3_CAPT_vect)
#endif

#ifdef TIMER3_COMPA_vect
	REG_INT(TIMER3_COMPA_vect)
#endif

#ifdef TIMER3_COMPB_vect
	REG_INT(TIMER3_COMPB_vect)
#endif

#ifdef TIMER3_COMPC_vect
	REG_INT(TIMER3_COMPC_vect)
#endif

#ifdef TIMER3_OVF_vect
	REG_INT(TIMER3_OVF_vect)
#endif

#ifdef TIMER4_CAPT_vect
	REG_INT(TIMER4_CAPT_vect)
#endif

#ifdef TIMER4_COMPA_vect
	REG_INT(TIMER4_COMPA_vect)
#endif

#ifdef TIMER4_COMPB_vect
	REG_INT(TIMER4_COMPB_vect)
#endif

#ifdef TIMER4_COMPC_vect
	REG_INT(TIMER4_COMPC_vect)
#endif

#ifdef TIMER4_OVF_vect
	REG_INT(TIMER4_OVF_vect)
#endif

#ifdef TIMER5_CAPT_vect
	REG_INT(TIMER5_CAPT_vect)
#endif

#ifdef TIMER5_COMPA_vect
	REG_INT(TIMER5_COMPA_vect)
#endif

#ifdef TIMER5_COMPB_vect
	REG_INT(TIMER5_COMPB_vect)
#endif

#ifdef TIMER5_COMPC_vect
	REG_INT(TIMER5_COMPC_vect)
#endif

#ifdef TIMER5_OVF_vect
	REG_INT(TIMER5_OVF_vect)
#endif

#ifdef TWI_vect
	REG_INT(TWI_vect)
#endif

#ifdef TXDONE_vect
	REG_INT(TXDONE_vect)
#endif

#ifdef TXEMPTY_vect
	REG_INT(TXEMPTY_vect)
#endif

#ifdef UART0_RX_vect
	REG_INT(UART0_RX_vect)
#endif

#ifdef UART0_TX_vect
	REG_INT(UART0_TX_vect)
#endif

#ifdef UART0_UDRE_vect
	REG_INT(UART0_UDRE_vect)
#endif

#ifdef UART1_RX_vect
	REG_INT(UART1_RX_vect)
#endif

#ifdef UART1_TX_vect
	REG_INT(UART1_TX_vect)
#endif

#ifdef UART1_UDRE_vect
	REG_INT(UART1_UDRE_vect)
#endif

#ifdef UART_RX_vect
	REG_INT(UART_RX_vect)
#endif

#ifdef UART_TX_vect
	REG_INT(UART_TX_vect)
#endif

#ifdef UART_UDRE_vect
	REG_INT(UART_UDRE_vect)
#endif

#ifdef USART0_RXC_vect
	REG_INT(USART0_RXC_vect)
#endif

#ifdef USART0_RX_vect
	REG_INT(USART0_RX_vect)
#endif

#ifdef USART0_TXC_vect
	REG_INT(USART0_TXC_vect)
#endif

#ifdef USART0_TX_vect
	REG_INT(USART0_TX_vect)
#endif

#ifdef USART0_UDRE_vect
	REG_INT(USART0_UDRE_vect)
#endif

#ifdef USART1_RXC_vect
	REG_INT(USART1_RXC_vect)
#endif

#ifdef USART1_RX_vect
	REG_INT(USART1_RX_vect)
#endif

#ifdef USART1_TXC_vect
	REG_INT(USART1_TXC_vect)
#endif

#ifdef USART1_TX_vect
	REG_INT(USART1_TX_vect)
#endif

#ifdef USART1_UDRE_vect
	REG_INT(USART1_UDRE_vect)
#endif

#ifdef USART2_RX_vect
	REG_INT(USART2_RX_vect)
#endif

#ifdef USART2_TX_vect
	REG_INT(USART2_TX_vect)
#endif

#ifdef USART2_UDRE_vect
	REG_INT(USART2_UDRE_vect)
#endif

#ifdef USART3_RX_vect
	REG_INT(USART3_RX_vect)
#endif

#ifdef USART3_TX_vect
	REG_INT(USART3_TX_vect)
#endif

#ifdef USART3_UDRE_vect
	REG_INT(USART3_UDRE_vect)
#endif

#ifdef USART_RXC_vect
	REG_INT(USART_RXC_vect)
#endif

#ifdef USART_TXC_vect
	REG_INT(USART_TXC_vect)
#endif

#ifdef USART_TX_vect
	REG_INT(USART_TX_vect)
#endif

#ifdef USART_UDRE_vect
	REG_INT(USART_UDRE_vect)
#endif

#ifdef USI_OVERFLOW_vect
	REG_INT(USI_OVERFLOW_vect)
#endif

#ifdef USI_OVF_vect
	REG_INT(USI_OVF_vect)
#endif

#ifdef USI_START_vect
	REG_INT(USI_START_vect)
#endif

#ifdef USI_STRT_vect
	REG_INT(USI_STRT_vect)
#endif

#ifdef USI_STR_vect
#pragma message "USI"
	REG_INT(USI_STR_vect)
#endif

#ifdef WATCHDOG_vect
	REG_INT(WATCHDOG_vect)
#endif

#ifdef WDT_OVERFLOW_vect
	REG_INT(WDT_OVERFLOW_vect)
#endif

#ifdef WDT_vect
	REG_INT(WDT_vect)
#endif

	*/

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

__attribute__((section(".bootloader")))  void ubh_impl_write_program_page(uint32_t page, uint8_t *buf, uint8_t size)
{
    uint16_t i;
    uint8_t sreg;
    // Disable interrupts.
    sreg = SREG;
    cli();
    eeprom_busy_wait ();
    boot_page_erase (page);
    boot_spm_busy_wait ();      // Wait until the memory is erased.
    for (i=0; i<SPM_PAGESIZE; i+=2)
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

	DDRB = _BV(PB5); //DEBUG
	//PORTB |= _BV(PB5);
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

uint8_t* ubh_impl_allocate_program_tmp_storage()
{
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


