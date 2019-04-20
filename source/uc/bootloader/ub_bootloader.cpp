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

#ifdef __linux__
	#include <sys/time.h>
	uint32_t ub_impl_get_current_usec()
	{
		struct timeval tv;
		struct timezone tz;
		int stat = gettimeofday(&tv, &tz);
		uint32_t now =
		//	(tv.tv_sec*1000 + tv.tv_usec);//% ((uint32_t) ~0);
			tv.tv_usec;
		//printf("now: %d\n", now);
		return now;
	}
	//empty call
	void ub_init_infrastructure(){};
#else

	#ifndef  ARDUINO
		#include <avr/interrupt.h>
		#ifndef sbi
			#define sbi(sfr, bit) (_SFR_BYTE(sfr) |= _BV(bit))
		#endif
		#define clockCyclesPerMicrosecond() ( F_CPU / 1000000L )
		#define clockCyclesToMicroseconds(a) ( (a) / clockCyclesPerMicrosecond() )
		#define microsecondsToClockCycles(a) ( (a) * clockCyclesPerMicrosecond() )
		#define MICROSECONDS_PER_TIMER0_OVERFLOW (clockCyclesToMicroseconds(64 * 256))
		#define MILLIS_INC (MICROSECONDS_PER_TIMER0_OVERFLOW / 1000)
		#define FRACT_INC ((MICROSECONDS_PER_TIMER0_OVERFLOW % 1000) >> 3)
		#define FRACT_MAX (1000 >> 3)

		volatile unsigned long timer0_overflow_count = 0;
		volatile unsigned long timer0_millis = 0;
		static unsigned char timer0_fract = 0;

		#if defined(__AVR_ATtiny24__) || defined(__AVR_ATtiny44__) || defined(__AVR_ATtiny84__)
		ISR(TIM0_OVF_vect)
		#else
		ISR(TIMER0_OVF_vect)
		#endif
		{
			// copy these to local variables so they can be stored in registers
			// (volatile variables must be read from memory on every access)
			unsigned long m = timer0_millis;
			unsigned char f = timer0_fract;

			m += MILLIS_INC;
			f += FRACT_INC;
			if (f >= FRACT_MAX) {
				f -= FRACT_MAX;
				m += 1;
			}

			timer0_fract = f;
			timer0_millis = m;
			timer0_overflow_count++;
		}

		//avr
		//extracted from: /usr/share/arduino/hardware/arduino/cores/arduino/wiring.c
		uint32_t micros()
		{
			unsigned long m;
			uint8_t oldSREG = SREG, t;
	
			cli();
			m = timer0_overflow_count;
		#if defined(TCNT0)
			t = TCNT0;
		#elif defined(TCNT0L)
			t = TCNT0L;
		#else
			#error TIMER 0 not defined
		#endif

		  
		#ifdef TIFR0
			if ((TIFR0 & _BV(TOV0)) && (t < 255))
				m++;
		#else
			if ((TIFR & _BV(TOV0)) && (t < 255))
				m++;
		#endif

			SREG = oldSREG;
	
			return ((m << 8) + t) * (64 / clockCyclesPerMicrosecond());
		}

		void ub_init_infrastructure()
		{
			// this needs to be called before setup() or some functions won't
			// work there
			sei();

			// on the ATmega168, timer 0 is also used for fast hardware pwm
			// (using phase-correct PWM would mean that timer 0 overflowed half as often
			// resulting in different millis() behavior on the ATmega8 and ATmega168)
		#if defined(TCCR0A) && defined(WGM01)
			sbi(TCCR0A, WGM01);
			sbi(TCCR0A, WGM00);
		#endif  

			// set timer 0 prescale factor to 64
		#if defined(__AVR_ATmega128__)
			// CPU specific: different values for the ATmega128
			sbi(TCCR0, CS02);
		#elif defined(TCCR0) && defined(CS01) && defined(CS00)
			// this combination is for the standard atmega8
			sbi(TCCR0, CS01);
			sbi(TCCR0, CS00);
		#elif defined(TCCR0B) && defined(CS01) && defined(CS00)
			// this combination is for the standard 168/328/1280/2560
			sbi(TCCR0B, CS01);
			sbi(TCCR0B, CS00);
		#elif defined(TCCR0A) && defined(CS01) && defined(CS00)
			// this combination is for the __AVR_ATmega645__ series
			sbi(TCCR0A, CS01);
			sbi(TCCR0A, CS00);
		#else
			#error Timer 0 prescale factor 64 not set correctly
		#endif

			// enable timer 0 overflow interrupt
		#if defined(TIMSK) && defined(TOIE0)
			sbi(TIMSK, TOIE0);
		#elif defined(TIMSK0) && defined(TOIE0)
			sbi(TIMSK0, TOIE0);
		#else
			#error	Timer 0 overflow interrupt not set correctly
		#endif
			
		}
	#else
		void ub_init_infrastructure(){}
	#endif
	//arduino
	__attribute__((noinline))  uint32_t ub_impl_get_current_usec()
	{
		return micros();
	}
