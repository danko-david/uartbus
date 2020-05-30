EESchema Schematic File Version 4
LIBS:UB_AT328_proto-cache
EELAYER 26 0
EELAYER END
$Descr A4 11693 8268
encoding utf-8
Sheet 1 2
Title ""
Date ""
Rev ""
Comp ""
Comment1 ""
Comment2 ""
Comment3 ""
Comment4 ""
$EndDescr
$Sheet
S 3250 3700 850  600 
U 5ED17DDD
F0 "bus_connector_2_FET" 60
F1 "bus_connector_2_FET.sch" 60
F2 "Online" I L 3250 3850 60 
F3 "TX" I L 3250 3950 60 
F4 "RX" I L 3250 4050 60 
F5 "GND" I L 3250 4150 60 
F6 "Bus_Data" I R 4100 3850 60 
F7 "Bus_Gnd" I R 4100 4150 60 
$EndSheet
$Comp
L UB_AT328_proto-rescue:Screw_Terminal_1x02 J2
U 1 1 5ED1A920
P 7000 5000
F 0 "J2" H 7000 5250 50  0000 C TNN
F 1 "Screw_Terminal_1x02" V 6850 5000 50  0000 C TNN
F 2 "TerminalBlock:TerminalBlock_bornier-2_P5.08mm" H 7000 4775 50  0001 C CNN
F 3 "" H 6975 5000 50  0001 C CNN
	1    7000 5000
	-1   0    0    1   
$EndComp
$Comp
L UB_AT328_proto-rescue:7805 U1
U 1 1 5ED1A9C1
P 7650 4750
F 0 "U1" H 7800 4554 50  0000 C CNN
F 1 "7805" H 7650 4950 50  0000 C CNN
F 2 "Package_TO_SOT_THT:TO-220-3_Vertical" H 7650 4750 50  0001 C CNN
F 3 "" H 7650 4750 50  0000 C CNN
	1    7650 4750
	1    0    0    -1  
$EndComp
$Comp
L Connector:RJ12_Shielded J1
U 1 1 5ED1AA48
P 6600 3950
F 0 "J1" H 6800 4450 50  0000 C CNN
F 1 "RJ12" H 6450 4450 50  0000 C CNN
F 2 "Connector_RJ:RJ12_Amphenol_54601" H 6600 3950 50  0001 C CNN
F 3 "" H 6600 3950 50  0000 C CNN
	1    6600 3950
	-1   0    0    1   
$EndComp
$Comp
L Connector:Conn_01x03_Male Bus_pin1
U 1 1 5ED1AABF
P 6000 3550
F 0 "Bus_pin1" H 6000 3750 50  0000 C CNN
F 1 "CONN_01X03" V 6100 3550 50  0000 C CNN
F 2 "Connector_PinHeader_2.54mm:PinHeader_1x03_P2.54mm_Vertical" H 6000 3550 50  0001 C CNN
F 3 "" H 6000 3550 50  0000 C CNN
	1    6000 3550
	-1   0    0    1   
$EndComp
$Comp
L UB_AT328_proto-rescue:Jumper_NO_Small JP_Supply_bus1
U 1 1 5ED1B525
P 5650 4550
F 0 "JP_Supply_bus1" H 5650 4630 50  0000 C CNN
F 1 "Jumper_NO_Small" H 5660 4490 50  0000 C CNN
F 2 "Jumper:SolderJumper-2_P1.3mm_Open_TrianglePad1.0x1.5mm" H 5650 4550 50  0001 C CNN
F 3 "" H 5650 4550 50  0000 C CNN
	1    5650 4550
	0    -1   -1   0   
$EndComp
$Comp
L UB_AT328_proto-rescue:Jumper_NO_Small JP_Supply_circuit1
U 1 1 5ED1B598
P 5050 4900
F 0 "JP_Supply_circuit1" H 5050 4980 50  0000 C CNN
F 1 "Jumper_NO_Small" H 5060 4840 50  0000 C CNN
F 2 "Jumper:SolderJumper-2_P1.3mm_Open_TrianglePad1.0x1.5mm" H 5050 4900 50  0001 C CNN
F 3 "" H 5050 4900 50  0000 C CNN
	1    5050 4900
	1    0    0    -1  
