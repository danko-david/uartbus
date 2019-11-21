#include "ub_arduino.h"

void onPacketReceived(UartBus& bus, uint8_t* data, uint16_t size)
{

}

UartBus ub;

void serialEvent()
{
	ub.processIncomingStream();
}

void setup()
{
	ub.init(Serial, 115200, 48, onPacketReceived);
}

void loop()
{
	ub.manage();
}

