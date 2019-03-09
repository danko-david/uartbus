#define F_CPU 16000000

#define BAUD_RATE 115200
#define USART_BAUDRATE BAUD_RATE

#define BAUD USART_BAUDRATE
#define BAUD_PRESCALE (((F_CPU / (USART_BAUDRATE * 16UL))) - 1)

#include <avr/io.h>
#include <avr/boot.h>
#include <avr/pgmspace.h>
#include <util/delay.h>
#include <avr/interrupt.h>
#include <avr/wdt.h>
#include <stdlib.h>
#include <alloca.h>
#include "posix_errno.h"

#include "ub.h"
#include "rpc.h"

#define UCSZ1   2
#define UCSZ0   1

uint32_t micros();

/******************************* GLOBAL variables *****************************/

volatile bool app_run;
volatile bool sos_signal;
volatile uint8_t reset_flag;

/*
// not being used but here for completeness
// Wait until a byte has been received and return received data 
int16_t USART_ReceiveByte(uint16_t timeoutCycles)
{
	timeoutCycles |= 0x1;
	for(uint16_t i=0;i<timeoutCycles;++i)
	{
		if(UCSR0A & _BV(RXC0))
		{
			uint8_t ch = UDR0;
			//if((UCSR0A & _BV(FE0)))//TODO on frameing error
			//{
			//	return -1;
			//}
			
			return ch;
		}
	}
	return -1;
}
*/

/*
static int16_t ub_receive_byte(struct uartbus* bus)
{
	//OLD_TODO add timeout
	//OLD_TODO calculate timeout depending from the byte time
	return USART_ReceiveByte(1024);
}
*/

/***************************** USART functions ********************************/

void USART_Init(void)
{
	#ifdef __AVR_ATmega8__
	  UCSRA = _BV(U2X); //Double speed mode USART
	  UCSRB = _BV(RXEN) | _BV(TXEN) | _BV(RXCIE0);  // enable Rx & Tx
	  UCSRC = _BV(URSEL) | _BV(UCSZ1) | _BV(UCSZ0);  // config USART; 8N1
	  UBRRL = (uint8_t)( (F_CPU + BAUD_RATE * 4L) / (BAUD_RATE * 8L) - 1 );
	#else
	  UCSR0A = _BV(U2X0); //Double speed mode USART0
	  UCSR0B = _BV(RXEN0) | _BV(TXEN0) | _BV(RXCIE0);
	  UCSR0C = _BV(UCSZ00) | _BV(UCSZ01);
	  UBRR0L = (uint8_t)( (F_CPU + BAUD_RATE * 4L) / (BAUD_RATE * 8L) - 1 );
	#endif
}

void USART_SendByte(uint8_t u8Data)
{
	// Wait until last byte has been transmitted
	while (!(UCSR0A & _BV(UDRE0))){}

	// Transmit data
	UDR0 = u8Data;
}

/******************* On board software upgrade  functionalities ***************/

//enter into upgrade mode (is in upgrade mode)

//get constants, required by the compiler whos send the code

//erase app

//supply code segment

//flush

//read programspace: addr len
//commit -> back to app mode
bool send_packet(int16_t to, uint8_t* data, uint16_t size);
bool send_packet_priv(int16_t to, uint8_t ns, uint8_t* data, uint8_t size);


int8_t pack_value(int16_t v, uint8_t* arr, int size)
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
//TODO watch out! this code is for little endian arch
int8_t unpack_value(int16_t* dst, uint8_t* arr, int size)
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

/*************************** RPC functions - Bus ******************************/

void rpc_bus_ping(struct rpc_request* req)
{
	PORTB ^= 0xff;
	il_reply(req, 0);
}

void* RPC_FUNCTIONS_BUS[] =
{
	(void*) 1,
	(void*) rpc_bus_ping
};

/****************************** RPC bootloader ********************************/

void rpc_bootloader_power_function(struct rpc_request* req)
{
	if(0 == (req->size - req->procPtr))
	{
		il_reply(req, 1, EINVAL);
		return;
	}
	
	switch(req->payload[req->procPtr])
	{
		case 0: wdt_enable(WDTO_15MS);while(1){}
		case 1: asm ("jmp 0x00");
		default: break;
	}
}

