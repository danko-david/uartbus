//TODO if you miss this file call "./scripts/get_c_deps.sh"
#include "../../../WD/toe/src/c/utils.c"

#include "lazyctest.h"
#include "rpc.h"
#include "../bootloader/ubh.h"


#define APP_START_ADDRESS 0x2000
#define BUS_ADDRESS 12
#define BAUD_RATE 115200

#define EXTERNAL_SEND_PACKET_PRIV

#include "../bootloader/ub_bootloader.c"


/****************************** test implementation ***************************/

bool has_app;

bool ubh_impl_has_app()
{
	return has_app;
}

uint8_t* address_space;

void ubh_impl_init()
{
	address_space = malloc(64*1024);
	memset(address_space, 0, 64*1024);
}

uint32_t last_write_page;
uint8_t* last_write_buffer;
uint8_t last_write_size;

void ubh_impl_write_program_page(const uint32_t page, const  uint8_t *buf,const uint8_t size)
{
	last_write_page = page;
	last_write_buffer = buf;
	last_write_size = size;

	memcpy((void*) (address_space+page), buf, size);
}

uint8_t ubh_impl_do_send_byte(struct uartbus* bus, uint8_t val)
{
	//USART_SendByte(val);
	return 0;
}

void ubh_impl_enable_receive_detect_interrupt(bool enable)
{
}


bool user_led;

uint8_t ubh_impl_set_user_led(uint8_t val)
{	
	switch(val)
	{
		case 0: user_led = false; break;
		case 1: user_led = true; break;
		case 2: user_led = !user_led; break;
	}
	return user_led?1:0;
}

void ubh_impl_wdt_start(bool longPeriod)
{
}

void ubh_impl_wdt_checkpoint()
{
}


uint8_t ubh_impl_read_code(uint16_t address, uint8_t length, uint8_t* buff)
{
	uint8_t i;
	for(i = 0;i<length;++i)
	{
		buff[i] = address_space[address+i];
	}
	
	return length;
}

#define SPM_PAGESIZE 128

uint8_t ubh_impl_get_program_page_size()
{
	return SPM_PAGESIZE;
}

uint8_t* ubh_impl_go_upload_and_allocate_program_tmp_storage()
{
	//TODO disable all unrelated ISRs
	//ok at this point i realized it is a fragile and messy solution to
	//collect all the Interrupt flags and reset them (with making special care
	//eg PCICR input masking). I don't do, because:
	//1) Collecting is a manual thing to do, and can be difficult, especially
	//	with point 2.
	//2) Fragile, because if you miss one interrupt or later you just use this
	// code with other microcontrollers with an interrupt not managed here, you
	// might face with random restart during try to upload a new code after
	// using this unlisted interrupt.
	//
	// The solution lies on the other side: before we stating to upload a new
	// code. First we reset and grab the device to not enter into the
	// application mode. If we do so, no interrupt will be enabled except the
	// defaults the the UHB uses, exactly what we need.

	return (uint8_t*) malloc(SPM_PAGESIZE);//(uint8_t*) malloc(SPM_PAGESIZE);
}

uint8_t power_state;

uint8_t ubh_impl_get_power_state()
{
	return power_state;
}


void ubh_impl_call_app(bool first)
{
}

/*extern volatile bool received;
extern volatile uint8_t* received_data;
extern volatile uint8_t received_ep;
*/

volatile int16_t last_sent_to;
volatile uint8_t last_sent_NS;
volatile uint8_t* last_sent_data;
volatile uint8_t last_sent_size;
volatile bool may_receive_new_packet;

volatile uint8_t tmp_sent_data[MAX_PACKET_SIZE];

bool send_packet_priv(int16_t to, uint8_t NS, uint8_t* data, uint8_t size)
{
	TEST_ASSERT_TRUE(may_receive_new_packet);
	last_sent_to = to;
	last_sent_NS = NS;
	memcpy(tmp_sent_data, data, size);
	last_sent_data = tmp_sent_data;

	last_sent_size = size;
}

