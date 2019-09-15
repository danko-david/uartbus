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


/**************************** queue management ********************************/

void* mem_alloc(size_t s)
{
//	cli();
	void* ret = malloc(s);
//	sei();
	return ret;
}

void mem_free(void* a)
{
//	cli();
	free(a);
//	sei();
}

struct queue_entry
{
	struct queue_entry* next;
	uint8_t size;
	uint8_t data[1];
};

struct queue
{
	struct queue_entry* head;
	struct queue_entry* tail;
};

struct queue* new_queue()
{
	struct queue* ret = (struct queue*) mem_alloc(sizeof(struct queue));
	memset(ret, 0, sizeof(struct queue));
	return ret;
}

struct queue_entry* new_queue_entry(size_t size)
{
	struct queue_entry* ret = (struct queue_entry*) mem_alloc(sizeof(struct queue_entry)+size);
	ret->next = NULL;
	ret->size = 0;
	return ret;
}

void free_queue_entry(struct queue_entry* ent)
{
	mem_free(ent);
}

void queue_push(struct queue* q, struct queue_entry* ent)
{
	//cli();
	if(NULL == q->head)
	{
		q->head = ent;
		q->tail = ent;
	}
	else
	{
		q->tail->next = ent;
		q->tail = ent;
	}
	//sei();
}

struct queue_entry* queue_take(struct queue* q)
{
	struct queue_entry* ret = q->head;
	if(NULL != ret)
	{
		//cli();
		q->head = ret->next;
		if(NULL == ret->next)
		{
			q->tail = NULL;
		}
		//sei();
	}
	return ret;
}

void queue_enqueue_content(struct queue* q, uint8_t* data, uint8_t size)
{
	struct queue_entry* add = new_queue_entry(size);
	add->size = size;
	for(size_t i = 0;i<size;++i)
	{
		add->data[i] = data[i];
	}
	queue_push(q, add);
}

/******************************** UARTBus *************************************/

void flashLed()
{
	digitalWrite(NET_TRAFFIC_LED, !digitalRead(NET_TRAFFIC_LED));
}

struct queue* from_serial;

struct uartbus bus;

//int received_ep;
//uint8_t received_data[MAX_PACKET_SIZE];

uint8_t rando()
{
	return rand()%256;
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

static void ub_rec_byte(struct uartbus* a, uint8_t data_byte)
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

static uint8_t ub_do_send_byte(struct uartbus* bus, uint8_t val)
{
	USART_SendByte(val);
	return 0;
}

static void ub_event(struct uartbus* a, enum uartbus_event event)
{
	if(ub_event_receive_start == event)
	{
		//digitalWrite(NET_TRAFFIC_LED, 1);
		/*received_ep = 0;*/
		receive = true;
	}
	else if(ub_event_receive_end == event)
	{
		//digitalWrite(NET_TRAFFIC_LED, 0);
		SERIAL_PC.write(PACKET_ESCAPE);
		SERIAL_PC.write(~PACKET_ESCAPE);
		receive = false;
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

//yet another memory allocation beacuse of the wrong memory ownership design...
uint8_t send_data[MAX_PACKET_SIZE];

static uint8_t send_on_idle(struct uartbus* bus, uint8_t** data, uint16_t* size)
{
	//cli();
	struct queue_entry* send;
	ATOMIC_BLOCK(ATOMIC_RESTORESTATE)
	{
		send = queue_take(from_serial);
	}
	//sei();

	if(NULL != send)
	{
		memcpy(send_data, send->data, send->size);
		*data = send_data;
		*size = send->size;
		ATOMIC_BLOCK(ATOMIC_RESTORESTATE)
		{
			free_queue_entry(send);
		}
		return 0;
	}
	return 1;
}

void init_bus()
{
	//received_ep = 0;

	bus.rand = (uint8_t (*)(struct uartbus*)) rando;
	bus.current_usec = (uint32_t (*)(struct uartbus* bus)) micros;
	bus.serial_byte_received = ub_rec_byte;
	bus.serial_event = ub_event;
	ub_init_baud(&bus, BAUD, 2);
	bus.do_send_byte = ub_do_send_byte;
	bus.cfg = 0
//		|	ub_cfg_fairwait_after_send_high
		|	ub_cfg_fairwait_after_send_low
		|	ub_cfg_read_with_interrupt
		|	ub_cfg_skip_collision_data
	;
	ub_init(&bus);
}



/********************************** serial ************************************/


int ep = 0;
bool mayCut = false;
byte decode[MAX_PACKET_SIZE];

void readSerial()
{
	int i = 0;
	while(SERIAL_PC.available() && ++i < 20)
	{
		byte b = SERIAL_PC.read();

		if(mayCut)
		{
			if(b == (byte)~PACKET_ESCAPE)
			{
				flashLed();
				//cli();
				ATOMIC_BLOCK(ATOMIC_RESTORESTATE)
				{
					queue_enqueue_content(from_serial, decode, ep);
				}
				//sei();
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

void ub_manage()
{
	ub_manage_connection(&bus, send_on_idle);
}

void setup()
{
	pinMode(NET_TRAFFIC_LED, OUTPUT);

	from_serial = new_queue();

	USART_Init();
	
	
	SERIAL_PC.begin
	(
		BAUD
	);
	
	init_bus();
	sei();
}

void loop()
{
	//SERIAL_PC.flush();
	ub_manage();
	readSerial();
}


