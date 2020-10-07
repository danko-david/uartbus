#
# Simple Arduino Makefile
#
# Author: Nick Gammon
# Date: 18th March 2015

# where you installed the Arduino app
SHELL = /bin/bash
ARDUINO_DIR = /usr/share/arduino/

# various programs
CC = "$(ARDUINO_DIR)hardware/tools/avr/bin/avr-gcc"
CPP = "$(ARDUINO_DIR)hardware/tools/avr/bin/avr-g++"
AR = "$(ARDUINO_DIR)hardware/tools/avr/bin/avr-ar"
OBJ_COPY = "$(ARDUINO_DIR)hardware/tools/avr/bin/avr-objcopy"

MAIN_SKETCH = uartbus_connector.cpp

# compile flags for g++ and gcc

# may need to change these
#F_CPU = 16000000
#MCU = atmega328p

# compile flags
GENERAL_FLAGS = -c -g -Os -Wall -ffunction-sections -fdata-sections -mmcu=$(MCU) -DF_CPU=$(F_CPU)L -MMD -DUSB_VID=null -DUSB_PID=null -DARDUINO=105 $(EXT_FLAGS)
CPP_FLAGS = $(GENERAL_FLAGS) -fno-exceptions
CC_FLAGS  = $(GENERAL_FLAGS)

# location of include files

INCLUDE_FILES = "-I$(ARDUINO_DIR)hardware/arduino/cores/" "-I$(ARDUINO_DIR)hardware/arduino/cores/arduino" "-I$(ARDUINO_DIR)hardware/arduino/variants/$(ARDUINO_VARIANT)/" "-I../../../../bus/lib/arduino" "-I../../../../bus/lib/common" "-I."

# library sources
LIBRARY_DIR = $(ARDUINO_DIR)hardware/arduino/cores/arduino/

build:
	$(CPP) $(CPP_FLAGS) $(INCLUDE_FILES) $(MAIN_SKETCH) -o $(MAIN_SKETCH).o
#	$(CC) $(CC_FLAGS) $(INCLUDE_FILES) $(LIBRARY_DIR)avr-libc/malloc.c -o malloc.c.o 
#	$(CC) $(CC_FLAGS) $(INCLUDE_FILES) $(LIBRARY_DIR)avr-libc/realloc.c -o realloc.c.o 
	$(CC) $(CC_FLAGS) $(INCLUDE_FILES) $(LIBRARY_DIR)WInterrupts.c -o WInterrupts.c.o 
	$(CC) $(CC_FLAGS) $(INCLUDE_FILES) $(LIBRARY_DIR)wiring.c -o wiring.c.o 
	$(CC) $(CC_FLAGS) $(INCLUDE_FILES) $(LIBRARY_DIR)wiring_analog.c -o wiring_analog.c.o 
	$(CC) $(CC_FLAGS) $(INCLUDE_FILES) $(LIBRARY_DIR)wiring_digital.c -o wiring_digital.c.o 
	$(CC) $(CC_FLAGS) $(INCLUDE_FILES) $(LIBRARY_DIR)wiring_pulse.c -o wiring_pulse.c.o 
	$(CC) $(CC_FLAGS) $(INCLUDE_FILES) $(LIBRARY_DIR)wiring_shift.c -o wiring_shift.c.o
	
	
	$(CPP) $(CPP_FLAGS) $(INCLUDE_FILES) -include HardwareSerial.h $(LIBRARY_DIR)HardwareSerial.cpp -o HardwareSerial.cpp.o
ifneq ("${MCU}","atmega328p")
ifneq (,$(wildcard $(LIBRARY_DIR)HardwareSerial0.cpp))
	$(info Add HS0)
	$(CPP) $(CPP_FLAGS) $(INCLUDE_FILES) -include HardwareSerial.h $(LIBRARY_DIR)HardwareSerial0.cpp -o HardwareSerial0.cpp.o
endif 
endif
	$(CPP) $(CPP_FLAGS) $(INCLUDE_FILES) $(LIBRARY_DIR)main.cpp -o main.cpp.o 
	$(CPP) $(CPP_FLAGS) $(INCLUDE_FILES) $(LIBRARY_DIR)new.cpp -o new.cpp.o 
	$(CPP) $(CPP_FLAGS) $(INCLUDE_FILES) $(LIBRARY_DIR)Print.cpp -o Print.cpp.o 
	$(CPP) $(CPP_FLAGS) $(INCLUDE_FILES) $(LIBRARY_DIR)Stream.cpp -o Stream.cpp.o
	$(CPP) $(CPP_FLAGS) $(INCLUDE_FILES) ./AltSoftSerial.cpp -o AltSoftSerial.cpp.o
	$(CPP) $(CPP_FLAGS) $(INCLUDE_FILES) ../../../../bus/lib/common/ub.c -o ub.cpp.o
	$(AR) rcs core.a *.{c,cpp}.o
	$(CC) -Os -Wl,--gc-sections -mmcu=$(MCU) -o $(MAIN_SKETCH).elf core.a -lm 
	$(OBJ_COPY) -O ihex -j .eeprom --set-section-flags=.eeprom=alloc,load --no-change-warnings --change-section-lma .eeprom=0 $(MAIN_SKETCH).elf $(MAIN_SKETCH).eep 
	$(OBJ_COPY) -O ihex -R .eeprom $(MAIN_SKETCH).elf $(MAIN_SKETCH).hex
	avr-objdump -S --disassemble $(MAIN_SKETCH).elf > $(MAIN_SKETCH).asm

clean:
	rm *.{o,d,a,hex,eep,elf}

ls:
	