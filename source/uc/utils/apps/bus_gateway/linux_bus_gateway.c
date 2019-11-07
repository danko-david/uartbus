
//if you miss rerunnable_thread.c or worker_pool.c file call "./scripts/get_c_deps.sh"


#include "inttypes.h"
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include "ub.h"

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <termios.h>
#include <signal.h>

#include "worker_pool.h"

#include <sys/time.h>

uint32_t micros()
{
	struct timeval tv;
	struct timezone tz;
	int stat = gettimeofday(&tv, &tz);
	uint32_t now =
		tv.tv_usec;
	return now;
}

struct uartbus bus;
int serial_fd;

#ifndef PACKET_ESCAPE
	#define PACKET_ESCAPE (uint8_t) 0xff
#endif

uint8_t PACKET_END[] = {PACKET_ESCAPE, ~PACKET_ESCAPE};

/******************************** UARTBus *************************************/

bool receive = false;

void ub_rec_byte(struct uartbus* a, uint8_t data_byte)
{
	if(receive)
	{
		if(data_byte == PACKET_ESCAPE)
		{
			write(1, &data_byte, 1);
		}
		write(1, &data_byte, 1);
	}
}

uint8_t ub_do_send_byte(struct uartbus* bus, uint8_t val)
{
	write(serial_fd, &val, 1);
	fsync(serial_fd);
	return 0;
}

void ub_event(struct uartbus* a, enum uartbus_event event)
{
	if(ub_event_receive_start == event)
	{
		#ifdef UBG_DEBUG_PRINT
			fprintf(stderr, "UB packet start\n");
		#endif
		receive = true;
	}
	else if(ub_event_receive_end == event)
	{
		#ifdef UBG_DEBUG_PRINT
			fprintf(stderr, "UB packet end\n");
		#endif
		write(1, PACKET_END, 2);
		receive = false;
	}
}

uint8_t rando()
{
	return rand()%256;
}

/********************************** serial ************************************/

volatile uint8_t* send_data;
volatile uint8_t send_size = 0;

uint8_t send_on_idle(struct uartbus* bus, uint8_t** data, uint16_t* size)
{
	if(send_size != 0)
	{
		*data = send_data;
		*size = send_size;
		send_size = 0;
		send_data = NULL;
		return 0;
	}
	return 1;
}

/****************************** Manage lines **********************************/

void elevate_priority()
{
	for(int i=0;i>-30;--i)
	{
		nice(i);
	}

	pthread_t this_thread = pthread_self();
	struct sched_param params;
	params.sched_priority = sched_get_priority_max(SCHED_FIFO);
	int ret = pthread_setschedparam(this_thread, SCHED_FIFO, &params);
}

void manage_data_from_pc()
{
	elevate_priority();

	int ep = 0;
	bool mayCut = false;
	int decode_size = 1024;
	uint8_t* decode = malloc(decode_size);

	uint8_t buffer[256];
	while(true)
	{
		int len = read(0, buffer, sizeof(buffer));

		if(len <= 0)
		{
			perror("Error while reading from PC: ");
			abort();
		}

		for(int i=0;i<len;++i)
		{
			uint8_t b = buffer[i];

		#ifdef UBG_DEBUG_PRINT
			fprintf(stderr, "PC read: %d (%02X)\n", b, b);
		#endif

			if(mayCut)
			{
				if(b == (uint8_t) ~PACKET_ESCAPE)
				{
					send_data = decode;
					send_size = ep;
					#ifdef UBG_DEBUG_PRINT
						fprintf(stderr, "Dispatching packet from PC\n");
					#endif

					while(NULL != send_data || bus.status != ub_stat_idle)
					{
						ub_manage_connection(&bus, send_on_idle);
					}
					ep = 0;
				}
				else
				{
					decode[ep++] = b;
					if(decode_size <= ep)
					{
						decode_size *= 2;
						decode = realloc(decode, decode_size);
					}
				}
				mayCut = false;
			}
			else
			{
				if(b == PACKET_ESCAPE)
				{
					mayCut = true;
					#ifdef UBG_DEBUG_PRINT
						fprintf(stderr, "PC read: mayCut=true\n");
					#endif
				}
				else
				{
					decode[ep++] = b;
					if(decode_size <= ep)
					{
						decode_size *= 2;
						decode = realloc(decode, decode_size);
					}
				}
			}
		}
	}
}

void manage_data_from_bus()
{
	elevate_priority();
	while(true)
	{
		uint8_t val;
		int len = read(serial_fd, &val, 1);
		if(len <= 0)
		{
			perror("Error while reading from serial: ");
			abort();
		}

		#ifdef UBG_DEBUG_PRINT
			fprintf(stderr, "serial read: %d (%02X)\n", val, val);
		#endif
		ub_out_rec_byte(&bus, val);
	}
}

