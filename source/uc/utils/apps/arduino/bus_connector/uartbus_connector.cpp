/***
 * Reqired macro definitions:
 *	- BAUD (19200, 115200 etc)
 * 	- PC_SERIAL{_SOFT,0,1,2,3}
 *	- BUS_SERIAL {_SOFT,0,1,2,3}
 *		1,2,3 serial are avilable only in mega
 */

#include "ub.h"
#include "Arduino.h"

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


/*
#ifdef BUS_SERIAL_SOFT
	#include <AltSoftSerial.h>
	AltSoftSerial SERIAL_BUS;
#endif

#ifdef BUS_SERIAL0
	#define SERIAL_BUS Serial
#endif

#ifdef BUS_SERIAL1
	#define SERIAL_BUS Serial1
#endif

#ifdef BUS_SERIAL2
	#define SERIAL_BUS Serial2
#endif

#ifdef BUS_SERIAL3
	#define SERIAL_BUS Serial3
#endif
*/


#ifndef MAX_PACKET_SIZE
	#define MAX_PACKET_SIZE 64
#endif

#ifndef PACKET_ESCAPE
	#define PACKET_ESCAPE 0xff
#endif


/**************************** queue management ********************************/

void* mem_alloc(size_t s)
{
	cli();
	void* ret = malloc(s);
	sei();
	return ret;
}

void mem_free(void* a)
{
	cli();
	free(a);
	sei();
}

struct queue_entry
{
	struct queue_entry* next;
	uint8_t size;
	uint8_t data[MAX_PACKET_SIZE];
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

struct queue_entry* new_queue_entry()
{
	struct queue_entry* ret = (struct queue_entry*) mem_alloc(sizeof(struct queue_entry));
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
	cli();
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
	sei();
}

struct queue_entry* queue_take(struct queue* q)
{
	struct queue_entry* ret = q->head;
	if(NULL != ret)
	{
		cli();
		q->head = ret->next;
		if(NULL == ret->next)
		{
			q->tail = NULL;
		}
		sei();
	}
	return ret;
}

void queue_enqueue_content(struct queue* q, uint8_t* data, uint8_t size)
{
	struct queue_entry* add = new_queue_entry();
	add->size = size;
	memcpy(add->data, data, size);
	queue_push(q, add);
}

/******************************** UARTBus *************************************/


struct queue* from_bus;
struct queue* from_serial;

struct uartbus bus;
int received_ep;

uint8_t received_data[MAX_PACKET_SIZE];

uint8_t rando()
{
	return rand()%256;
}

void USART_Init(void)
{
	UCSR3A = _BV(U2X3); //Double speed mode USART3
	UCSR3B = _BV(RXEN3) | _BV(TXEN3) | _BV(RXCIE3);
	UCSR3C = _BV(UCSZ00) | _BV(UCSZ01);
	UBRR3L = (uint8_t)( (F_CPU + BAUD * 4L) / (BAUD * 8L) - 1 );
}

void USART_SendByte(uint8_t u8Data)
{
	// Wait until last byte has been transmitted
	while (!(UCSR3A & _BV(UDRE3))){}

	// Transmit data
	UDR3 = u8Data;
}

ISR(USART3_RX_vect)
{
	//check for framing error
	bool error = (UCSR3A & _BV(FE0));
	uint8_t data = UDR3;
	if(error)
	{
		ub_out_rec_byte(&bus, ~0);
	}
	else
	{
		ub_out_rec_byte(&bus, data);
	}
}

static void ub_rec_byte(struct uartbus* a, uint8_t data_byte)
{
	if(received_ep == MAX_PACKET_SIZE)
	{
		//brake the package manually
		received_ep = 0;
	}
	else
	{
		received_data[received_ep++] = data_byte;
	}
}

static uint8_t ub_do_send_byte(struct uartbus* bus, uint8_t val)
{
	USART_SendByte(val);
	return 0;
}

static void ub_event(struct uartbus* a, enum uartbus_event event)
{
	if
	(
			ub_event_collision_start == event
		||
			ub_event_receive_start == event
		||
			ub_event_send_end == event
	)
	{
		received_ep = 0;
	}


	if(ub_event_receive_end == event)
	{
		if(0 != received_ep)
		{
			queue_enqueue_content(from_bus, received_data, received_ep);
			received_ep = 0;
		}
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
	struct queue_entry* send = queue_take(from_serial);

	if(NULL != send)
	{
		memcpy(send_data, send->data, send->size);
		*data = send_data;
		*size = send->size;
		free_queue_entry(send);
		return 0;
	}
	return 1;
}

void init_bus()
{
	received_ep = 0;

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

byte encode[MAX_PACKET_SIZE*2+2];

void flashLed()
{
	digitalWrite(NET_TRAFFIC_LED, !digitalRead(NET_TRAFFIC_LED));
}

void serialWriteEscape(uint8_t* data, uint8_t size)
{	
	for(uint8_t i=0;i<size;++i)
	{
		if(data[i] == PACKET_ESCAPE)
		{
			SERIAL_PC.write(PACKET_ESCAPE);
		}
		SERIAL_PC.write(data[i]);
	}
	
	SERIAL_PC.write(PACKET_ESCAPE);
	SERIAL_PC.write(~PACKET_ESCAPE);
	
	flashLed();
}

void relaySerial()
{
	//pop data and relay
	struct queue_entry* send = queue_take
	(
		from_bus
	);

	if(NULL != send)
	{
		serialWriteEscape(send->data, send->size);
		free_queue_entry(send);
	}
}

int ep = 0;
bool mayCut = false;
byte decode[MAX_PACKET_SIZE];

void readSerial()
{
	while(SERIAL_PC.available())
	{
		byte b = SERIAL_PC.read();

		if(mayCut)
		{
			if(b == (byte)~PACKET_ESCAPE)
			{
				flashLed();
				queue_enqueue_content(from_serial, decode, ep);
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
	pinMode(LED_BUILTIN, OUTPUT);

	from_bus = new_queue();
	from_serial = new_queue();

	USART_Init();
	
	
	SERIAL_PC.begin(BAUD);
	
	init_bus();
	sei();
}

void loop()
{
	ub_manage();
	readSerial();
	relaySerial();
}


