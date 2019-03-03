/***
 * Reqired macro definitions:
 *	- BAUD (19200, 115200 etc)
 * 	- PC_SERIAL{_SOFT,0,1,2,3}
 *	- BUS_SERIAL {_SOFT,0,1,2,3}
 *		1,2,3 serial are avilable only in mega
 */

#include "ub_arduino.h"

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



#ifndef MAX_PACKET_SIZE
	#define MAX_PACKET_SIZE 64
#endif

#ifndef PACKET_ESCAPE
	#define PACKET_ESCAPE 0xff
#endif

struct uartbus_arduino BUS;

byte encode[MAX_PACKET_SIZE*2+2];

void flashLed()
{
	digitalWrite(NET_TRAFFIC_LED, !digitalRead(NET_TRAFFIC_LED));
}

void relaySerial(uint8_t* data, uint8_t size)
{
	uint8_t ep = 0;
	
	for(uint8_t i=0;i<size;++i)
	{
		if(data[i] == PACKET_ESCAPE)
		{
			encode[ep++] = PACKET_ESCAPE;
		}
		encode[ep++] = data[i];
	}
	
	encode[ep++] = PACKET_ESCAPE;
	encode[ep++] = ~PACKET_ESCAPE;
	
	flashLed();
	SERIAL_PC.write((uint8_t*) encode, (uint8_t) ep);
}

void onPacketReceived(struct uartbus_arduino* bus, uint8_t* data, uint16_t size)
{
	if(size > 0)
	{
		relaySerial(data, size);
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
				arduino_ub_send_packet(&BUS, decode, ep);
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
	
	SERIAL_PC.begin(BAUD);
	SERIAL_BUS.begin(BAUD);
	ub_init_infrastructure();
	arduino_ub_init(&BUS, &SERIAL_BUS, BAUD, MAX_PACKET_SIZE, onPacketReceived, NULL);
}

void loop()
{
	arduino_ub_manage_bus(&BUS);
	readSerial();
}


