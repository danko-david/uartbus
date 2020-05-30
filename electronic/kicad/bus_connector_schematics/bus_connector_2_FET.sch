EESchema Schematic File Version 4
LIBS:UB_AT328_proto-cache
EELAYER 26 0
EELAYER END
$Descr A4 11693 8268
encoding utf-8
Sheet 2 2
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
Text HLabel 8250 3350 2    60   Input ~ 0
Bus_Data
Text HLabel 8250 4550 2    60   Input ~ 0
Bus_Gnd
$Comp
L UB_AT328_proto-rescue:BS170 Q1
U 1 1 5D2F88C0
P 5500 4350
AR Path="/5D2F88C0" Ref="Q1"  Part="1" 
AR Path="/5ED17DDD/5D2F88C0" Ref="Q_Inv"  Part="1" 
F 0 "Q_Inv" H 5700 4425 50  0000 L CNN
F 1 "BS170" H 5700 4350 50  0000 L CNN
F 2 "Package_TO_SOT_THT:TO-92L_Inline" H 5700 4275 50  0001 L CIN
F 3 "" H 5500 4350 50  0000 L CNN
	1    5500 4350
	1    0    0    -1  
$EndComp
$Comp
L UB_AT328_proto-rescue:BS170 Q2
U 1 1 5D2F88C1
P 6350 4350
AR Path="/5D2F88C1" Ref="Q2"  Part="1" 
AR Path="/5ED17DDD/5D2F88C1" Ref="Q_TX"  Part="1" 
F 0 "Q_TX" H 6550 4425 50  0000 L CNN
F 1 "BS170" H 6550 4350 50  0000 L CNN
F 2 "Package_TO_SOT_THT:TO-92L_Inline" H 6550 4275 50  0001 L CIN
F 3 "" H 6350 4350 50  0000 L CNN
	1    6350 4350
	1    0    0    -1  
$EndComp
$Comp
L UB_AT328_proto-rescue:R R_TX1
U 1 1 5D2F88C2
P 5000 4400
AR Path="/5D2F88C2" Ref="R_TX1"  Part="1" 
AR Path="/5ED17DDD/5D2F88C2" Ref="R_TX1"  Part="1" 
F 0 "R_TX1" V 5080 4400 50  0000 C CNN
F 1 "R" V 5000 4400 50  0000 C CNN
F 2 "Resistor_THT:R_Axial_DIN0204_L3.6mm_D1.6mm_P2.54mm_Vertical" V 4930 4400 50  0001 C CNN
F 3 "" H 5000 4400 50  0000 C CNN
	1    5000 4400
	0    -1   -1   0   
$EndComp
$Comp
L UB_AT328_proto-rescue:R R_online1
U 1 1 5D2F88C3
P 5000 3950
AR Path="/5D2F88C3" Ref="R_online1"  Part="1" 
AR Path="/5ED17DDD/5D2F88C3" Ref="R_online1"  Part="1" 
F 0 "R_online1" V 5080 3950 50  0000 C CNN
F 1 "R" V 5000 3950 50  0000 C CNN
F 2 "Resistor_THT:R_Axial_DIN0204_L3.6mm_D1.6mm_P2.54mm_Vertical" V 4930 3950 50  0001 C CNN
F 3 "" H 5000 3950 50  0000 C CNN
	1    5000 3950
	0    -1   -1   0   
$EndComp
$Comp
L UB_AT328_proto-rescue:BS170 Q3
U 1 1 5D2F88C4
P 6800 3750
AR Path="/5D2F88C4" Ref="Q3"  Part="1" 
AR Path="/5ED17DDD/5D2F88C4" Ref="Q_RX"  Part="1" 
F 0 "Q_RX" H 7000 3825 50  0000 L CNN
F 1 "BS170" H 7000 3750 50  0000 L CNN
F 2 "Package_TO_SOT_THT:TO-92L_Inline" H 7000 3675 50  0001 L CIN
F 3 "" H 6800 3750 50  0000 L CNN
	1    6800 3750
	1    0    0    -1  
$EndComp
$Comp
L UB_AT328_proto-rescue:R R_in1
U 1 1 5D2F88C5
P 6900 4250
AR Path="/5D2F88C5" Ref="R_in1"  Part="1" 
AR Path="/5ED17DDD/5D2F88C5" Ref="R_in1"  Part="1" 
F 0 "R_in1" V 6980 4250 50  0000 C CNN
F 1 "R" V 6900 4250 50  0000 C CNN
F 2 "Resistor_THT:R_Axial_DIN0204_L3.6mm_D1.6mm_P2.54mm_Vertical" V 6830 4250 50  0001 C CNN
F 3 "" H 6900 4250 50  0000 C CNN
	1    6900 4250
	1    0    0    -1  
$EndComp
Text Notes 8200 7650 0    60   ~ 0
2019/07/17
Text Notes 7350 7500 0    60   ~ 0
Bus connector implemented with FETs, for bus voltage between 7-18V
$Comp
L UB_AT328_proto-rescue:D_TVS D1
U 1 1 5D2F91F9
P 7300 4200
AR Path="/5D2F91F9" Ref="D1"  Part="1" 
AR Path="/5ED17DDD/5D2F91F9" Ref="D1"  Part="1" 
F 0 "D1" H 7300 4300 50  0000 C CNN
F 1 "D_TVS" H 7300 4100 50  0000 C CNN
F 2 "Diode_THT:D_T-1_P2.54mm_Vertical_KathodeUp" H 7300 4200 50  0001 C CNN
F 3 "" H 7300 4200 50  0000 C CNN
	1    7300 4200
	0    1    1    0   
