#include <AltSoftSerial.h>

#include "ub_arduino.h"

#define NET_TRAFFIC_LED 13

//#define rxPin 8
//#define txPin 9

AltSoftSerial softSerial;//(rxPin, txPin);
#define BAUD 19200
#define SERIAL_PC softSerial
//#define SERIAL_PC Serial2
//#define SERIAL_BUS Serial3
#define SERIAL_BUS Serial

#define MAX_PACKET_SIZE 64
#define PACKET_ESCAPE 0xff

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
												//Serial2.write(decode, ep);
												//relaySerial(decode, ep);
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
	//pinMode(rxPin, INPUT);
	//pinMode(txPin, OUTPUT);
	
	SERIAL_PC.begin(BAUD);
	SERIAL_BUS.begin(BAUD);
	ub_init_infrastructure();
	arduino_ub_init(&BUS, &SERIAL_BUS, 115200, MAX_PACKET_SIZE, onPacketReceived, NULL);
}

void loop()
{
	//SERIAL_PC.println("ASD");
	//delay(500);
	arduino_ub_manage_bus(&BUS);
	readSerial();
}


