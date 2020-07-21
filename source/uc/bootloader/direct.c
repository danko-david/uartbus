
#include "ubh.h"

#ifndef PACKET_ESCAPE
	#define PACKET_ESCAPE (uint8_t) 0xff
#endif

void try_dispatch_received_packet();
void USART_SendByte(uint8_t u8Data);
void ub_init_infrastructure();

//Exact duplication of crc8 from `ub.c` (we want to exclude uartbus library but
//this very single function is required to imitate we are a single node bus)

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


//fake uartbus strucure and `bus` varaible
struct uartbus{} bus;

uint8_t rando()
{
	return 0;
}

void init_bus()
{
	ub_init_infrastructure();
};

//copied and modified from uartbus_connector.cpp

#include <avr/io.h>


bool mayCut = false;
/*
if(received_ep == MAX_PACKET_SIZE)
	{
		//brake the package manually
		received_ep = 0;
	}
	else
	{
		received_data[received_ep++] = data_byte;
	}
*/
void ub_out_rec_byte(struct uartbus* bus, uint16_t data)
{
	if(data < 0 || data > 255)
	{
		return;
	}
	
	//if we have a pending packet
	if(received)
	{
		return;
	}
	
	if(received_ep >= MAX_PACKET_SIZE)
	{
		received_ep = 0;//break too the long packet
	}
	
	uint8_t b = data;

	if(mayCut)
	{
		if(b == (uint8_t)~PACKET_ESCAPE)
		{
			//new packet received
			received = true;
		}
		else
		{
			received_data[received_ep++] = b;
		}
		mayCut = false;
	}
	else
	{
		if(b == (uint8_t)PACKET_ESCAPE)
		{
			mayCut = true;
		}
		else
		{
			received_data[received_ep++] = b;
		}
	}
}

void try_send_packet()
{
	if(send_size != 0)
	{
		for(uint8_t i=0;i<send_size;++i)
		{
			uint8_t val = send_data[i];
			if(PACKET_ESCAPE == val)
			{
				USART_SendByte((uint8_t)PACKET_ESCAPE);
			}
			USART_SendByte(val);
		}
		USART_SendByte((uint8_t)PACKET_ESCAPE);
		USART_SendByte((uint8_t)~PACKET_ESCAPE);
		
		send_size = 0;
	}
}


void ubh_manage_bus()
{
	try_send_packet();
	try_dispatch_received_packet();
}
