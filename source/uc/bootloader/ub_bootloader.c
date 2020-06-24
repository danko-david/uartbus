
#include "ubh.h"

#pragma message "Compiling UARTBus host application with clock speed: " SX(F_CPU)

/******************************* GLOBAL variables *****************************/

volatile bool app_run;
volatile uint8_t reset_flag;

void (*app_int_handler)(void*);

void ubh_provide_dispatch_interrupt(void* from)
{
	if(NULL != app_int_handler)
	{
		app_int_handler(from);
	}
}

/******************* On board software upgrade  functionalities ***************/

//enter into upgrade mode (is in upgrade mode)

//get constants, required by the compiler whos send the code

//erase app

//supply code segment

//flush

//read programspace: addr len
//commit -> back to app mode
//bool send_packet(int16_t to, uint8_t* data, uint16_t size);
bool send_packet_priv(int16_t to, uint8_t ns, uint8_t* data, uint8_t size);

int16_t rpc_response(struct rpc_request* req, uint8_t args, struct response_part** parts)
{
	int neg = req->procPtr;
	
	int size = rpc_append_size(args, parts);
	if(size < 0)
	{
		return size;
	}
	
	uint8_t* d = (uint8_t*) alloca(size+neg);
	
	for(uint8_t i=0;i<neg;++i)
	{
		d[i] = req->payload[i];
	}
	
	rpc_append_arr(d+neg, size, args, parts);

	return send_packet_priv(req->from, 0, d, size+neg);
}

/************************** RPC functions - Basic *****************************/

//1:0
void rpc_basic_ping(struct rpc_request* req)
{
	il_reply(req, 0);
}

//1:1
void rpc_basic_replay(struct rpc_request* req)
{
	il_reply_arr(req, req->payload+req->procPtr, req->size - req->procPtr);
}

//1:2:x
void rpc_basic_user_led(struct rpc_request* req)
{
	if(0 != (req->size - req->procPtr))
	{
		il_reply(req, 1, ubh_impl_set_user_led(req->payload[req->procPtr]));
	}
}


//https://www.avrfreaks.net/forum/extracting-bytes-long
union LONG_BYTES
{
	int16_t int16;
	uint16_t uint16;
	int32_t int32;
	uint32_t uint32;
	
	struct {
		uint8_t b0;
		uint8_t b1;
		uint8_t b2;
		uint8_t b3;
	} bytes;

	struct {
		uint16_t w0;
		uint16_t w1;
	} words;
};

//1:3
void rpc_basic_get_time(struct rpc_request* req)
{
	union LONG_BYTES t;
	t.uint32 = micros();
	
	il_reply
	(
		req, 4, 
		
		t.bytes.b3,
		t.bytes.b2,
		t.bytes.b1,
		t.bytes.b0
	);
}

//1:4
void rpc_basic_random(struct rpc_request* req)
{
	il_reply(req, 1, rando());
}

void* RPC_FUNCTIONS_BASIC[] =
{
	(void*) 5,
	(void*) rpc_basic_ping,
	(void*) rpc_basic_replay,
	(void*) rpc_basic_user_led,
	(void*) rpc_basic_get_time,
	(void*) rpc_basic_random
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
		case 0: ubh_impl_wdt_start(false);while(1){}
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
		case 3: res = ubh_impl_has_app();  break;
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
	
	uint8_t read = ubh_impl_read_code(base, s, &rec[2]);

	il_reply_arr(req, rec, s+2);
}

/***************************** Flashing functions *****************************/

uint8_t flash_stage = 0;

//when it's initailized, always must point to the start of a page boundry.
uint16_t flash_crnt_address = 0;
uint8_t* flash_tmp = NULL;

//2:4:0
void blf_get_flash_stage(struct rpc_request* req)
{
	il_reply(req, 2, 0, flash_stage);
}

