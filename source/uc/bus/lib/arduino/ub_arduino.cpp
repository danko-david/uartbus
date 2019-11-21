
#include <ub_arduino.h>

#ifdef ARDUINO

static void arduino_ub_rec_byte(struct uartbus* bus, uint8_t data_byte)
{
	UartBus* a = (UartBus*) bus->user_data;

	if(a->receive_ep == a->receive_size)
	{
		//break the packet
		a->receive_ep = 0;
	}
	else
	{
		a->receive_data[a->receive_ep++] = data_byte;
	}
}

static void arduino_ub_event(struct uartbus* bus, enum uartbus_event event)
{
	if(ub_event_receive_end == event)
	{
		UartBus* a = (UartBus*) bus->user_data;
		a->packet_received
		(
			*a,
			a->receive_data,
			a->receive_ep
		);
		a->receive_ep = 0;
	}
}

static uint8_t arduino_ub_do_send_byte(struct uartbus* bus, uint8_t val)
{
	UartBus* a = (UartBus*) bus->user_data;
	a->stream->write(val);
	a->stream->flush();
	return 0;
}

static int16_t arduino_ub_receive_byte(struct uartbus* bus)
{
	UartBus* a = (UartBus*) bus->user_data;
	//while(0 == a->stream->available());
	return a->stream->read();
}

static uint8_t arduino_ub_send_on_idle(struct uartbus* bus, uint8_t** data, uint16_t* size)
{
	UartBus* a = (UartBus*) bus->user_data;
	if(a->send_size != 0)
	{
		*data = a->send_data;
		*size = a->send_size;
		a->send_size = 0;
		return 0;
	}
	return 1;
}

static uint8_t ub_random(struct uartbus* bus)
{
	return (uint8_t) random(0,255);
}

void UartBus::init
(
	Stream& stream,
	uint32_t baudRate,
	uint8_t maxPacketSize,
	void (*onPacketReceived)(UartBus& bus, uint8_t* data, uint16_t size)
)
{
	this->receive_size = maxPacketSize;
	this->receive_data = (uint8_t*) malloc(maxPacketSize);
	this->send_data = (uint8_t*) malloc(maxPacketSize);
	this->stream = &stream;
	this->send_size = 0;
	this->packet_received = onPacketReceived;
	bus.user_data = this;
	bus.current_usec = (uint32_t(*)(struct uartbus*)) micros;
	bus.rand = ub_random;

	bus.serial_byte_received = arduino_ub_rec_byte;
	bus.serial_event = arduino_ub_event;
	ub_init_baud(&bus, baudRate, 2);
	bus.do_send_byte = arduino_ub_do_send_byte;
	bus.do_receive_byte = arduino_ub_receive_byte;
	bus.cfg = 0
//		|	ub_cfg_fairwait_after_send_low
		|	ub_cfg_fairwait_after_send_high
	;
	ub_init(&bus);
}

void UartBus::init
(
	HardwareSerial& serial,
	uint32_t baudRate,
	uint8_t maxPacketSize,
	void (*onPacketReceived)(UartBus& bus, uint8_t* data, uint16_t size)
)
{
	init((Stream&)serial, baudRate, maxPacketSize, onPacketReceived);
	serial.begin(baudRate);
}

UartBus::UartBus(){}

void UartBus::intFeedByte(uint8_t val)
{
	ub_out_rec_byte(&this->bus, val);
}

int UartBus::manage()
{
	return ub_manage_connection(&this->bus, arduino_ub_send_on_idle);
}

Stream* UartBus::getStream()
{
	return this->stream;
}

int UartBus::sendRawPacket(uint8_t* data, uint8_t size)
{
	if(size > this->receive_size)
	{
		return -1;
	}

	if(0 != this->send_size)
	{
		return 1;
	}

	for(uint8_t i=0;i<size;++i)
	{
		this->send_data[i] = data[i];
	}
	this->send_size = size;
	return 0;
}

/*
int UartBus::sendTo(int16_t to, uint8_t* data, uint8_t size)
{
	uint8_t buf[size+4];
	uint8_t ep = 0;

	uint8_t add;
	if((add = ub_pack_16_value(to, buf, this->receive_size)) < 1)
	{
		return -10;
	}

	ep += add;

	if((add = ub_pack_16_value(address, buf+ep, this->receive_size)) < 1)
	{
		return -11;
	}

	ep += add;

	for(uint8_t i=0;i<size;++i)
	{
		buf[ep+i] = data[i];
	}

	ep += add;

	buf[ep] = crc8(buf, ep);
	++ep;

	return this->sendRawPacket(buf, size);
}
*/
void UartBus::processIncomingStream()
{
	Stream* ser = this->stream;
	while(ser->available())
	{
		ub_out_rec_byte(&this->bus, ser->read());
	}
}

#endif
