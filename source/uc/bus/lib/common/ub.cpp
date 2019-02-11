/**
 * UartBus is an extension for uart, makes possible to send packet between
 * multiple uC using only 1 dataline.
 *
 * TODO:
 *
 *
 *
 * Motivation: many microcontroller, embedded system, even desktop machines
 * has UART port, what makes possible to establish a point-to-point
 * communication between these devices.
 *
 * But the real reason is, I need a cheap, widely usable bus system.
 * As main goal: connect a bunch of microcontrollers at home, where are long
 * distances between units and connect this network to a small computer
 * (eg.: Raspberry pi), secondly, use few wires as possible.
 *
 * How to achieve that? Look up for a communication interface that available
 * in most of the chips and systems, study the best technics of each, then hack
 * them together:
 * uartbus made from:
 * 	- I2C: it's awesome that it's connects multiple uC together, but it's
 *	requires 2 line to communicate (data and clock)
 * (4 at least: GND, VCC, DATA, CLK)
 * 	And used for short distance.
 *
 * Uart: it's uses 2 wires for each direction. But i'ts designed to point-to
 * point communication
 *
 * */


#include "ub.h"

#ifdef __linux__
	#include <sys/time.h>
	uint32_t ub_impl_get_current_usec()
	{
		struct timeval tv;
		struct timezone tz;
		int stat = gettimeofday(&tv, &tz);
		uint32_t now =
		//	(tv.tv_sec*1000 + tv.tv_usec);//% ((uint32_t) ~0);
			tv.tv_usec;
		//printf("now: %d\n", now);
		return now;
	}
	//empty call
	void ub_init_infrastructure(){};
#else

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
		//TODO extract from: /usr/share/arduino/hardware/arduino/cores/arduino/wiring.c
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
#endif

static void inline ub_update_last_activity_now(struct uartbus* bus)
{
	bus->last_bus_activity = ub_impl_get_current_usec();
}

__attribute__((noinline)) static bool is_slice_exceed
(
	struct uartbus* bus,
	bool update
)
{
	uint32_t last = bus->last_bus_activity;
	uint32_t now = ub_impl_get_current_usec();

	if(update)
	{
		bus->last_bus_activity = now;
	}

	//check for overflow and calculate back
	if(now < last)
	{
		return now + (UINT32_MAX - last) >= bus->bus_idle_time;
	}
	return now-last >= bus->bus_idle_time;
}

static uint8_t get_fairwait_conf_cycles(struct uartbus* bus)
{
	uint8_t ret = 0;
	if(bus->cfg & ub_cfg_fairwait_after_send_low)
	{
		ret = 0x1;
	}

	if(bus->cfg & ub_cfg_fairwait_after_send_high)
	{
		ret |= 0x2;
	}

	return (ret+1)*2;
}

//https://www.ccsinfo.com/forum/viewtopic.php?t=37015
//x^8+x^5+x^4+x^0 //Dallas-Maxim CRC8
uint8_t crc8(uint8_t* data, uint8_t length)
{
	uint8_t crc = 0;
	uint8_t v;
	uint8_t i;
	for(i=0;i<length;++i)
	{
		v = (data[i] ^ crc) & 0xff; 
		crc = 0;
		if(v & 1)
			crc ^= 0x5e; 
		if(v & 2)
			crc ^= 0xbc; 
		if(v & 4)
			crc ^= 0x61; 
		if(v & 8)
			crc ^= 0xc2; 
		if(v & 0x10)
			crc ^= 0x9d; 
		if(v & 0x20)
			crc ^= 0x23; 
		if(v & 0x40)
			crc ^= 0x46; 
		if(v & 0x80)
			crc ^= 0x8c;
	}

	return crc;
}

__attribute__((noinline)) void ub_out_update_state(struct uartbus* bus)
{
	enum uartbus_status status = bus->status;

	bool exceed = is_slice_exceed(bus, false);

	if(exceed)
	{
		//if we transmitted previously and cycles time exceed to go idle
		//we really go idle
		if(ub_stat_sending == status || ub_stat_sending_fairwait == status)
		{
			uint8_t fw = get_fairwait_conf_cycles(bus);
			/*Serial.print("until: ");
			Serial.print(bus->last_bus_activity);
			Serial.print(" + ");
			Serial.print(fw);
			Serial.print(" * ");
			Serial.print(bus->bus_idle_time);
			Serial.print(" = ");
			Serial.print(bus->last_bus_activity + fw*bus->bus_idle_time);
			Serial.print(", now: ");
			Serial.println(ub_impl_get_current_usec());*/
			if
			(
					bus->last_bus_activity + fw*bus->bus_idle_time
				>
					ub_impl_get_current_usec()
			)
			{
				bus->status = ub_stat_sending_fairwait;
				return;
			}
			
			ub_update_last_activity_now(bus);
			bus->status = ub_stat_idle;

			//else we still in send (it's necessary to keep the bus busy for a
			//certain time to others can acquire the transmission line
			//and we can't send before this time exceeds) [fairwait feature]
			return;
		}

		if(ub_stat_receiving == status)
		{
			//if there's no more byte to receive, of course we will not
			//notified by the ub_out_rec_byte function, so we use this function
			//to notify the client
			bus->status = ub_stat_idle;
			bus->serial_event(bus, ub_event_receive_end);
		}

		if(ub_stat_connecting == status)
		{
			bus->status = ub_stat_idle;
		}
	}
}