//2:4:1
void blf_start_flash(struct rpc_request* req)
{
	if(1 == flash_stage)
	{
		il_reply(req, 1, EALREADY);		
	}
	
	flash_tmp = (uint8_t*) ubh_impl_go_upload_and_allocate_program_tmp_storage();
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

//2:4:2
void blf_get_next_addr(struct rpc_request* req)
{
	il_reply(req, 2, ((flash_crnt_address >> 8) & 0xff), flash_crnt_address & 0xff); 
}


void fill_flash(uint8_t *buf, uint16_t size)
{
	uint8_t page_size = ubh_impl_get_program_page_size();
	for(uint16_t i = 0;i<size;++i)
	{
		//filling flash temporary write storage from the given buffer.
		flash_tmp[flash_crnt_address % page_size] = buf[i];
		++flash_crnt_address;
		
		//if page fullfilled => flush it.
		if(0 == flash_crnt_address % page_size)
		{
			ubh_impl_write_program_page
			(
				(flash_crnt_address-1) & ~(page_size-1),
				flash_tmp,
				page_size
			);
		}
	}
}

//2:4:3
void blf_push_code(struct rpc_request* req)
{
	uint8_t* data = req->payload + req->procPtr;
	uint8_t size = req->size - req->procPtr;
	if(0 == flash_stage)
	{
		il_reply(req, 1, ENOTCONN);
		return;
	}
	
	if(size < 3)
	{
		il_reply(req, 1, EBADMSG);
		return;
	}
	
	if(flash_crnt_address < 0x1f00)
	{
		il_reply(req, 1, EFAULT);
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

//2:4:4
void commit_flash(struct rpc_request* req)
{
	if(0 == flash_stage)
	{
		il_reply(req, 1, EBADFD);
		return;
	}
	
	if(flash_crnt_address < 0x1f00)
	{
		il_reply(req, 1, EFAULT);
		return;
	}
	
	uint8_t page_size = ubh_impl_get_program_page_size();
	
	//something filled into the write buffer => writing page
	if(0 != flash_crnt_address % page_size)
	{
		ubh_impl_write_program_page
		(
			flash_crnt_address & ~(page_size-1),
			flash_tmp,
			(flash_crnt_address & (page_size-1))
		);
	}
	
	flash_stage = 0;
	
	il_reply(req, 1, 0);
}

//2:4:x
void* BOOTLOADER_FLASH_FUNCTIONS[] =
{
	(void*) 5,
	(void*) blf_get_flash_stage,
	(void*) blf_start_flash,
	(void*) blf_get_next_addr,
	(void*) blf_push_code,
	(void*) commit_flash
};

//2:4:x
void rpc_bootloader_flash(struct rpc_request* req)
{
	dispatch_function_chain(BOOTLOADER_FLASH_FUNCTIONS, req);
}

//2:
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
	(void*) 5,
	NULL,
	RPC_FUNCTIONS_BASIC,
	RPC_FUNCTIONS_BOOTLOADER,
	RPC_FUNCTIONS_DEBUG,
	RPC_FUNCTIONS_TRANSACTION,//transaction management
};

void dispatch_root(struct rpc_request* req)
{
	dispatch_descriptor_chain(RPC_NS_FUNCTIONS, req);
}

/************************ UARTBus application code ****************************/


int received_ep;
uint8_t received_data[MAX_PACKET_SIZE];

uint8_t send_size;
uint8_t send_data[MAX_PACKET_SIZE];

void (*app_dispatch)(struct rpc_request* req) = NULL;

/*void predict_transmission()
{
	if((PORTD & _BV(PD0)))//if RX is low
	{
		ub_predict_transmission_start(&bus);
	}
}*/


#ifndef EXTERNAL_SEND_PACKET_PRIV

bool send_packet_priv(int16_t to, uint8_t NS, uint8_t* data, uint8_t size)
{
	//1 from 1 to 1 NS 1 CRC
	if(0 != send_size || size >= MAX_PACKET_SIZE-4)
	{
		return false;
	}
	
	uint8_t ep = 0;
	int8_t add;
	
	if((add = ub_pack_16_value(to, send_data, MAX_PACKET_SIZE)) < 1)
	{
		return false;
	}
	
	ep += add;
		
	if((add = ub_pack_16_value(BUS_ADDRESS, send_data+ep, MAX_PACKET_SIZE-ep)) < 1)
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
	
#ifdef UBH_CALLBACK_ENQUEUE_PACKET
	ubh_callback_enqueue_packet();
#endif

	return true;
}

#endif

void dispatch(struct rpc_request* req)
{
	if(0 == req->size)
	{
		return;
	}
	
	uint8_t ns = req->payload[req->procPtr];

	if(req->from >= 0 && 0 < ns && ns < 32)
	{
		dispatch_root(req);
	}
	else if(NULL != app_dispatch && (32 <= ns || 0 == ns))
	{
		app_dispatch(req);
	}
}

volatile bool received = false;

void try_dispatch_received_packet()
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
			if((add = ub_unpack_16_value(&req.to, received_data, received_ep-1)) < 1)
			{
				return;
			}
			
			ep += add;
			
			if((add = ub_unpack_16_value(&req.from, received_data+ep, received_ep-1-ep)) < 1)
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
			
			//early release of receive buffer 
			received_ep = 0;
			
			//is we are the target, or group/broadcast?
			if(req.to < 1 || BUS_ADDRESS == req.to)
			{
				dispatch(&req);
			}
		}
	}