$EndComp
$Comp
L UB_AT328_proto-rescue:Jumper_NO_Small JP_Supply_regulator1
U 1 1 5ED1B625
P 6200 4900
F 0 "JP_Supply_regulator1" H 6200 4980 50  0000 C CNN
F 1 "Jumper_NO_Small" H 6210 4840 50  0000 C CNN
F 2 "Jumper:SolderJumper-2_P1.3mm_Open_TrianglePad1.0x1.5mm" H 6200 4900 50  0001 C CNN
F 3 "" H 6200 4900 50  0000 C CNN
	1    6200 4900
	1    0    0    -1  
$EndComp
$Comp
L Connector:Conn_01x12_Male P9
U 1 1 5ED1FE59
P 2850 5400
F 0 "P9" H 2850 6050 50  0000 C CNN
F 1 "CONN_01X12" V 2950 5400 50  0000 C CNN
F 2 "Connector_PinHeader_2.54mm:PinHeader_1x12_P2.54mm_Vertical" H 2850 5400 50  0001 C CNN
F 3 "" H 2850 5400 50  0000 C CNN
	1    2850 5400
	0    -1   -1   0   
$EndComp
$Comp
L Connector:Conn_01x12_Female P7
U 1 1 5ED200FC
P 2900 3250
F 0 "P7" H 2900 3900 50  0000 C CNN
F 1 "CONN_01X12" V 3000 3250 50  0000 C CNN
F 2 "Connector_PinHeader_2.54mm:PinHeader_1x12_P2.54mm_Vertical" H 2900 3250 50  0001 C CNN
F 3 "" H 2900 3250 50  0000 C CNN
	1    2900 3250
	0    1    1    0   
$EndComp
$Comp
L Connector:Conn_01x12_Male P6
U 1 1 5ED20181
P 2900 2600
F 0 "P6" H 2900 3250 50  0000 C CNN
F 1 "CONN_01X12" V 3000 2600 50  0000 C CNN
F 2 "Connector_PinHeader_2.54mm:PinHeader_1x12_P2.54mm_Vertical" H 2900 2600 50  0001 C CNN
F 3 "" H 2900 2600 50  0000 C CNN
	1    2900 2600
	0    1    1    0   
$EndComp
$Comp
L UB_AT328_proto-rescue:CONN_01X04 P1
U 1 1 5ED20239
P 2500 4000
F 0 "P1" H 2500 4250 50  0000 C CNN
F 1 "CONN_01X04" V 2600 4000 50  0000 C CNN
F 2 "Connector_PinHeader_2.54mm:PinHeader_1x04_P2.54mm_Vertical" H 2500 4000 50  0001 C CNN
F 3 "" H 2500 4000 50  0000 C CNN
	1    2500 4000
	-1   0    0    1   
$EndComp
$Comp
L Connector:Conn_01x12_Female P8
U 1 1 5ED2097E
P 2850 4650
F 0 "P8" H 2850 5300 50  0000 C CNN
F 1 "CONN_01X12" V 2950 4650 50  0000 C CNN
F 2 "Connector_PinHeader_2.54mm:PinHeader_1x12_P2.54mm_Vertical" H 2850 4650 50  0001 C CNN
F 3 "" H 2850 4650 50  0000 C CNN
	1    2850 4650
	0    -1   -1   0   
$EndComp
$Comp
L Connector:Conn_01x08_Male P5
U 1 1 5ED234D1
P 2750 6300
F 0 "P5" H 2750 6750 50  0000 C CNN
F 1 "CONN_01X08" V 2850 6300 50  0000 C CNN
F 2 "Connector_PinHeader_2.54mm:PinHeader_1x08_P2.54mm_Vertical" H 2750 6300 50  0001 C CNN
F 3 "" H 2750 6300 50  0000 C CNN
	1    2750 6300
	0    1    1    0   
$EndComp
$Comp
L Connector:Conn_01x08_Male P4
U 1 1 5ED235D9
P 2750 5900
F 0 "P4" H 2750 6350 50  0000 C CNN
F 1 "CONN_01X08" V 2850 5900 50  0000 C CNN
F 2 "Connector_PinHeader_2.54mm:PinHeader_1x08_P2.54mm_Vertical" H 2750 5900 50  0001 C CNN
F 3 "" H 2750 5900 50  0000 C CNN
	1    2750 5900
	0    1    1    0   