uint32_t micros()
{
	struct timeval tv;
	gettimeofday(&tv, NULL);
	return 1000000 * tv.tv_sec + tv.tv_usec;
}

void ub_init_infrastructure(){}

void reset_last_received()
{
	may_receive_new_packet = true;
	last_sent_to = 0;
	last_sent_NS = 0;
	last_sent_data = NULL;
	last_sent_size = 0;
}

void assert_response(uint8_t len, ...)
{
	TEST_ASSERT_NOT_NULL(last_sent_data);
	TEST_ASSERT(len > 1);
	
	uint8_t expected[len];
	
	va_list v;
	va_start(v, len);
	for(uint8_t i=0;i<len;++i)
	{
		expected[i] = (uint8_t) va_arg(v, int);
	}
	va_end(v);
	
	char result[1024];
	char* sb = result;
	int sbl = 1024;
	
	bool fail = false;
	if(last_sent_size +1 != len)
	{
		char tmp[128];
		sprintf(tmp, "Response length mismatch, Expected: %d, Actual: %d\n", len, last_sent_size+1);
		str_append(&sb, &sbl, tmp);
		fail = true;
	}
	else
	{
		if(last_sent_NS != expected[0])
		{
			fail = true;
		}
		else
		{
			for(int i=1;i<len;++i)
			{
				if(expected[i] != last_sent_data[i-1])
				{
					fail = true;
					break;
				}
			}
		}
	}
	
	if(fail)
	{
		char tmp[128];
		str_append(&sb, &sbl, "Packet response mismatch. Expected: ");
		for(int i=0;i<len;++i)
		{
			if(0 != i)
			{
				str_append(&sb, &sbl, ":");
			}
			
			sprintf(tmp, "%d", expected[i]);
			str_append(&sb, &sbl, tmp);
		}
		
		str_append(&sb, &sbl, ", Actual: ");
		sprintf(tmp, "%d", last_sent_NS);
		str_append(&sb, &sbl, tmp);
		
		for(int i=0;i<last_sent_size;++i)
		{
			sprintf(tmp, ":%d", last_sent_data[i]);
			str_append(&sb, &sbl, tmp);
		}
		
		str_append(&sb, &sbl, "\n");
	}
	
	int mm = len < last_sent_size?len:last_sent_size;
	
	if(fail)
	{
		lct_test_failed(__FILE__, __LINE__, result);
	}
}

//TODO
/*
	1: tester
	12: unit under test
*/
void send_ensure(uint8_t len, ...)
{
	TEST_ASSERT(len+3 < MAX_PACKET_SIZE);

	va_list v;
	va_start(v, len);
	received_data[0] = 12;
	received_data[1] = 1;
	
	for(uint8_t i=0;i<len;++i)
	{
		received_data[2+i] = (uint8_t) va_arg(v, int);
	}
	va_end(v);
	
	received_data[2+len] = crc8(received_data, 2+len);
	received_ep = 3+len;
	received = true;
	
	//clear last received packet
	reset_last_received();
	
	//do the dispatch on the injected packet in the host (bootloader) code.
	try_dispatch_received_packet();
	
	//try_dispatch... must succeed
	TEST_ASSERT_FALSE(received);
	
	TEST_ASSERT_EQUAL(0, received_ep);
	
	//check response arrived
	TEST_ASSERT_NOT_NULL(last_sent_data);
}

/******************************** Test cases **********************************/

void flashing_upload_code_16(uint32_t addr)
{
	send_ensure(21, 2,4,3, (addr >> 8)&0xff, addr&0xff, 1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16);
	addr += 16;
	assert_response(7,  0,2,4,3, 0, (addr >> 8) & 0xff, addr&0xff);
}

void check_code_space_pattern(uint32_t from, uint16_t size)
{
	//check the code space
	for(uint16_t addr = from;addr < from+size;++addr)
	{
		//printf("%d (%d):",address_space[addr], 1+(addr%16));
		TEST_ASSERT_EQUAL(1+(addr%16), address_space[addr]);
	}
}

