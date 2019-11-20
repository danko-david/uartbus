
#include "addr16.h"

int8_t ub_pack_16_value(int16_t v, uint8_t* arr, int size)
{
	if(0 == size)
	{
		return -EMSGSIZE;
	}

	bool neg = v < 0;
	if(neg)
	{
		v = -(v+1);
	}

	//ensure data in big endian
//	if(O32_HOST_ORDER == O32_LITTLE_ENDIAN)
//	{
//		v = ((v&0xff00)>>8) | ((v&0xff)<<8);
//	}

	arr[0] = neg?0x40:0x00;

	if(v <= 63)//1 byte
	{
		arr[0] |= v & 0x3f;
		return 1;
	}
	else if(v <= 8191)//2 byte
	{
		//TODO check size
		arr[1] = v & 0x7f;
		arr[0] |= 0x3f & (v >> 7);
		return 2;
	}

		//TODO check size
	//else if(v <= 16384)//3 byte
	arr[2] = v & 0x7f;
	arr[1] = ((v >> 7) & 0xff) | 0x80;
	arr[0] |= (0x3f & (v >> 14)) | 0x80;
	return 3;

}

//-8192 - 8191 //we might use one more bit from the beginning to double the
//available adresses and max out the int16_t type
int8_t ub_unpack_16_value(int16_t* dst, uint8_t* arr, int size)
{
	int16_t value = 0;
	int req = 1;
	for(int i=0;i<size;++i)
	{
		if(arr[i] & 0x80)
		{
			++req;
			if(i == size-1)
			{
				return -ELNRNG;//truncated value
			}
		}
		else
		{
			break;
		}

		if(req > 2)
		{
			return -EMSGSIZE;//too long value for int16_t
		}
	}

	//check value remains in buffer a 3 byte might fit to int16_t
	if(req == 3)
	{
		if(arr[1] & 0b0011100)
		{
			return -EOVERFLOW;//value overflow
		}

		value = (0b00111111 & arr[0]) << 14 | (0b01111111 & arr[1]) << 7 | arr[2];
	}
	else if(req == 2)
	{
		value = (0b00111111 & arr[0]) << 7 | arr[1];
	}
	else
	{
		value = 0b00111111 & arr[0];
	}

	//ensure data in the host's endian
//	if(O32_HOST_ORDER == O32_LITTLE_ENDIAN)
//	{
//		value = (value>>8) | (value<<8);
//	}

	if(arr[0] & 0x40)
	{
		value = -(value+1);
	}

	*dst = value;

	return req;
}
