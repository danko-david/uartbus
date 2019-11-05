#include "arduino/HardwareSerial.h"

#pragma message "A MOCK HardwareSerial.h injected into the include path"

#ifdef BUS_SERIAL0
#undef USART0_RX_vect
#undef USART0_UDRE_vect
#undef UBRR0H
#endif

#ifdef BUS_SERIAL1
#undef USART1_RX_vect
#undef USART1_UDRE_vect
#undef UBRR1H
#endif

#ifdef BUS_SERIAL2
#undef USART2_RX_vect
#undef USART2_UDRE_vect
#undef UBRR2H
#endif

#ifdef BUS_SERIAL3
#undef USART3_RX_vect
#undef USART3_UDRE_vect
#undef UBRR3H
#endif