$EndComp
$Comp
L Connector:Conn_01x08_Male P3
U 1 1 5ED23806
P 2600 2150
F 0 "P3" H 2600 2600 50  0000 C CNN
F 1 "CONN_01X08" V 2700 2150 50  0000 C CNN
F 2 "Connector_PinHeader_2.54mm:PinHeader_1x08_P2.54mm_Vertical" H 2600 2150 50  0001 C CNN
F 3 "" H 2600 2150 50  0000 C CNN
	1    2600 2150
	0    -1   -1   0   
$EndComp
$Comp
L Connector:Conn_01x08_Male P2
U 1 1 5ED2387B
P 2600 1650
F 0 "P2" H 2600 2100 50  0000 C CNN
F 1 "CONN_01X08" V 2700 1650 50  0000 C CNN
F 2 "Connector_PinHeader_2.54mm:PinHeader_1x08_P2.54mm_Vertical" H 2600 1650 50  0001 C CNN
F 3 "" H 2600 1650 50  0000 C CNN
	1    2600 1650
	0    -1   -1   0   
$EndComp
Wire Wire Line
	5650 4650 5650 4900
Wire Wire Line
	5150 4900 5650 4900
Connection ~ 5650 4900
Wire Wire Line
	6300 4900 6650 4900
Wire Wire Line
	6650 4700 6650 4900
Connection ~ 6650 4900
Wire Wire Line
	7650 5500 6700 5500
Wire Wire Line
	5800 5500 5800 5100
Wire Wire Line
	6800 5100 5800 5100
Connection ~ 5800 5100
Wire Wire Line
	5650 4450 5650 3950
Wire Wire Line
	3250 4150 3000 4150
Wire Wire Line
	2950 3850 3250 3850
Wire Wire Line
	2950 3850 2950 4050
Wire Wire Line
	2950 4050 2700 4050
Wire Wire Line
	3250 3950 2900 3950
Wire Wire Line
	2900 3950 2900 3850
Wire Wire Line
	2900 3850 2700 3850
Wire Wire Line
	2700 3950 2800 3950
Wire Wire Line
	2800 3950 2800 4000
Wire Wire Line
	2800 4000 3100 4000
Wire Wire Line
	3100 4000 3100 4050
Wire Wire Line
	3100 4050 3250 4050
Wire Wire Line
	1850 1450 1850 6500
Wire Wire Line
	2100 1950 2100 2950
Wire Wire Line
	3450 4850 3450 4900
Wire Wire Line
	3350 4850 3350 5150
Wire Wire Line
	3250 4850 3250 5200
Wire Wire Line
	3150 4850 3150 5050
Wire Wire Line
	3050 4850 3050 5200
Wire Wire Line
	2950 4850 2950 5200
Wire Wire Line
	2850 4850 2850 5200
Wire Wire Line
	2750 4850 2750 5200
Wire Wire Line
	2650 4850 2650 5200
Wire Wire Line
	2550 4850 2550 5200
Wire Wire Line
	2450 4850 2450 5200
Wire Wire Line
	2350 4850 2350 5200
Wire Wire Line
	4950 4900 3450 4900
Connection ~ 3450 4900
Wire Wire Line
	3350 5150 3650 5150
Wire Wire Line
	3650 5150 3650 6100
Connection ~ 3350 5150
$Comp
L UB_AT328_proto-rescue:Jumper_NO_Small JP_Supply_VCC1
U 1 1 5ED26C8F
P 4150 5050
F 0 "JP_Supply_VCC1" H 4150 5130 50  0000 C CNN
F 1 "Jumper_NO_Small" H 4160 4990 50  0000 C CNN
F 2 "Jumper:SolderJumper-2_P1.3mm_Open_TrianglePad1.0x1.5mm" H 4150 5050 50  0001 C CNN
F 3 "" H 4150 5050 50  0000 C CNN
	1    4150 5050
	1    0    0    -1  
$EndComp
Wire Wire Line
	4050 5050 3150 5050
Connection ~ 3150 5050
Wire Wire Line
	4250 5050 4250 6500
Connection ~ 4250 6500
Wire Wire Line
	2300 2800 2300 3050
Wire Wire Line
	2400 2800 2400 3050
Wire Wire Line
	2500 2800 2500 3050
Wire Wire Line
	2600 2800 2600 3050
Wire Wire Line
	2700 2800 2700 3050
Wire Wire Line
	2800 2800 2800 3050
Wire Wire Line
	2900 2800 2900 3050
