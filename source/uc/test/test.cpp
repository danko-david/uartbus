
#include "np.h"

//#include "ub.h"
//#include "rpc.h"

extern "C"
{
	void test_one(void)
	{
		NP_ASSERT_PTR_EQUAL(NULL, (void*)~NULL);
	}

	void test_two(void)
	{
		NP_ASSERT_EQUAL(1, 1);
	}
}
