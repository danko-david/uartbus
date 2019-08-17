EESchema Schematic File Version 2
LIBS:power
LIBS:device
LIBS:transistors
LIBS:conn
LIBS:linear
LIBS:regul
LIBS:74xx
LIBS:cmos4000
LIBS:adc-dac
LIBS:memory
LIBS:xilinx
LIBS:microcontrollers
LIBS:dsp
LIBS:microchip
LIBS:analog_switches
LIBS:motorola
LIBS:texas
LIBS:intel
LIBS:audio
LIBS:interface
LIBS:digital-audio
LIBS:philips
LIBS:display
LIBS:cypress
LIBS:siliconi
LIBS:opto
LIBS:atmel
LIBS:contrib
LIBS:valves
LIBS:bus_connector_frame-cache
EELAYER 25 0
EELAYER END
$Descr A4 11693 8268
encoding utf-8
Sheet 1 1
Title ""
Date ""
Rev ""
Comp ""
Comment1 ""
Comment2 ""
Comment3 ""
Comment4 ""
$EndDescr
$Comp
L R R_bus
U 1 1 5D2F83A1
P 6250 3400
F 0 "R_bus" V 6330 3400 50  0000 C CNN
F 1 "R" V 6250 3400 50  0000 C CNN
F 2 "" V 6180 3400 50  0000 C CNN
F 3 "" H 6250 3400 50  0000 C CNN
	1    6250 3400
	1    0    0    -1  
$EndComp
Text Label 5150 3150 0    60   ~ 0
Vcc
Text Label 5100 4850 0    60   ~ 0
GND
Text HLabel 6150 4100 0    60   Input ~ 0
Bus_Data
Text HLabel 6150 4550 0    60   Input ~ 0
Bus_Gnd
Text HLabel 5050 4100 2    60   Input ~ 0
Online
Text HLabel 5050 4250 2    60   Input ~ 0
TX
Text HLabel 5050 4400 2    60   Input ~ 0
RX
Text HLabel 5050 4550 2    60   Input ~ 0
GND
Text Notes 5400 4400 0    60   ~ 0
Bus connector\n  circuit
Wire Wire Line
	4900 3150 6800 3150
Wire Wire Line
	4900 3650 6800 3650
Wire Wire Line
	4900 4850 6850 4850
Wire Wire Line
	4550 4100 5050 4100
Wire Wire Line
	5050 4250 4550 4250
Wire Wire Line
	5050 4400 4550 4400
Wire Wire Line
	5050 4550 4550 4550
Wire Wire Line
	6250 3550 6250 4100
Connection ~ 6250 3650
Wire Wire Line
	6250 3250 6250 3150
Connection ~ 6250 3150
Wire Wire Line
	6250 4100 6150 4100
Wire Wire Line
	6150 4550 6250 4550
Wire Wire Line
	6250 4550 6250 4850
Connection ~ 6250 4850
Wire Notes Line
	5050 3950 6150 3950
Wire Notes Line
	6150 3950 6150 4700
Wire Notes Line
	6150 4700 5050 4700
Wire Notes Line
	5050 4700 5050 3950
Wire Notes Line
	4550 4700 4550 3950
Wire Notes Line
	3850 4700 4550 4700
Text Notes 8200 7650 0    60   ~ 0
2019/07/17
Text Notes 7400 7500 0    60   ~ 0
Bus connector frame
Wire Notes Line
	4550 3950 3850 3950
Wire Notes Line
	3850 3950 3850 4700
Text Label 5150 3650 0    60   ~ 0
Bus
Text Notes 3850 4350 0    60   ~ 0
Microcontroller
$EndSCHEMATC