#ifdef UBH_DEBUG_BROADCAST_PACKET_WITH_BAD_CRC
	else
	{
		received_data[received_ep] = crc8(received_data, received_ep-1);
		send_packet_priv(-10, 0, received_data, received_ep+1);
	}
#endif
	
	received_ep = 0;
}

/*bool send_packet(int16_t to, uint8_t* data, uint16_t size)
{
	return send_packet_priv(to, 16, data, size);
}*/

bool may_send_packet()
{
	return 0 == send_size;
}

uint8_t get_max_packet_size()
{
	return MAX_PACKET_SIZE;
}

void register_packet_dispatch(void (*addr)(struct rpc_request* req))
{
	app_dispatch = addr;
}

/*************************** Host software utilities **************************/

int init_board(void)
{
	ubh_impl_init();
	init_bus();
}

bool manage_bus()
{
	ubh_impl_wdt_checkpoint();
	ubh_manage_bus();
	ubh_impl_wdt_checkpoint();
}

/********************************** Host tables *******************************/

//constants, function pointers

//bus address
//app start address [default] = 4096

void* HOST_TABLE[] =
{
	(void*) register_packet_dispatch,
	(void*) may_send_packet,
	(void*) send_packet_priv,
	(void*) get_max_packet_size,
	(void*) micros,
	(void*) manage_bus
};



__attribute__((section(".host_table"))) void** getHostTable()
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

//boolean bit
#define bb(x, y) x?(0x1 <<y):0

int main()
{
	reset_flag = ubh_impl_get_power_state();
	//watchdog reset and not external or power-on 
	
	app_run = reset_flag != 8;
	
	init_board();
	
	busSignalOnline
	(
		reset_flag,
//			bb(sos_signal, 2)
//		|
			(app_run?2:0)
		|
			(ubh_impl_has_app()?1:0)
	);
	//srand(micros());
	
	#ifdef DEBUG_TIME_AT_BOOT
	
	{
		ubh_impl_set_user_led(1);
		uint32_t t = micros();
		while(!afterMicro(&t, 1000000));
		ubh_impl_set_user_led(0);
	}
	#endif
	//wait a little bit, we might get some instruction from the bus before
	//entering application mode
	{
		uint32_t t = micros();
		while(!afterMicro(&t, 500000))
		{
			ubh_impl_wdt_checkpoint();
			ubh_manage_bus();
		}
	}
	
	
	
	ubh_impl_wdt_start(true);
	bool first = true;
	while(1)
	{
		manage_bus();
		
		if(app_run && ubh_impl_has_app())
		{
			ubh_impl_call_app(first);
			first = false;
		}
	}

	abort();
}


