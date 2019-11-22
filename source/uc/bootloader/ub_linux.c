
#include "ubh.h"
#include <sys/time.h>
#include <alloca.h>
#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdio.h>
#include <stddef.h>
#include <unistd.h>
#include <fcntl.h>

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

uint32_t micros()
{
	return ub_impl_get_current_usec();
}

//empty call
void ub_init_infrastructure(){};

uint16_t ubh_linux_get_bus_address()
{
	//TODO read from env
	return 1;
}

void ubh_impl_init()
{

}

void ub_read_stdin()
{
	char *line = NULL;
	size_t len = 0;
	ssize_t read;

	while((read = getline(&line, &len, stdin)) != -1)
	{
		int from = 0;
		int i=0;
		char* end;
		for(;i<MAX_PACKET_SIZE;++i)
		{
			received_data[i] = strtol(line+from, &end, 10);
			if(end == line+from)
			{
				break;
			}
			from = (end - line)+1;
		}
		received_ep = i;
		received = true;

		try_dispatch_received_packet();
	}

	free(line);
	perror("STDIN closed ");
	abort();
}

void init_bus()
{
	//TODO start read thread
	pthread_t tid;
	if(pthread_create(&tid, NULL, ub_read_stdin, NULL) != 0)
	{
		perror("Can't create new thread ");
		abort();
	}
}

bool led = false;

uint8_t ubh_impl_set_user_led(uint8_t v)
{
	switch(v)
	{
	case 0:
		led = false;
		break;

	case 1:
		led = true;
		break;
	case 2:
		led = !led;
		break;
	}

	fprintf(stderr, "User led is turned: %s\n", led?"ON":"OFF");

	return led;
}

bool ubh_impl_has_app()
{
	return true;
}

void ubh_impl_wdt_start(bool b)
{

}


uint8_t* ubh_impl_go_upload_and_allocate_program_tmp_storage()
{
	return NULL;
}


uint8_t ubh_impl_get_program_page_size()
{
	return 0;
}

void ubh_impl_wdt_checkpoint(){}

uint8_t ubh_impl_read_code(uint16_t address, uint8_t length, uint8_t* buff)
{
	return 0;
}

/**
 * Writes the application memory page (usually the program flash memory)
 *
 */
void ubh_impl_write_program_page(const uint32_t address,const uint8_t* data,const uint8_t length){}

uint8_t ubh_impl_get_power_state()
{
	return 0;
}

uint8_t rando()
{
	return (uint8_t) rand()%256;
}

void ubh_impl_call_app(bool first_call)
{
	sleep(1000);
};

void ubh_callback_enqueue_packet()
{
	if(0 != send_size)
	{
		for(int i=0;i<send_size;++i)
		{
			if(0 != i)
			{
				printf(":");
			}
			printf("%d", send_data[i]);
		}
		printf("\n");

		//https://stackoverflow.com/questions/1716296/why-does-printf-not-flush-after-the-call-unless-a-newline-is-in-the-format-strin
		fflush(stdout);
		send_size = 0;
	}
}

void ubh_manage_bus()
{
}