Wire Wire Line
	3000 2800 3000 3050
Wire Wire Line
	3100 2800 3100 2950
Wire Wire Line
	3200 2800 3200 3050
Wire Wire Line
	3300 2800 3300 3050
Wire Wire Line
	3400 2800 3400 3050
Connection ~ 2100 2950
Connection ~ 3100 2950
$Comp
L Connector:Barrel_Jack_Switch CON1
U 1 1 5ED29C4E
P 7100 5800
F 0 "CON1" H 7100 6050 50  0000 C CNN
F 1 "BARREL_JACK" H 7100 5600 50  0000 C CNN
F 2 "Connector_BarrelJack:BarrelJack_CUI_PJ-063AH_Horizontal" H 7100 5800 50  0001 C CNN
F 3 "" H 7100 5800 50  0000 C CNN
	1    7100 5800
	-1   0    0    1   
$EndComp
Wire Wire Line
	6700 5700 6700 5500
Connection ~ 6700 5500
Wire Wire Line
	5650 4900 6100 4900
Wire Wire Line
	6650 4900 6800 4900
Wire Wire Line
	6650 4900 6650 5900
Wire Wire Line
	5800 5100 5800 4150
Wire Wire Line
	3450 4900 3450 5200
Wire Wire Line
	3350 5150 3350 5200
Wire Wire Line
	3150 5050 3150 5200
Wire Wire Line
	4250 6500 8050 6500
Wire Wire Line
	3100 2950 3100 3050
Wire Wire Line
	6700 5500 5800 5500
Wire Wire Line
	1850 1450 2300 1450
Wire Wire Line
	2900 1450 3000 1450
Wire Wire Line
	2100 1950 2300 1950
Wire Wire Line
	2900 1950 3000 1950
Wire Wire Line
	1850 6500 2350 6500
Wire Wire Line
	2100 6100 2350 6100
Wire Wire Line
	3050 6500 4250 6500
Wire Wire Line
	3050 6100 3650 6100
Wire Wire Line
	2100 2950 2100 6100
Wire Wire Line
	3050 6100 2950 6100
Connection ~ 3050 6100
Wire Wire Line
	2850 6100 2950 6100
Connection ~ 2950 6100
Wire Wire Line
	2750 6100 2850 6100
Connection ~ 2850 6100
Wire Wire Line
	2750 6100 2650 6100
Connection ~ 2750 6100
Wire Wire Line
	2650 6100 2550 6100
Connection ~ 2650 6100
Wire Wire Line
	2550 6100 2450 6100
Connection ~ 2550 6100
Wire Wire Line
	2350 6100 2450 6100
Connection ~ 2350 6100
Connection ~ 2450 6100
Wire Wire Line
	2450 6500 2350 6500
Connection ~ 2350 6500
Wire Wire Line
	2450 6500 2550 6500
Connection ~ 2450 6500
Wire Wire Line
	2550 6500 2650 6500
Connection ~ 2550 6500
Wire Wire Line
	2750 6500 2650 6500
Connection ~ 2650 6500
Wire Wire Line
	2850 6500 2750 6500
Connection ~ 2750 6500
Wire Wire Line
	2950 6500 2850 6500
Connection ~ 2850 6500
Wire Wire Line
	3050 6500 2950 6500
Connection ~ 3050 6500
Connection ~ 2950 6500
Wire Wire Line
	2300 1450 2400 1450
Connection ~ 2300 1450
Wire Wire Line
	2500 1450 2600 1450
Wire Wire Line
	2700 1450 2800 1450
Wire Wire Line
	2800 1450 2900 1450
Connection ~ 2800 1450
Connection ~ 2900 1450
Wire Wire Line
	2700 1450 2600 1450
Connection ~ 2700 1450
Connection ~ 2600 1450
Wire Wire Line
	2500 1450 2400 1450
Connection ~ 2500 1450
Connection ~ 2400 1450
Wire Wire Line
	2300 1950 2400 1950
Connection ~ 2300 1950
Wire Wire Line
	2500 1950 2600 1950
Wire Wire Line
	2700 1950 2800 1950
Wire Wire Line
	2900 1950 2800 1950
Connection ~ 2900 1950
Connection ~ 2800 1950
Wire Wire Line
	2700 1950 2600 1950
Connection ~ 2700 1950
Connection ~ 2600 1950
Wire Wire Line
	2500 1950 2400 1950