void rpc_bootloader_get_var(struct rpc_request* req)
{
	if(0 == (req->size - req->procPtr))
	{
		il_reply(req, 1, EINVAL);
		return;
	}
	
	uint8_t res;
	switch(req->payload[req->procPtr])
	{
		case 0: res = app_run; break;
		case 1: res = sos_signal; break;
		case 2: res = reset_flag; break;
		default: res = 0;break;
	}
	il_reply(req, 1, res);
}

void rpc_bootloader_set_var(struct rpc_request* req)
{
	if((req->size - req->procPtr) < 2)
	{
		il_reply(req, 1, EINVAL);
		return;
	}
	
	uint8_t val = req->payload[req->procPtr+1];
	switch(req->payload[req->procPtr])
	{
		case 0: app_run = (bool) val; break;
		case 1: sos_signal =  (bool) val; break;
//		case 2: reset_flag = data[2]; break;
		default: break;
	}
	il_reply(req, 1, 0);
}

void rpc_bootloader_read_code(struct rpc_request* req)
{
	uint8_t* data = req->payload+ req->procPtr;
	uint16_t base = data[0] << 8 | data[1];
	uint8_t s = (uint8_t) data[2];
	if(s > 32)
	{
		s = 32;
	}
	
	uint8_t* rec = (uint8_t*) alloca(s+2);

	rec[0] = data[0];
	rec[1] = data[1];

	for(uint8_t i = 0;i<s;++i)
	{
		rec[i+2] = pgm_read_byte(base+i);
	}

	il_reply_arr(req, rec, s+2);
}

/***************************** Flashing functions *****************************/

uint8_t flash_stage = 0;

//when it's initailized, always must point to the start of a page boundry.
uint16_t flash_crnt_address = 0;
uint8_t* flash_tmp = NULL;

void blf_get_flash_stage(struct rpc_request* req)
{
	il_reply(req, 1, flash_stage);
}

void blf_start_flash(struct rpc_request* req)
{
	if(1 == flash_stage)
	{
		il_reply(req, 1, EALREADY);		
	}
	
	flash_tmp = (uint8_t*) malloc(SPM_PAGESIZE);
	if(NULL == flash_tmp)
	{
		il_reply(req, 1, ENOMEM);
		return;
	}
	
	app_run = false;
	sos_signal = false;
	flash_crnt_address = (uint16_t) APP_START_ADDRESS;
	flash_stage = 1;

	il_reply(req, 1, 0);
}

void blf_get_next_addr(struct rpc_request* req)
{
	il_reply(req, 2, ((flash_crnt_address >> 8) & 0xff), flash_crnt_address & 0xff); 
}

__attribute__((section(".bootloader"))) void bootloader_main()
{
	//return;
	//__do_global_ctors();
//	asm ("call 0x9c");
//	main();
	asm ("jmp 0x0");
}

__attribute__((section(".bootloader")))  void boot_program_page(uint32_t page, uint8_t *buf, uint8_t size)
{
    uint16_t i;
    uint8_t sreg;
    // Disable interrupts.
    sreg = SREG;
    cli();
    eeprom_busy_wait ();
    boot_page_erase (page);
    boot_spm_busy_wait ();      // Wait until the memory is erased.
    for (i=0; i<SPM_PAGESIZE; i+=2)
    {
        // Set up little-endian word.
        uint16_t w = *buf++;
        w += (*buf++) << 8;
    
        boot_page_fill (page + i, w);
    }
    boot_page_write (page);     // Store buffer in flash page.
    boot_spm_busy_wait();       // Wait until the memory is written.
    // Reenable RWW-section again. We need this if we want to jump back
    // to the application after bootloading.
    boot_rww_enable ();
    // Re-enable interrupts (if they were ever enabled).
    SREG = sreg;
}