void init_bus(int baud)
{
	//received_ep = 0;

	bus.rand = (uint8_t (*)(struct uartbus*)) rando;
	bus.current_usec = (uint32_t (*)(struct uartbus* bus)) micros;
	bus.serial_byte_received = ub_rec_byte;
	bus.serial_event = ub_event;
	ub_init_baud(&bus, baud, 2);
	bus.do_send_byte = ub_do_send_byte;
	bus.cfg = 0
//		|	ub_cfg_fairwait_after_send_high
		|	ub_cfg_fairwait_after_send_low
		|	ub_cfg_read_with_interrupt
		|	ub_cfg_skip_collision_data
	;
	ub_init(&bus);
}

//https://stackoverflow.com/questions/47311500/how-to-efficiently-convert-baudrate-from-int-to-speed-t
int get_baud(int baud)
{
	switch (baud) {
	case 9600:
		return B9600;
	case 19200:
		return B19200;
	case 38400:
		return B38400;
	case 57600:
		return B57600;
	case 115200:
		return B115200;
	case 230400:
		return B230400;
	case 460800:
		return B460800;
	case 500000:
		return B500000;
	case 576000:
		return B576000;
	case 921600:
		return B921600;
	case 1000000:
		return B1000000;
	case 1152000:
		return B1152000;
	case 1500000:
		return B1500000;
	case 2000000:
		return B2000000;
	case 2500000:
		return B2500000;
	case 3000000:
		return B3000000;
	case 3500000:
		return B3500000;
	case 4000000:
		return B4000000;
	default:
		return -1;
	}
}

int tty(const char* tty, int baudSpeedSymbol)
{
	/*
	  Open modem device for reading and writing and not as controlling tty
	  because we don't want to get killed if linenoise sends CTRL-C.
	*/
	int fd = open(tty, O_RDWR | O_NOCTTY | O_SYNC);
	if(fd < 0)
	{
		fprintf(stderr, "failed to open tty: %s, %s\n", tty, strerror(errno));
		exit(1);
	}

	struct termios options;// Terminal options

	// Get the current options for the port
	if(tcgetattr(fd, &options) < 0)
	{
		fprintf(stderr, "failed to get attr: %d, %s\n", fd, strerror(errno));
		exit(1);
	}

	// Set the baud rates to 230400
	cfsetispeed(&options, baudSpeedSymbol);

	// Set the baud rates to 230400
	cfsetospeed(&options, baudSpeedSymbol);

	cfmakeraw(&options);
	options.c_cflag |= (CLOCAL | CREAD);   // Enable the receiver and set local mode
	options.c_cflag &= ~CSTOPB;// 1 stop bit
	options.c_cflag &= ~CRTSCTS;   // Disable hardware flow control
	options.c_cc[VMIN]  = 1;
	options.c_cc[VTIME] = 2;

	// Set the new attributes
	if(tcsetattr(fd, TCSANOW, &options) < 0)
	{
		fprintf(stderr, "failed to set attr: %d, %s\n", fd, strerror(errno));
		exit(2);
	}

	return fd;
}

void print_help_and_exit(char* cmd)
{
	fprintf(stderr, "Usage: %s $serial_path $baud\n", cmd);
	fprintf(stderr, "eg.: %s /dev/ttyUSB0 115200\n", cmd);
	exit(1);
}

void sigsegv_handler()
{
#ifdef __GLIBC__
	void* array[10];
	size_t size;
	char** strings;
	size_t i;

	size = backtrace(array, 10);
	strings = backtrace_symbols(array, size);

	printf("Obtained %zd stack frames.\n", size);

	for(i = 0; i < size; i++)
	{
		printf ("%s\n", strings[i]);
	}

	fflush(stdout);
	free (strings);
	abort();
#else
	printf("//TODO implement `print_stack_trace` on this platform!\n");
#endif
}

void install_sigsegv_handler()
{
	struct sigaction sa;

	memset(&sa, 0, sizeof(sigaction));
	sigemptyset(&sa.sa_mask);
	sa.sa_sigaction = sigsegv_handler;
	sigaction(SIGSEGV, &sa, NULL);
}

void main(int argc, char* argv[])
{
	install_sigsegv_handler();

	if(3 != argc)
	{
		fprintf(stderr, "Invalid number of parameter supplied\n");
		print_help_and_exit(argv[0]);
	}

	int baud = atoi(argv[2]);
	int baud_symbol = get_baud(baud);
	if(-1 == baud_symbol)
	{
		fprintf(stderr, "Unrecognizable baud rate %s\n", argv[2]);
		print_help_and_exit(argv[0]);
	}

	serial_fd = tty(argv[1], baud_symbol);

	elevate_priority();

	init_bus(baud);

	struct worker_pool pool;

	wp_init(&pool);
	wp_submit_task(&pool, manage_data_from_bus, NULL);
	wp_submit_task(&pool, manage_data_from_pc, NULL);

	while(true)
	{
		//printf("bus state: %d\n", bus.status);
		usleep(100);
		//sleep(1000);
		ub_manage_connection(&bus, NULL);
	}

	//TODO start from serial and to serial

}

