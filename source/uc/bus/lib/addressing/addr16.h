
#ifndef _UB_ADDR16_H_
#define _UB_ADDR16_H_

#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>

#include "posix_errno.h"

int8_t ub_pack_16_value(int16_t v, uint8_t* arr, int size);
int8_t ub_unpack_16_value(int16_t* dst, uint8_t* arr, int size);

#endif