void check_code_space_empty(uint32_t from, uint16_t size)
{
	for(uint16_t addr = from;addr < from+size;++addr)
	{
		TEST_ASSERT_EQUAL(0, address_space[addr]);
	}
}

void start_flashing()
{
	//go flashing stage: 2:4:1
	send_ensure(3,  2,4,1);
	assert_response(5,  0,2,4,1,0);
	
	TEST_ASSERT_EQUAL(1, flash_stage);
	
	//get next address and ensure it is 0x2000 aka :32:0
	send_ensure(3,  2,4,2);
	assert_response(6,  0,2,4,2, (APP_START_ADDRESS >> 8) & 0xff,0);
}

void do_commit_flash()
{
	//and now commit 2:4:
	send_ensure(3,  2,4,4);
	assert_response(5,  0,2,4,4,0);
}

void test_upload_app_1_page()
{
	ubh_impl_init();

	start_flashing();
	
	/*
	 * push code 7x 16 byte code to fill a page to go just before flushing a
	 * program page
	 */
	uint32_t next_address = APP_START_ADDRESS;
	for(int i=0;i<7;++i)
	{
		flashing_upload_code_16(next_address);
		next_address += 16;
	}

	//assert there's no page written yet
	TEST_ASSERT_EQUAL(0, last_write_page);

	/*
	 * Sending the last piece of code of this page.
	 * Code page write should happen now.
	 * */

	//flash write should happen now
	flashing_upload_code_16(next_address);
	next_address += 16;

	//check page wite happened
	TEST_ASSERT_EQUAL(0x2000, last_write_page);

	/**
	 * Reset that last write marker and write a half page of code, then commit.
	 * */

	//reset last write page
	last_write_page = 0;

	//write a half page
	for(int i=0;i<4;++i)
	{
		flashing_upload_code_16(next_address);
		next_address += 16;
	}

	do_commit_flash();

	//last half page write should be happened.
	//Checking the page start address and size
	TEST_ASSERT_EQUAL(0x2080, last_write_page);
	TEST_ASSERT_EQUAL(64, last_write_size);

	check_code_space_pattern(0x2000, 12*16);
}


void test_upload_app_exactly_one_page()
{
	ubh_impl_init();
	start_flashing();

	uint32_t addr = APP_START_ADDRESS;
	//write code 0x2000 to 0x2c24
	for(int i=0;i<8;++i)
	{
		flashing_upload_code_16(addr);
		addr += 16;
	}

	TEST_ASSERT_EQUAL(0x2000, last_write_page);
	TEST_ASSERT_EQUAL(128, last_write_size);

	//reset last write log
	last_write_page = 0;
	last_write_size = 0;

	do_commit_flash();

	TEST_ASSERT_EQUAL(0, last_write_page);
	TEST_ASSERT_EQUAL(0, last_write_size);

	check_code_space_pattern(0x2000, 128);
}

/**
 * The bug i try to spot: I've uploaded a code with size 0xc24 (3108)
 * And the memory mismatched.
 *
 * Its a write between 0x2000 - 2C00 at this time the page starts with 2B80
 * 	is written.
 *
 * 	The last data between 0x2B80 and 0x2C24 is written when commit called
 * */
void test_upload_app_over_3Kb()
{
	ubh_impl_init();
	start_flashing();

	uint32_t addr = APP_START_ADDRESS;
	//write code 0x2000 to 0x2c24
	for(int i=0;i<194;++i)
	{
		flashing_upload_code_16(addr);
		addr += 16;
	}

	send_ensure(9, 2,4,3, (addr >> 8)&0xff, addr&0xff, 1,2,3,4);
	addr += 4;
	assert_response(7,  0,2,4,3, 0, (addr >> 8) & 0xff, addr&0xff);

	TEST_ASSERT_EQUAL(0x2B80, last_write_page);
	TEST_ASSERT_EQUAL(128, last_write_size);


	do_commit_flash();

	TEST_ASSERT_EQUAL(0x2C00, last_write_page);
	TEST_ASSERT_EQUAL(36, last_write_size);

	check_code_space_pattern(0x2000, 0xC24);
}


//TODO test pack unpack

