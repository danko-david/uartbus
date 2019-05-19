
#include "lazyctest.h"

#include "ub.h"
#include "rpc.h"

void test_one(void)
{
	TEST_ASSERT_PTR_EQUAL(NULL, (void*)0x1);
}

void test_two(void)
{
	TEST_ASSERT_EQUAL(1, 1);
}

