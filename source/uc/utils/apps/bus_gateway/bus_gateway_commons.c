
//caller source should implement this functions

void ub_rec_byte(struct uartbus* a, uint8_t data_byte);
void ub_event(struct uartbus* a, enum uartbus_event event);
uint8_t ub_do_send_byte(struct uartbus* bus, uint8_t val);
void manage_data_from_pc();

/**************************** queue management ********************************/

#ifndef MAX_QUEUE_ENTRY
	#define MAX_QUEUE_ENTRY 0
#endif

#ifndef MAX_PACKET_SIZE
	#define MAX_PACKET_SIZE 64
#endif

struct queue_entry
{
	struct queue_entry* next;
	#if MAX_QUEUE_ENTRY != 0
		bool in_use;
	#endif
	int size;
	uint8_t data[MAX_PACKET_SIZE];
};

struct queue
{
	struct queue_entry* head;
	struct queue_entry* tail;
};


struct queue from_serial;

#if MAX_QUEUE_ENTRY != 0
	struct queue_entry queue_entries[MAX_QUEUE_ENTRY];
#endif

/*
struct queue* new_queue()
{
	struct queue* ret = (struct queue*) malloc(sizeof(struct queue));
	memset(ret, 0, sizeof(struct queue));
	return ret;
}
*/

struct queue_entry* new_queue_entry(size_t size)
{
	#if MAX_QUEUE_ENTRY == 0
		struct queue_entry* ret = (struct queue_entry*) malloc(sizeof(struct queue_entry)+size);
		if(NULL != ret)
		{
			ret->next = NULL;
			ret->size = 0;
		}
		return ret;
	#else
		for(uint8_t i=0;i<MAX_QUEUE_ENTRY;++i)
		{
			if(!queue_entries[i].in_use)
			{
				queue_entries[i].in_use = true;
				return &queue_entries[i];
			}
		}
		
		return NULL;
	#endif
}

void free_queue_entry(struct queue_entry* ent)
{
	#if MAX_QUEUE_ENTRY == 0
		free(ent);
	#else
		ent->next = NULL;
		ent->in_use = false;
	#endif
}

void queue_push(struct queue* q, struct queue_entry* ent)
{
	if(NULL == q->head)
	{
		q->head = ent;
		q->tail = ent;
	}
	else
	{
		q->tail->next = ent;
		q->tail = ent;
	}
}

struct queue_entry* queue_take(struct queue* q)
{
	struct queue_entry* ret = q->head;
	if(NULL != ret)
	{
		q->head = ret->next;
		if(NULL == ret->next)
		{
			q->tail = NULL;
		}
	}
	return ret;
}

void queue_enqueue_content(struct queue* q, uint8_t* data, uint8_t size)
{
	struct queue_entry* add = new_queue_entry(size);
	if(NULL != add)
	{
		add->size = size;
		for(size_t i = 0;i<size;++i)
		{
			add->data[i] = data[i];
		}
		queue_push(q, add);
	}
}

struct uartbus bus;

uint8_t rando()
{
	return rand()%256;
}

void init_ubg_commons()
{
	#if MAX_QUEUE_ENTRY == 0
		from_serial = new_queue();
	#endif
}

//yet another memory allocation beacuse of the wrong memory ownership design...
uint8_t send_data[MAX_PACKET_SIZE];

static uint8_t send_on_idle(struct uartbus* bus, uint8_t** data, uint16_t* size)
{
	struct queue_entry* send;

#ifndef UBG_ALLOC_NO_SYNCHRONIZATION_NEEDED
	ATOMIC_BLOCK(ATOMIC_RESTORESTATE)
#endif
	{
		send = queue_take(&from_serial);
	}

	if(NULL != send)
	{
		memcpy(send_data, send->data, send->size);
		*data = send_data;
		*size = send->size;

#ifndef UBG_ALLOC_NO_SYNCHRONIZATION_NEEDED
		ATOMIC_BLOCK(ATOMIC_RESTORESTATE)
#endif
		{
			free_queue_entry(send);
		}
		return 0;
	}
	return 1;
}

void init_bus()
{
	//received_ep = 0;

	bus.rand = (uint8_t (*)(struct uartbus*)) rando;
	bus.current_usec = (uint32_t (*)(struct uartbus* bus)) micros;
	bus.serial_byte_received = ub_rec_byte;
	bus.serial_event = ub_event;
	ub_init_baud(&bus, BAUD, 2);
	bus.do_send_byte = ub_do_send_byte;
	bus.cfg = 0
//		|	ub_cfg_fairwait_after_send_high
		|	ub_cfg_fairwait_after_send_low
		|	ub_cfg_read_with_interrupt
		|	ub_cfg_skip_collision_data
	;
	ub_init(&bus);
}

void ub_manage()
{
	ub_manage_connection(&bus, send_on_idle);
}

void ubg_handle_events()
{
	ub_manage();
	manage_data_from_pc();
}