#endif

/******************************* GLOBAL variables *****************************/

volatile bool app_run;
volatile uint8_t reset_flag;

void ub_manage();

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
//	UCSR0A |= 1<<TXC0;
	//while (!(UCSR0A & _BV(UDRE0))){}
//	while((UCSR0A&(1<<UDRE0)) == 0);
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

int16_t rpc_response(struct rpc_request* req, uint8_t args, struct response_part** parts)
{
	int neg = req->procPtr-1;
	
	int size = rpc_append_size(args, parts);
	if(size < 0)
	{
		return size;
	}
	
	uint8_t* d = (uint8_t*) alloca(size+neg);
	
	for(uint8_t i=0;i<neg;++i)
	{
		d[i] = req->payload[i+1];
	}
	
	rpc_append_arr(d+neg, size, args, parts);
	
	return send_packet_priv(req->from, req->payload[0], d, size+neg);
}

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

void rpc_bus_replay(struct rpc_request* req)
{
	il_reply_arr(req, req->payload+req->procPtr, req->size - req->procPtr);
}

void* RPC_FUNCTIONS_BUS[] =
{
	(void*) 2,
	(void*) rpc_bus_ping,
	(void*) rpc_bus_replay
};

/****************************** RPC bootloader ********************************/
//2:0:x
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

//2:1:x
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
//		case 1: res = sos_signal; break;
		case 2: res = reset_flag; break;
		default: res = 0;break;
	}
	il_reply(req, 1, res);
}

//2:2:x
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
//		case 1: sos_signal =  (bool) val; break;
		case 2: reset_flag = (uint8_t) val; break;
		default: break;
	}
	il_reply(req, 1, 0);
}

//2:3:x
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
	il_reply(req, 2, 0, flash_stage);
}

void blf_start_flash(struct rpc_request* req)
{
	if(1 == flash_stage)
	{
		il_reply(req, 1, EALREADY);		
	}
	
	flash_tmp = (uint8_t*)RAMSTART;//(uint8_t*) malloc(SPM_PAGESIZE);
	if(NULL == flash_tmp)
	{
		il_reply(req, 1, ENOMEM);
		return;
	}
	
	app_run = false;
//	sos_signal = false;
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
		flash_tmp[flash_crnt_address % SPM_PAGESIZE] = buf[i];
		++flash_crnt_address;
		
		//if page fullfilled => flush it.		
		if(0 == flash_crnt_address % SPM_PAGESIZE)
		{
			boot_program_page
			(
				(flash_crnt_address-1) & ~(SPM_PAGESIZE-1),
				flash_tmp,
				SPM_PAGESIZE
			);
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
	
	if(size < 3)
	{
		il_reply(req, 1, EBADMSG);
		return;
	}
	
	if(data[0] != ((flash_crnt_address >> 8) & 0xff) || data[1] != (flash_crnt_address & 0xff))
	{
		il_reply(req, 1, ENXIO);
		return;
	}
	
	fill_flash(data+2, size-2);
	
	il_reply(req, 3, 0, (flash_crnt_address >> 8) & 0xff, flash_crnt_address & 0xff);
}

void commit_flash(struct rpc_request* req)
{
	if(0 == flash_stage)
	{
		il_reply(req, 1, EBADFD);
		return;
	}
	
	//something filled into the write buffer => writing page
	if(0 != ((flash_crnt_address-1) % (SPM_PAGESIZE-1)))
	{
		boot_program_page
		(
			flash_crnt_address & ~(SPM_PAGESIZE-1),
			flash_tmp,
			flash_crnt_address % (SPM_PAGESIZE-1)
		);
	}
	
	//free(flash_tmp);
	
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
	//get sram value
	//set sram value
	//free (available) mem
};

/********************* On board transaction functionalities *******************/

void* RPC_FUNCTIONS_TRANSACTION[] =
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
	//1 from 1 to 1 NS 1 CRC
	if(0 != send_size || size >= MAX_PACKET_SIZE-4)
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
	
	send_data[ep] = NS;
	++ep;
	
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
	
	if(req->from >= 0 && 0 < ns && ns < 32)
	{
		dispatch_root(req);
	}
	else if(NULL != app_dispatch && 32 <= ns)
	{
		app_dispatch(req);
	}
}

