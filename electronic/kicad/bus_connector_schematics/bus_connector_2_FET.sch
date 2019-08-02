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
LIBS:szakdoga-cache
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
Text HLabel 4400 3950 0    60   Input ~ 0
Online
Text HLabel 4400 4150 0    60   Input ~ 0
TX
Text HLabel 4400 4350 0    60   Input ~ 0
RX
Text HLabel 4400 4550 0    60   Input ~ 0
GND
Text HLabel 7800 3350 2    60   Input ~ 0
Bus_Data
Text HLabel 7800 4550 2    60   Input ~ 0
Bus_Gnd
$Comp
L BS170 Q1
U 1 1 5D2F88C0
P 5500 4350
F 0 "Q1" H 5700 4425 50  0000 L CNN
F 1 "BS170" H 5700 4350 50  0000 L CNN
F 2 "" H 5700 4275 50  0000 L CIN
F 3 "" H 5500 4350 50  0000 L CNN
	1    5500 4350
	1    0    0    -1  
$EndComp
$Comp
L BS170 Q2
U 1 1 5D2F88C1
P 6350 4350
F 0 "Q2" H 6550 4425 50  0000 L CNN
F 1 "BS170" H 6550 4350 50  0000 L CNN
F 2 "" H 6550 4275 50  0000 L CIN
F 3 "" H 6350 4350 50  0000 L CNN
	1    6350 4350
	1    0    0    -1  
$EndComp
$Comp
L R R_TX
U 1 1 5D2F88C2
P 5000 4400
F 0 "R_TX" V 5080 4400 50  0000 C CNN
F 1 "R" V 5000 4400 50  0000 C CNN
F 2 "" V 4930 4400 50  0000 C CNN
F 3 "" H 5000 4400 50  0000 C CNN
	1    5000 4400
	0    -1   -1   0   
$EndComp
$Comp
L R R_online
U 1 1 5D2F88C3
P 5000 3950
F 0 "R_online" V 5080 3950 50  0000 C CNN
F 1 "R" V 5000 3950 50  0000 C CNN
F 2 "" V 4930 3950 50  0000 C CNN
F 3 "" H 5000 3950 50  0000 C CNN
	1    5000 3950
	0    -1   -1   0   
$EndComp
$Comp
L BS170 Q3
U 1 1 5D2F88C4
P 6800 3750
F 0 "Q3" H 7000 3825 50  0000 L CNN
F 1 "BS170" H 7000 3750 50  0000 L CNN
F 2 "" H 7000 3675 50  0000 L CIN
F 3 "" H 6800 3750 50  0000 L CNN
	1    6800 3750
	1    0    0    -1  
$EndComp
$Comp
L R R_in
U 1 1 5D2F88C5
P 6900 4250
F 0 "R_in" V 6980 4250 50  0000 C CNN
F 1 "R" V 6900 4250 50  0000 C CNN
F 2 "" V 6830 4250 50  0000 C CNN
F 3 "" H 6900 4250 50  0000 C CNN
	1    6900 4250
	1    0    0    -1  
$EndComp
Text Notes 8200 7650 0    60   ~ 0
2019/07/17
Text Notes 7350 7500 0    60   ~ 0
Bus connector implemented with FETs, for bus voltage between 7-18V
$Comp
L D_TVS D1
U 1 1 5D2F91F9
P 7300 4200
F 0 "D1" H 7300 4300 50  0000 C CNN
F 1 "D_TVS" H 7300 4100 50  0000 C CNN
F 2 "" H 7300 4200 50  0000 C CNN
F 3 "" H 7300 4200 50  0000 C CNN
	1    7300 4200
	0    1    1    0   
$EndComp
$Comp
L Polyfuse F1
U 1 1 5D2F9528
P 7550 3350
F 0 "F1" V 7450 3350 50  0000 C CNN
F 1 "Polyfuse" V 7650 3350 50  0000 C CNN
F 2 "" H 7600 3150 50  0001 L CNN
F 3 "" H 7550 3350 50  0001 C CNN
	1    7550 3350
	0    -1   -1   0   
$EndComp
Wire Wire Line
	4400 4150 4850 4150
Wire Wire Line
	4400 3950 4850 3950
Wire Wire Line
	5150 4400 5300 4400
Wire Wire Line
	4850 4150 4850 4400
Wire Wire Line
	6150 3950 6150 4400
Wire Wire Line
	5600 3950 5600 4150
Connection ~ 5600 3950
Connection ~ 5600 4550
Connection ~ 6450 4550
Wire Wire Line
	6150 3950 5150 3950
Connection ~ 6900 4550
Wire Wire Line
	4600 4350 4400 4350
Wire Wire Line
	6450 3350 6450 4150
Wire Wire Line
	6600 3800 6450 3800
Connection ~ 6450 3800
Wire Wire Line
	6900 3550 4500 3550
Wire Wire Line
	4500 3550 4500 3950
Connection ~ 4500 3950
Wire Wire Line
	6900 3950 6900 4100
Wire Wire Line
	6900 4550 6900 4400
Wire Wire Line
	6900 4000 6300 4000
Wire Wire Line
	6300 4000 6300 3700
Wire Wire Line
	6300 3700 4600 3700
Wire Wire Line
	4600 3700 4600 4350
Connection ~ 6900 4000
Wire Wire Line
	7300 3350 7300 4050
Connection ~ 7300 3350
Wire Wire Line
	7300 4550 7300 4350
Connection ~ 7300 4550
Wire Wire Line
	4400 4550 7300 4550
$EndSCHEMATC
