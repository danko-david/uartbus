
#ifndef _UB_ARDUINO_H_
#define _UB_ARDUINO_H_

#ifdef ARDUINO

#include "ub.h"
#include "addr16.h"

#ifdef ARDUINO
	#if ARDUINO >= 100
		#include "Arduino.h"
	#else
		#include "WProgram.h"
	#endif
#endif

class UartBus
{
public:
	struct uartbus bus;
	uint8_t* receive_data;
	uint8_t receive_ep;
	uint8_t receive_size;
	Stream* stream;
	uint8_t* send_data;
	uint8_t send_size;
	void (*packet_received)(UartBus& bus, uint8_t* data, uint16_t size);
	void* user_data;

	//disable copy constructor
	UartBus(const UartBus&) = delete;

	UartBus();

	void init
	(
		Stream& stream,
		uint32_t baudRate,
		uint8_t maxPacketSize,
		void (*onPacketReceived)(UartBus& bus, uint8_t* data, uint16_t size)
	);

	void init
	(
		HardwareSerial& stream,
		uint32_t baudRate,
		uint8_t maxPacketSize,
		void (*onPacketReceived)(UartBus& bus, uint8_t* data, uint16_t size)
	);

	void intFeedByte(uint8_t val);
	void processIncomingStream();
	Stream* getStream();
	int manage();
	int sendRawPacket(uint8_t* data, uint8_t size);
	int sendCrc8Packet(uint8_t* data, uint8_t size);
	//int sendTo(int16_t to, uint8_t* data, uint8_t size);
};

#endif
#endif