volatile bool received = false;

static void try_dispatch_received_packet()
{
	if(!received)
	{
		return;
	}
	
	received = false;

	//is the packet flawless?
	if(crc8(received_data, received_ep-1) == received_data[received_ep-1])
	{
		//is size is acceptable?
		if(received_ep > 3)
		{
			uint8_t ep = 0;
			int8_t add = 0;
			
			struct rpc_request req;
			req.reply = rpc_response;
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
			
			req.size = received_ep-ep-1;
			
			req.payload = (uint8_t*) alloca(req.size);// = received_data+ep;
			for(int i=0;i<req.size;++i)
			{
				req.payload[i] = received_data[ep+i];
			}
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

static void ub_event(struct uartbus* a, enum uartbus_event event)
{
	if(ub_event_receive_end == event)
	{
		if(0 == received_ep)
		{
			return;
		}
		
		received = true;
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

int8_t rando()
{
	return rand()%16;
}

void init_bus()
{
	received_ep = 0;

	bus.rand = rando;
	bus.currentUsec = micros;
	bus.serial_byte_received = ub_rec_byte;
	bus.serial_event = ub_event;
	ub_init_baud(&bus, BAUD_RATE, 3);
	bus.do_send_byte = ub_do_send_byte;
	bus.cfg = 0
		|	ub_cfg_fairwait_after_send_high
//		|	ub_cfg_fairwait_after_send_low
		|	ub_cfg_read_with_interrupt
	;
	ub_init(&bus);
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

__attribute__((noinline)) void call_app(bool first)
{
	//void (*app)(bool) = (void (*)(bool)) APP_START_ADDRESS;
	//app(first);
	//asm("call " SX(APP_START_ADDRESS));
	asm("jmp " SX(APP_START_ADDRESS));
	asm ("ret");

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

//uint32_t last_panic_signal_time = 0;

void busSignalOnline(uint8_t powerOnMode, uint8_t softMode)
{
	uint8_t p[2];
	p[0] = powerOnMode;
	p[1] = softMode;
	send_packet_priv(-1, 0, (uint8_t*) p, sizeof(p));
}

void ub_manage()
{
	ub_manage_connection(&bus, send_on_idle);
	try_dispatch_received_packet();
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
	}
	else
	{
		app_run = true;
	}
	
	MCUSR = 0;
	
	has_app();
	init_board();
	has_app();
	
	bool app_deployed = has_app();
	busSignalOnline
	(
		reset_flag,
//			bb(sos_signal, 2)
//		|
			bb(app_run, 1)
		|
			bb(app_deployed, 0)
	);
	srand(micros());
	
	//wait a little bit, we might get some instruction from the bus before
	//entering application mode
	{
		uint32_t t = micros();
		while(afterMicro(&t, 500000))
		{
			ub_manage();
		}
	}
	wdt_enable(WDTO_1S);
	bool first = true;
	while(1)
	{
		wdt_reset();
		ub_manage();
		wdt_reset();
		
		if(app_run && has_app())
		{
			call_app(first);
			first = false;
		}
	}
	
	abort();
}