$EndComp
$Comp
L UB_AT328_proto-rescue:Polyfuse F1
U 1 1 5D2F9528
P 8000 3350
AR Path="/5D2F9528" Ref="F1"  Part="1" 
AR Path="/5ED17DDD/5D2F9528" Ref="F1"  Part="1" 
F 0 "F1" V 7900 3350 50  0000 C CNN
F 1 "Polyfuse" V 8100 3350 50  0000 C CNN
F 2 "Diode_THT:D_T-1_P5.08mm_Horizontal" H 8050 3150 50  0001 L CNN
F 3 "" H 8000 3350 50  0001 C CNN
	1    8000 3350
	0    -1   -1   0   
$EndComp
$Comp
L UB_AT328_proto-rescue:R R_sImax1
U 1 1 5ED0E127
P 7600 3350
AR Path="/5ED0E127" Ref="R_sImax1"  Part="1" 
AR Path="/5ED17DDD/5ED0E127" Ref="R_sImax1"  Part="1" 
F 0 "R_sImax1" V 7680 3350 50  0000 C CNN
F 1 "R" V 7600 3350 50  0000 C CNN
F 2 "Resistor_THT:R_Axial_DIN0204_L3.6mm_D1.6mm_P2.54mm_Vertical" V 7530 3350 50  0001 C CNN
F 3 "" H 7600 3350 50  0000 C CNN
	1    7600 3350
	0    -1   -1   0   
$EndComp
$Comp
L UB_AT328_proto-rescue:Jumper_NO_Small JP_USE_FET1
U 1 1 5ED0EF48
P 5000 4150
AR Path="/5ED0EF48" Ref="JP_USE_FET1"  Part="1" 
AR Path="/5ED17DDD/5ED0EF48" Ref="JP_USE_FET1"  Part="1" 
F 0 "JP_USE_FET1" H 5000 4230 50  0000 C CNN
F 1 "Jumper_NO_Small" H 5010 4090 50  0000 C CNN
F 2 "Jumper:SolderJumper-2_P1.3mm_Open_TrianglePad1.0x1.5mm" H 5000 4150 50  0001 C CNN
F 3 "" H 5000 4150 50  0000 C CNN
	1    5000 4150
	1    0    0    -1  
$EndComp
$Comp
L UB_AT328_proto-rescue:Jumper_NO_Small JP_NO_safety1
U 1 1 5ED0F012
P 7750 3600
AR Path="/5ED0F012" Ref="JP_NO_safety1"  Part="1" 
AR Path="/5ED17DDD/5ED0F012" Ref="JP_NO_safety1"  Part="1" 
F 0 "JP_NO_safety1" H 7750 3680 50  0000 C CNN
F 1 "Jumper_NO_Small" H 7760 3540 50  0000 C CNN
F 2 "Jumper:SolderJumper-2_P1.3mm_Open_TrianglePad1.0x1.5mm" H 7750 3600 50  0001 C CNN
F 3 "" H 7750 3600 50  0000 C CNN
	1    7750 3600
	1    0    0    -1  
$EndComp
Wire Wire Line
	4400 4150 4850 4150
Wire Wire Line
	4400 3950 4500 3950
Wire Wire Line
	5150 4400 5250 4400
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
	6150 3950 5600 3950
Connection ~ 6900 4550
Wire Wire Line
	4600 4350 4400 4350
Wire Wire Line
	6450 3350 6450 3800
Wire Wire Line
	6600 3800 6450 3800
Connection ~ 6450 3800
Wire Wire Line
	6900 3550 4500 3550
Wire Wire Line
	4500 3550 4500 3950
Connection ~ 4500 3950
Wire Wire Line
	6900 3950 6900 4000
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
	7300 3350 7300 3600
Connection ~ 7300 3350
Wire Wire Line
	7300 4550 7300 4350
Connection ~ 7300 4550
Wire Wire Line
	4400 4550 5600 4550
Wire Wire Line
	6450 3350 7300 3350
Wire Wire Line
	7750 3350 7850 3350
Wire Wire Line
	8150 3350 8250 3350
Connection ~ 4850 4150
Wire Wire Line
	5100 4150 5250 4150
Wire Wire Line
	5250 4150 5250 4400
Connection ~ 5250 4400
Wire Wire Line
	7300 3600 7650 3600
Connection ~ 7300 3600
Wire Wire Line
	7850 3600 8150 3600
Wire Wire Line
	8150 3600 8150 3350
Wire Wire Line
	5600 3950 5150 3950
Wire Wire Line
	5600 4550 6450 4550
Wire Wire Line
	6450 4550 6900 4550
Wire Wire Line
	6900 4550 7300 4550
Wire Wire Line
	6450 3800 6450 4150
Wire Wire Line
	4500 3950 4850 3950
Wire Wire Line
	6900 4000 6900 4100
Wire Wire Line
	7300 3350 7450 3350
Wire Wire Line
	7300 4550 8250 4550
Wire Wire Line
	4850 4150 4900 4150
Wire Wire Line
	5250 4400 5300 4400
Wire Wire Line
	7300 3600 7300 4050
$EndSCHEMATC
