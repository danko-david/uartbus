/***
 * Reqired macro definitions:
 *	- BAUD (19200, 115200 etc)
 * 	- PC_SERIAL{_SOFT,0,1,2,3}
 *	- BUS_SERIAL {0,1,2,3}
 *		1,2,3 serial are avilable only in mega
 */

#include "ub.h"
#include <util/atomic.h>
#include "Arduino.h"
#include <avr/interrupt.h>

#define NET_TRAFFIC_LED 13

#ifdef PC_SERIAL_SOFT
	#include <AltSoftSerial.h>
	AltSoftSerial SERIAL_PC;
#endif

#ifdef PC_SERIAL0
	#define SERIAL_PC Serial
#endif

#ifdef PC_SERIAL1
	#define SERIAL_PC Serial1
#endif

#ifdef PC_SERIAL2
	#define SERIAL_PC Serial2
#endif

#ifdef PC_SERIAL3
	#define SERIAL_PC Serial3
#endif



#ifdef BUS_SERIAL0
	#define USARTX_RX_vect _VECTOR(18)
	
	//USART0_RX_vect
	#define UCSRXA UCSR0A
	#define UDRX UDR0
	#define UCSRXA UCSR0A
	#define UCSRXB UCSR0B
	#define UCSRXC UCSR0C
	#define UBRRXL UBRR0L
	#define UDREX UDRE0
	#define TXENX TXEN0
	#define RXENX RXEN0
	#define RXCIEX RXCIE0
	#define U2XX U2X0
	
	#define SERIAL_BUS Serial
#endif

#ifdef BUS_SERIAL1
	#define USARTX_RX_vect USART1_RX_vect
	#define UCSRXA UCSR1A
	#define UDRX UDR1
	#define UCSRXA UCSR1A
	#define UCSRXB UCSR1B
	#define UCSRXC UCSR1C
	#define UBRRXL UBRR1L
	#define UDREX UDRE1
	#define TXENX TXEN1
	#define RXENX RXEN1
	#define RXCIEX RXCIE1
	#define U2XX U2X1

	#define SERIAL_BUS Serial1
#endif

#ifdef BUS_SERIAL2
	#define USARTX_RX_vect USART2_RX_vect
	#define UCSRXA UCSR2A
	#define UDRX UDR2
	#define UCSRXA UCSR2A
	#define UCSRXB UCSR2B
	#define UCSRXC UCSR2C
	#define UBRRXL UBRR2L
	#define UDREX UDRE2
	#define TXENX TXEN2
	#define RXENX RXEN2
	#define RXCIEX RXCIE2
	#define U2XX U2X2

	#define SERIAL_BUS Serial2
#endif

#ifdef BUS_SERIAL3
	#define USARTX_RX_vect USART3_RX_vect
	#define UCSRXA UCSR3A
	#define UDRX UDR3
	#define UCSRXA UCSR3A
	#define UCSRXB UCSR3B
	#define UCSRXC UCSR3C
	#define UBRRXL UBRR3L
	#define UDREX UDRE3
	#define TXENX TXEN3
	#define RXENX RXEN3
	#define RXCIEX RXCIE3
	#define U2XX U2X3

	#define SERIAL_BUS Serial3
#endif



#ifndef MAX_PACKET_SIZE
	#define MAX_PACKET_SIZE 64
#endif

#ifndef PACKET_ESCAPE
	#define PACKET_ESCAPE 0xff
#endif

#define MAX_QUEUE_ENTRY 16

#include "../../bus_gateway/bus_gateway_commons.c"

/******************************** UARTBus *************************************/

void flashLed()
{
	//digitalWrite(NET_TRAFFIC_LED, !digitalRead(NET_TRAFFIC_LED));
}

void USART_Init(void)
{
	UCSRXA = _BV(U2XX); //Double speed mode USARTX
	UCSRXB = _BV(RXENX) | _BV(TXENX) | _BV(RXCIEX);
	UCSRXC = _BV(UCSZ00) | _BV(UCSZ01);
	UBRRXL = (uint8_t)( (F_CPU + BAUD * 4L) / (BAUD * 8L) - 1 );
}

void USART_SendByte(uint8_t u8Data)
{
	// Wait until last byte has been transmitted
	while (!(UCSRXA & _BV(UDREX))){}

	// Transmit data
	UDRX = u8Data;
}

ISR(USARTX_RX_vect)
{
	//check for framing error
	bool error = (UCSRXA & _BV(FE0));
	uint8_t data = UDRX;
	if(error)
	{
	//	ub_out_rec_byte(&bus, ~0);
	}
	else
	{
		ub_out_rec_byte(&bus, data);
	}
}

bool receive = false;

void ub_rec_byte(struct uartbus* a, uint8_t data_byte)
{
	if(receive)
	{
		if(data_byte == PACKET_ESCAPE)
		{
			SERIAL_PC.write(PACKET_ESCAPE);
		}
		SERIAL_PC.write(data_byte);
	}
}

uint8_t ub_do_send_byte(struct uartbus* bus, uint8_t val)
{
	USART_SendByte(val);
	return 0;
}

void ub_event(struct uartbus* a, enum uartbus_event event)
{
	if(ub_event_receive_start == event)
	{
		receive = true;
	}
	else if(ub_event_receive_end == event)
	{
		SERIAL_PC.write(PACKET_ESCAPE);
		SERIAL_PC.write(~PACKET_ESCAPE);
		receive = false;
		SERIAL_PC.flush();
		flashLed();
	}
	
	//os send end or collision
/*	if
	(
			event == ub_event_receive_end
		||
			event == ub_event_send_end
		||
			event == ub_event_collision
	)
	{
		PORTB |= _BV(PB5);
		PCMSK2 |= _BV(PCINT16);
	}
	
	//or send start
	if(ub_event_receive_start == event || ub_event_send_start == event)
	{
		PORTB &= ~_BV(PB5);
		PCMSK2 &= ~_BV(PCINT16);
	}

*/
}

/********************************** serial ************************************/

int ep = 0;
bool mayCut = false;
byte decode[MAX_PACKET_SIZE];

void manage_data_from_pc()
{
	int i = 0;
	while(SERIAL_PC.available() && ++i < 20)
	{
		if(ep >= MAX_PACKET_SIZE)
		{
			ep = 0;//break too long package
		}
	
		byte b = SERIAL_PC.read();

		if(mayCut)
		{
			if(b == (byte)~PACKET_ESCAPE)
			{
				flashLed();
				ATOMIC_BLOCK(ATOMIC_RESTORESTATE)
				{
					queue_enqueue_content(&from_serial, decode, ep);
				}
				ep = 0;
			}
			else
			{
				decode[ep++] = b;
			}
			mayCut = false;
		}
		else
		{
			if(b == (byte)PACKET_ESCAPE)
			{
				mayCut = true;
			}
			else
			{
				decode[ep++] = b;
			}
		}
	}
}

void setup()
{
	pinMode(NET_TRAFFIC_LED, OUTPUT);

	init_ubg_commons();

	USART_Init();
	
	SERIAL_PC.begin(BAUD);
	
	init_bus();
	sei();
}

void loop()
{
	ubg_handle_events();
}

