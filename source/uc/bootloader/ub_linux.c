
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