void fill_flash(uint8_t *buf, uint16_t size)
{
	for(uint16_t i = 0;i<size;++i)
	{
		//filling flash temporary write storage from the given buffer.
		flash_tmp[flash_crnt_address % (SPM_PAGESIZE-1)] = buf[i];
		++flash_crnt_address;
		
		//if page fullfilled => flush it.		
		if(0 == flash_crnt_address % (SPM_PAGESIZE-1))
		{
			boot_program_page
			(
				(flash_crnt_address-1) & ~(SPM_PAGESIZE-1),
				flash_tmp,
				SPM_PAGESIZE
			);
		}
		
		//buffer is bigger than the native page size => offsetting iteration
		if(i == SPM_PAGESIZE)
		{
			i = 0;
			size -= SPM_PAGESIZE;
		}
	}
}

void blf_push_code(struct rpc_request* req)
{
	uint8_t* data = req->payload + req->procPtr;
	uint8_t size = req->size - req->procPtr;
	if(0 == flash_stage)
	{
		il_reply(req, 1, ENOTCONN);
	}
	
	if(size < 4)
	{
		il_reply(req, 1, EBADMSG);
		return;
	}
	
	if(data[0] != ((flash_crnt_address >> 8) & 0xff) || data[1] != (flash_crnt_address & 0xff))
	{
		il_reply(req, 1, ENXIO);
		return;
	}
	
	fill_flash(data, size);
	/*for(int i=0;i<size;++i)
	{
		flash_tmp[i] = data[i];
	}
	flash_crnt_address += size;
	*/
	
	il_reply(req, 3, 0, (flash_crnt_address >> 8) & 0xff, flash_crnt_address & 0xff);
}

void commit_flash(struct rpc_request* req)
{
	if(0 == flash_stage)
	{
		il_reply(req, 1, EBADFD);
	}
	
	//something filled into the write buffer => writing page
	if(0 != flash_crnt_address % (SPM_PAGESIZE-1))
	{
		boot_program_page
		(
			flash_crnt_address & ~(SPM_PAGESIZE-1),
			flash_tmp,
			flash_crnt_address % (SPM_PAGESIZE-1)
		);
	}
	
	free(flash_tmp);
	
	flash_stage = 0;
	
	il_reply(req, 1, 0);
}

void* BOOTLOADER_FLASH_FUNCTIONS[] =
{
	(void*) 5,
	(void*) blf_get_flash_stage,
	(void*) blf_start_flash,
	(void*) blf_get_next_addr,
	(void*) blf_push_code,
	(void*) commit_flash
};

void rpc_bootloader_flash(struct rpc_request* req)
{
	dispatch_function_chain(BOOTLOADER_FLASH_FUNCTIONS, req);
}

void* RPC_FUNCTIONS_BOOTLOADER[] =
{
	(void*) 5,
	(void*) rpc_bootloader_power_function,
	(void*) rpc_bootloader_get_var,
	(void*) rpc_bootloader_set_var,
	(void*) rpc_bootloader_read_code,
	(void*) rpc_bootloader_flash,
};

/************************ On board debug functionalities **********************/

void* RPC_FUNCTIONS_DEBUG[] =
{
	(void*) 0,
};

/********************* On board transaction functionalities *******************/

void* RPC_FUNCTIONS_TRANSACTION[] =
{
	(void*) 0,
};

/************************ On board debug functionalities **********************/

void* RPC_FUNCTIONS_TRANSMISSION[] =
{
	(void*) 0,
};

/************************* RPC namespace  dispatch ****************************/

void* RPC_NS_FUNCTIONS[] =
{
	(void*) 6,
	NULL,
	RPC_FUNCTIONS_BUS,
	RPC_FUNCTIONS_BOOTLOADER,
	RPC_FUNCTIONS_DEBUG,
	RPC_FUNCTIONS_TRANSACTION,//transaction management
	RPC_FUNCTIONS_TRANSMISSION

};

void dispatch_root(struct rpc_request* req)
{
	dispatch_descriptor_chain(RPC_NS_FUNCTIONS, req);
}

/************************ UARTBus application code ****************************/


#define MAX_PACKET_SIZE 48

struct uartbus bus;
int received_ep;
uint8_t received_data[MAX_PACKET_SIZE];

uint8_t send_size;
uint8_t send_data[MAX_PACKET_SIZE];

bool bus_active = false;

void (*app_dispatch)(struct rpc_request* req) = NULL;

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