/**
 * Call this method from the outside to dispatch received byte (if it's come
 * from other device)
 * */
__attribute__((noinline)) void ub_out_rec_byte(struct uartbus* bus, uint8_t data)
{
	enum uartbus_status status = bus->status;
	if(ub_stat_sending == status)
	{
		//we receive the data back that we sending now
		return;
	}

	bool ex = is_slice_exceed(bus, true);

	if(ex)
	{
		if(ub_stat_receiving == status)
		{
			//the previous transmission closed but not yet notified
			bus->serial_event(bus, ub_event_receive_end);
		}
		bus->status = ub_stat_idle;
	}

	if(ub_stat_idle == status || ub_stat_sending_fairwait == status)
	{
		bus->status = ub_stat_receiving;
		//if we idle we notifies the start only
		bus->serial_event(bus, ub_event_receive_start);
	}

	bus->serial_byte_received(bus, data);
}

/*
 * ret:
 * positive value: user defined error returned from do_send_byte, if value is
 * 	negative, returted with abs(retval)
 * 	0	success
 * 	-1	bus is busy
 *	-2	reread_mismatch
 * */
int8_t ub_send_packet(struct uartbus* bus, uint8_t* addr, uint16_t size)
{
	if(bus->status != ub_stat_idle)
	{
		return 1;
	}

	bus->status = ub_stat_sending;

	uint8_t (*send)(struct uartbus* bus, uint8_t) = bus->do_send_byte;
	int16_t (*rec)(struct uartbus* bus) = bus->do_receive_byte;

	uint8_t stat;
	for(int i=0;i<size;++i)
	{
		uint8_t val = addr[i];
		stat = send(bus, val);
		if(0 != stat)
		{
			if(stat < 0)
			{
				return -stat;
			}
			return stat;
		}

		//update the last acitivity time
		ub_update_last_activity_now(bus);

		if(bus->cfg & ub_cfg_read_after_send)
		{
			//if rereaded value mismatch: interrupt if specified so in config
			int16_t re = bus->do_receive_byte(bus);
			if(((int16_t)re) != val)
			{
				if(bus->cfg & ub_cfg_interrupt_bad_send)
				{
					return -2;
				}
			}
		}
	}

	return 0;
}

void ub_init(struct uartbus* bus)
{
	//we are "connecting" (getting synchronized to the bus) and we go busy
	//'till we ensure, there's no transmission on the bus or we capture one.
	bus->status = ub_stat_connecting;
	ub_update_last_activity_now(bus);
}

enum uartbus_status ub_get_bus_state(struct uartbus* bus)
{
	return bus->status;
}

#ifndef DONT_USE_SOFT_FP
uint16_t ub_calc_baud_cycle_time(uint32_t baud)
{
	float val = baud;
	val = 11000000.0/val;
	return val;
}

uint32_t ub_calc_timeout(uint32_t baud, uint8_t cycles)
{
	float val = baud;
	val = (11000000.0/baud);
	val *= cycles;
	return val;
}
#endif

void ub_try_receive(struct uartbus* bus)
{
	int16_t re = 0;
	while(true)
	{
		re = bus->do_receive_byte(bus);
		if(re < 0 || re > 255)
		{
			break;
		}
		else
		{
			ub_out_rec_byte(bus, (uint8_t) re);
		}
	}
}

__attribute__((noinline)) uint8_t ub_manage_connection
(
	struct uartbus* bus,
	uint8_t (*send_on_idle)(struct uartbus*, uint8_t** data, uint16_t* size)
)
{
	if(!(bus->cfg & ub_cfg_external_read))
	{
		ub_try_receive(bus);
	}

	if(NULL == send_on_idle)
	{
		return 0;
	}

	//concurrency point: if we received, we have to delay the next possible
	//data sending
	ub_out_update_state(bus);
	/*if(ub_stat_sending_fairwait == stat)
	{
		Serial.print("stat: ");
		Serial.println(stat);
	}*/
	if(ub_stat_idle == bus->status)
	{
		uint8_t* data;
		uint16_t size;
		if(0 != send_on_idle(bus, &data, &size))
		{
			return 0;
		}

		return ub_send_packet(bus, data, size);
	}

	return 0;
}