Connection ~ 2500 1950
Connection ~ 2400 1950
Wire Wire Line
	6700 5700 6800 5700
Wire Wire Line
	6650 5900 6800 5900
NoConn ~ 6600 3550
NoConn ~ 6200 3750
NoConn ~ 6200 4050
NoConn ~ 6200 4250
NoConn ~ 6800 5800
Wire Wire Line
	2100 2950 3100 2950
Wire Wire Line
	4100 4150 4700 4150
Wire Wire Line
	4100 3850 5550 3850
Wire Wire Line
	5650 3950 6200 3950
Connection ~ 5650 3950
Wire Wire Line
	5800 4150 6200 4150
Connection ~ 5800 4150
Wire Wire Line
	5800 3650 5800 4150
Wire Wire Line
	5800 3550 5650 3550
Wire Wire Line
	5650 3550 5650 3950
Wire Wire Line
	5800 3450 5550 3450
Wire Wire Line
	5550 3450 5550 3850
Connection ~ 5550 3850
Wire Wire Line
	5550 3850 6200 3850
$Comp
L Connector_Generic:Conn_01x14 J3
U 1 1 5ED985C0
P 6850 1800
F 0 "J3" H 6930 1792 50  0000 L CNN
F 1 "Conn_01x14" H 6930 1701 50  0000 L CNN
F 2 "Connector_PinSocket_2.54mm:PinSocket_1x14_P2.54mm_Vertical" H 6850 1800 50  0001 C CNN
F 3 "~" H 6850 1800 50  0001 C CNN
	1    6850 1800
	1    0    0    -1  
$EndComp
$Comp
L Connector_Generic:Conn_01x14 J4
U 1 1 5ED986CC
P 7450 1800
F 0 "J4" H 7530 1792 50  0000 L CNN
F 1 "Conn_01x14" H 7530 1701 50  0000 L CNN
F 2 "Connector_PinSocket_2.54mm:PinSocket_1x14_P2.54mm_Vertical" H 7450 1800 50  0001 C CNN
F 3 "~" H 7450 1800 50  0001 C CNN
	1    7450 1800
	1    0    0    -1  
$EndComp
$Comp
L Connector_Generic:Conn_01x14 J5
U 1 1 5ED98730
P 7900 1800
F 0 "J5" H 7980 1792 50  0000 L CNN
F 1 "Conn_01x14" H 7980 1701 50  0000 L CNN
F 2 "Connector_PinSocket_2.54mm:PinSocket_1x14_P2.54mm_Vertical" H 7900 1800 50  0001 C CNN
F 3 "~" H 7900 1800 50  0001 C CNN
	1    7900 1800
	1    0    0    -1  
$EndComp
$Comp
L Connector_Generic:Conn_01x14 J6
U 1 1 5ED987AB
P 8400 1800
F 0 "J6" H 8480 1792 50  0000 L CNN
F 1 "Conn_01x14" H 8480 1701 50  0000 L CNN
F 2 "Connector_PinSocket_2.54mm:PinSocket_1x14_P2.54mm_Vertical" H 8400 1800 50  0001 C CNN
F 3 "~" H 8400 1800 50  0001 C CNN
	1    8400 1800
	1    0    0    -1  
$EndComp
$Comp
L Connector_Generic:Conn_01x14 J7
U 1 1 5ED98831
P 8950 1800
F 0 "J7" H 9030 1792 50  0000 L CNN
F 1 "Conn_01x14" H 9030 1701 50  0000 L CNN
F 2 "Connector_PinSocket_2.54mm:PinSocket_1x14_P2.54mm_Vertical" H 8950 1800 50  0001 C CNN
F 3 "~" H 8950 1800 50  0001 C CNN
	1    8950 1800
	1    0    0    -1  
$EndComp
Wire Wire Line
	6650 4700 7250 4700
Wire Wire Line
	8050 4700 8050 6500
Wire Wire Line
	7650 5000 7650 5500
Wire Wire Line
	3000 4150 3000 4450
Wire Wire Line
	3000 4450 4700 4450
Wire Wire Line
	4700 4450 4700 4150
Connection ~ 3000 4150
Wire Wire Line
	3000 4150 2700 4150
Connection ~ 4700 4150
Wire Wire Line
	4700 4150 5800 4150
$EndSCHEMATC