bool send_packet_priv(int16_t to, uint8_t NS, uint8_t* data, uint8_t size)
{
	if(0 != send_size || size >= MAX_PACKET_SIZE-6)
	{
		return false;
	}
	
	uint8_t ep = 0;
	int8_t add;
	
	if((add = pack_value(to, send_data, MAX_PACKET_SIZE)) < 1)
	{
		return false;
	}
	
	ep += add;
		
	if((add = pack_value(BUS_ADDRESS, send_data+ep, MAX_PACKET_SIZE-ep)) < 1)
	{
		return false;
	}
	
	ep += add;
	
	/*send_data[0] = (uint8_t) ((to >> 8) & 0xff);
	send_data[1] = (uint8_t) (to & 0xff);
	send_data[2] = (uint8_t) ((BUS_ADDRESS >> 8) & 0xff);
	send_data[3] = (uint8_t) (BUS_ADDRESS & 0xff);
	send_data[4] = NS;*/
	send_data[ep] = NS;
	++ep;
	
	size = 0;
	
	for(int i=0;i<size;++i)
	{
		send_data[ep+i] = data[i];
	}
	send_data[ep+size] = crc8(send_data, ep+size);
	send_size = size+ep+1;
	
	return true;
}

void dispatch(struct rpc_request* req)
{
	if(0 == req->size)
	{
		return;
	}
	
	int ns = req->payload[req->procPtr];
	
	if(0 < ns && ns < 32)
	{
		dispatch_root(req);
	}
	else if(NULL != app_dispatch && 32 == ns)
	{
		++req->procPtr;
		app_dispatch(req);
	}
	else if(ns == 33)
	{
		send_packet_priv(req->from, 33, req->payload+1, req->size-1);
	}
/*	else if(ns == 34)
	{
		rpc_bootloader_read_code(from, data, size, 1);
	}
	else if(ns == 35)
	{
		switch(data[0])
		{
			case 0: il_reply(from, data, 1, 1, arr_size(RPC_NS_FUNCTIONS)); break;
			case 1: il_reply(from, data, 1, 1, arr_size(RPC_FUNCTIONS_BOOTLOADER)); break;
//			case 2: il_reply(from, data, -1, 1, 0); break;
		}
	}
*/
}

static void ub_event(struct uartbus* a, enum uartbus_event event)
{
	if(ub_event_receive_end == event)
	{
		if(0 == received_ep)
		{
			return;
		}

		//is the packet flawless?
		if(crc8(received_data, received_ep-1) == received_data[received_ep-1])
		{
			//is size is acceptable?
			if(received_ep > 3)
			{
				uint8_t ep = 0;
				int8_t add = 0;
				
				//TODO rewrite to variable size addresses;
				struct rpc_request req;
				if((add = unpack_value(&req.to, received_data, received_ep-1)) < 1)
				{
					return;
				}
				
				ep += add;
				
				if((add = unpack_value(&req.from, received_data+ep, received_ep-1-ep)) < 1)
				{
					return;
				}
				
				ep += add;
				
				req.payload = received_data+ep;
				req.size = received_ep-ep-1;
				req.procPtr = 0;
				
				//is we are the target, or group/broadcast?
				if(req.to < 1 || BUS_ADDRESS == req.to)
				{
					dispatch(&req);
				}
			}
		}
		
		received_ep = 0;
	}
}

static uint8_t ub_do_send_byte(struct uartbus* bus, uint8_t val)
{
	USART_SendByte(val);
	return 0;
}

static uint8_t send_on_idle(struct uartbus* bus, uint8_t** data, uint16_t* size)
{
	if(send_size != 0)
	{
		*data = send_data;
		*size = send_size;
		send_size = 0;
		return 0;
	}
	return 1;
}

bool send_packet(int16_t to, uint8_t* data, uint16_t size)
{
	return send_packet_priv(to, 16, data, size);
}

bool may_send_packet()
{
	return 0 == send_size;
}

uint8_t get_max_packet_size()
{
	return MAX_PACKET_SIZE;
}

void init_bus()
{
	received_ep = 0;

	bus.serial_byte_received = ub_rec_byte;
	bus.serial_event = ub_event;
	bus.byte_time_us = static_ub_calc_baud_cycle_time(BAUD_RATE);
	bus.bus_idle_time = static_ub_calc_timeout(BAUD_RATE, 2);
	bus.do_send_byte = ub_do_send_byte;
//	bus.do_receive_byte = ub_receive_byte;
	bus.cfg = 0
		|	ub_cfg_fairwait_after_send_high
//		|	ub_cfg_fairwait_after_send_low
		|	ub_cfg_read_with_interrupt
	;
	ub_init(&bus);
	bus_active = true;
}

ISR(USART_RX_vect)
{
	ub_out_rec_byte(&bus, UDR0);
}

void register_packet_dispatch(void (*addr)(struct rpc_request* req))
{
	app_dispatch = addr;
}

/*************************** Host software utilities **************************/

int init_board(void)
{
	ub_init_infrastructure();
	USART_Init();
	init_bus();
}

//https://stackoverflow.com/questions/6686675/gcc-macro-expansion-arguments-inside-string
#define S(x) #x
#define SX(x) S(x)

__attribute__((noinline)) void call_app()
{
	asm("call " SX(APP_START_ADDRESS));
}

bool has_app()
{
	return 0xff != pgm_read_byte(APP_START_ADDRESS);
}

/********************************** Host tables *******************************/

//constants, function pointers

//bus address
//app start address [default] = 4096

void** HOST_FUNCTIONS = (void*[])
{
	(void*) register_packet_dispatch,
	(void*) may_send_packet,
	(void*) send_packet,
	(void*) get_max_packet_size
};

void** HOST_CONSTANTS = (void*[])
{
	(void*) UB_HOST_VERSION,
	(void*) BUS_ADDRESS,
	(void*) HOST_TABLE_ADDRESS,
	(void*) APP_START_ADDRESS,
//	(void*) APP_END_ADDRESS,
	(void*) APP_CHECKSUM
};

//TODO provide mode feature: malloc/free, micros, bus_manage (externalised bus call) 
//dispatch for the upper namespace over 32
void** HOST_TABLE[] =
{
	(void**) HOST_FUNCTIONS,
	(void**) HOST_CONSTANTS
};

__attribute__((section(".host_table"))) void*** getHostTable()
{
	return HOST_TABLE;
} 

/********************************** Host Main *********************************/

bool afterMicro(uint32_t* last_time, uint32_t timeMicro)
{
	if(*last_time + timeMicro < micros())
	{
		*last_time = micros();
		return true;
	}
	return false;
}

uint32_t last_panic_signal_time = 0;

void busSignalOnline(uint8_t powerOnMode, uint8_t softMode)
{
	uint8_t p[2];
	p[0] = powerOnMode;
	p[1] = softMode;
	send_packet_priv(-1, 0, (uint8_t*) p, sizeof(p));
}


//boolean bit
#define bb(x, y) x?0x1 <<y:0

int main()
{
	DDRB = 0xFF; //DEBUG
	reset_flag = MCUSR;
	//watchdog reset and not external or power-on 
	bool wdt_reset = MCUSR == 8;
	
	if(wdt_reset)
	{
		app_run = false;
		sos_signal = true;
	}
	else
	{
		app_run = true;
		sos_signal = false;
	}
	
	MCUSR = 0;
	
	has_app();
	init_board();
	has_app();
	
	bool app_deployed = has_app();
	busSignalOnline
	(
		reset_flag,
			bb(sos_signal, 2)
		|
			bb(app_run, 1)
		|
			bb(app_deployed, 0)
	);
	ub_manage_connection(&bus, send_on_idle);
	
	//wait a little bit, we might get some instruction from the bus before
	//entering application mode
	
	wdt_enable(WDTO_1S);
	while(1)
	{
		wdt_reset();
		ub_manage_connection(&bus, send_on_idle);
		

		wdt_reset();
		if(app_run && has_app())
		{
			call_app();
		}
		
		if(sos_signal)
		{
			//blinkSos();//TODO sequence
			if(afterMicro(&last_panic_signal_time, 2000000))
			{
				send_packet(-1, (uint8_t*) "WDT restart", 11);
			}
		}
	}
}


