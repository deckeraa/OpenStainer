EESchema Schematic File Version 4
EELAYER 30 0
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
L pi_board-rescue:Conn_02x20_Odd_Even J1
U 1 1 5E29DD84
P 5650 3750
F 0 "J1" H 5700 4750 50  0000 C CNN
F 1 "Conn_02x20_Odd_Even" H 5700 2650 50  0000 C CNN
F 2 "Connector_PinHeader_2.54mm:PinHeader_2x20_P2.54mm_Vertical" H 5650 3750 50  0001 C CNN
F 3 "" H 5650 3750 50  0001 C CNN
	1    5650 3750
	1    0    0    -1  
$EndComp
Text Label 5250 2850 0    60   ~ 0
3v3
Text Label 5150 2950 0    60   ~ 0
GPIO2
Text Label 5150 3050 0    60   ~ 0
GPIO3
Text Label 5150 3150 0    60   ~ 0
GPIO4
Text Label 5250 3250 0    60   ~ 0
GND
Text Label 5100 3350 0    60   ~ 0
GPIO17
Text Label 5100 3450 0    60   ~ 0
GPIO27
Text Label 5100 3550 0    60   ~ 0
GPIO22
Text Label 5250 3650 0    60   ~ 0
3v3
Text Label 5100 3750 0    60   ~ 0
GPIO10
Text Label 5100 3850 0    60   ~ 0
GPIO9
Text Label 5100 3950 0    60   ~ 0
GPIO11
Text Label 5200 4050 0    60   ~ 0
GND
Text Label 5100 4150 0    60   ~ 0
GPIO0
Text Label 5100 4250 0    60   ~ 0
GPIO5
Text Label 5100 4350 0    60   ~ 0
GPIO6
Text Label 5100 4450 0    60   ~ 0
GPIO13
Text Label 5100 4550 0    60   ~ 0
GPIO19
Text Label 5100 4650 0    60   ~ 0
GPIO26
Text Label 5200 4750 0    60   ~ 0
GND
Text Label 6000 2850 0    60   ~ 0
5v
Text Label 6000 2950 0    60   ~ 0
5v
Text Label 6000 3050 0    60   ~ 0
GND
Text Label 6000 3150 0    60   ~ 0
GPIO14
Text Label 6000 3250 0    60   ~ 0
GPIO15
Text Label 6000 3350 0    60   ~ 0
GPIO18
Text Label 6000 3450 0    60   ~ 0
GND
Text Label 6000 3550 0    60   ~ 0
GPIO23
Text Label 6000 3650 0    60   ~ 0
GPIO24
Text Label 6000 3750 0    60   ~ 0
GND
Text Label 6000 3850 0    60   ~ 0
GPIO25
Text Label 6000 3950 0    60   ~ 0
GPIO8
Text Label 6000 4050 0    60   ~ 0
GPIO7
Text Label 6000 4150 0    60   ~ 0
GPIO1
Text Label 6000 4250 0    60   ~ 0
GND
Text Label 6000 4350 0    60   ~ 0
GPIO12
Text Label 6000 4450 0    60   ~ 0
GND
Text Label 6000 4550 0    60   ~ 0
GPIO16
Text Label 6000 4650 0    60   ~ 0
GPIO20
Text Label 6000 4750 0    60   ~ 0
GPIO21
$Comp
L pi_board-rescue:Conn_01x03_Male J4
U 1 1 5E29F8C2
P 7550 2950
F 0 "J4" H 7550 3150 50  0000 C CNN
F 1 "X Limit Switch" H 7550 2750 50  0000 C CNN
F 2 "Connector_PinHeader_2.54mm:PinHeader_1x03_P2.54mm_Vertical" H 7550 2950 50  0001 C CNN
F 3 "" H 7550 2950 50  0001 C CNN
	1    7550 2950
	-1   0    0    1   
$EndComp
Text Label 7450 2850 0    60   ~ 0
V
Text Label 7450 2950 0    60   ~ 0
S
Text Label 7450 3050 0    60   ~ 0
G
Wire Wire Line
	5950 3150 7300 3150
Wire Wire Line
	7300 3150 7300 2950
Wire Wire Line
	7300 2950 7350 2950
$Comp
L pi_board-rescue:Conn_01x03_Male J5
U 1 1 5E2A207A
P 7550 3450
F 0 "J5" H 7550 3650 50  0000 C CNN
F 1 "Z Limit Switch" H 7550 3250 50  0000 C CNN
F 2 "Connector_PinHeader_2.54mm:PinHeader_1x03_P2.54mm_Vertical" H 7550 3450 50  0001 C CNN
F 3 "" H 7550 3450 50  0001 C CNN
	1    7550 3450
	-1   0    0    1   
$EndComp
Text Label 7450 3350 0    60   ~ 0
V
Text Label 7450 3450 0    60   ~ 0
S
Text Label 7450 3550 0    60   ~ 0
G
Wire Wire Line
	7250 2850 7250 3350
Wire Wire Line
	7250 3350 7350 3350
Connection ~ 7250 2850
Wire Wire Line
	5950 3250 7100 3250
Wire Wire Line
	7100 3250 7100 3450
Wire Wire Line
	7100 3450 7350 3450
Wire Wire Line
	7250 2850 7350 2850
$Comp
L Transistor_BJT:MMBT3904 Q8
U 1 1 5E2A3301
P 7250 4300
F 0 "Q8" H 7441 4346 50  0000 L CNN
F 1 "MMBT3904" H 7441 4255 50  0000 L CNN
F 2 "Package_TO_SOT_SMD:SOT-23" H 7450 4225 50  0001 L CIN
F 3 "https://www.fairchildsemi.com/datasheets/2N/2N3904.pdf" H 7250 4300 50  0001 L CNN
	1    7250 4300
	1    0    0    -1  
$EndComp
$Comp
L Connector:Conn_01x04_Female J6
U 1 1 5E2A4BD1
P 7850 3850
F 0 "J6" H 7878 3826 50  0000 L CNN
F 1 "Green Button" H 7878 3735 50  0000 L CNN
F 2 "Connector_PinHeader_2.54mm:PinHeader_1x04_P2.54mm_Vertical" H 7850 3850 50  0001 C CNN
F 3 "~" H 7850 3850 50  0001 C CNN
	1    7850 3850
	1    0    0    -1  
$EndComp
Wire Wire Line
	5450 2850 5450 2600
Wire Wire Line
	5450 2600 6450 2600
Wire Wire Line
	6900 2600 6900 3750
Wire Wire Line
	6900 3750 7650 3750
Wire Wire Line
	5950 2850 6250 2850
Wire Wire Line
	5950 3350 7000 3350
Wire Wire Line
	7000 3350 7000 3850
Wire Wire Line
	7000 3850 7650 3850
Wire Wire Line
	6800 2850 6800 3950
Connection ~ 6800 2850
Wire Wire Line
	6800 2850 7250 2850
Wire Wire Line
	5950 3550 6700 3550
Wire Wire Line
	6700 3550 6700 4150
Wire Wire Line
	6700 4150 7050 4150
$Comp
L Connector:Conn_01x04_Female J7
U 1 1 5E2BD908
P 7900 4950
F 0 "J7" H 7928 4926 50  0000 L CNN
F 1 "Red Button" H 7928 4835 50  0000 L CNN
F 2 "Connector_PinHeader_2.54mm:PinHeader_1x04_P2.54mm_Vertical" H 7900 4950 50  0001 C CNN
F 3 "~" H 7900 4950 50  0001 C CNN
	1    7900 4950
	1    0    0    -1  
$EndComp
$Comp
L Transistor_BJT:MMBT3904 Q9
U 1 1 5E2BE19A
P 7250 5450
F 0 "Q9" H 7441 5496 50  0000 L CNN
F 1 "MMBT3904" H 7441 5405 50  0000 L CNN
F 2 "Package_TO_SOT_SMD:SOT-23" H 7450 5375 50  0001 L CIN
F 3 "https://www.fairchildsemi.com/datasheets/2N/2N3904.pdf" H 7250 5450 50  0001 L CNN
	1    7250 5450
	1    0    0    -1  
$EndComp
Wire Wire Line
	6800 3950 6800 5050
Connection ~ 6800 3950
Wire Wire Line
	5950 3650 6650 3650
Wire Wire Line
	6650 3650 6650 5250
Wire Wire Line
	6650 5250 7050 5250
Wire Wire Line
	6900 3750 6900 4850
Wire Wire Line
	6900 4850 7700 4850
Connection ~ 6900 3750
Wire Wire Line
	6500 4950 6500 3850
Wire Wire Line
	6500 3850 5950 3850
$Comp
L Connector:Conn_01x04_Male J3
U 1 1 5E2CA8A2
P 2350 2950
F 0 "J3" H 2458 3231 50  0000 C CNN
F 1 "X Axis" H 2458 3140 50  0000 C CNN
F 2 "Connector_PinHeader_2.54mm:PinHeader_1x04_P2.54mm_Vertical" H 2350 2950 50  0001 C CNN
F 3 "~" H 2350 2950 50  0001 C CNN
	1    2350 2950
	1    0    0    -1  
$EndComp
Text Label 2050 2850 0    50   ~ 0
ENA
Text Label 2050 2950 0    50   ~ 0
OPTO
Text Label 2050 3050 0    50   ~ 0
DIR
Text Label 2050 3150 0    50   ~ 0
PUL
$Comp
L Transistor_FET:BSS138 Q6
U 1 1 5E2D2B42
P 3300 2850
F 0 "Q6" V 3642 2850 50  0000 C CNN
F 1 "BSS138" V 3551 2850 50  0000 C CNN
F 2 "Package_TO_SOT_SMD:SOT-23" H 3500 2775 50  0001 L CIN
F 3 "https://www.fairchildsemi.com/datasheets/BS/BSS138.pdf" H 3300 2850 50  0001 L CNN
	1    3300 2850
	0    -1   -1   0   
$EndComp
Connection ~ 5450 2850
Wire Wire Line
	5450 2850 5500 2850
Wire Wire Line
	3600 2850 3600 3050
Wire Wire Line
	3600 3050 3500 3050
$Comp
L Device:R R12
U 1 1 5E2DBACE
P 3500 2900
F 0 "R12" H 3570 2946 50  0000 L CNN
F 1 "10k" H 3570 2855 50  0000 L CNN
F 2 "Resistor_SMD:R_1206_3216Metric_Pad1.42x1.75mm_HandSolder" V 3430 2900 50  0001 C CNN
F 3 "~" H 3500 2900 50  0001 C CNN
	1    3500 2900
	1    0    0    -1  
$EndComp
Connection ~ 3500 3050
Wire Wire Line
	3500 3050 3300 3050
$Comp
L Device:R R6
U 1 1 5E2DC46D
P 3100 2600
F 0 "R6" H 3170 2646 50  0000 L CNN
F 1 "10k" H 3170 2555 50  0000 L CNN
F 2 "Resistor_SMD:R_1206_3216Metric_Pad1.42x1.75mm_HandSolder" V 3030 2600 50  0001 C CNN
F 3 "~" H 3100 2600 50  0001 C CNN
	1    3100 2600
	1    0    0    -1  
$EndComp
Wire Wire Line
	6250 2850 6250 2400
Connection ~ 6250 2850
Wire Wire Line
	6250 2850 6800 2850
Wire Wire Line
	3250 2750 3100 2750
Wire Wire Line
	2850 2750 2850 2850
Wire Wire Line
	2850 2850 2550 2850
Connection ~ 3100 2750
Wire Wire Line
	3100 2750 2850 2750
Wire Wire Line
	4800 2400 4800 2300
Wire Wire Line
	3100 2300 3100 2450
$Comp
L Transistor_FET:BSS138 Q1
U 1 1 5E2F6D0B
P 3250 3700
F 0 "Q1" V 3592 3700 50  0000 C CNN
F 1 "BSS138" V 3501 3700 50  0000 C CNN
F 2 "Package_TO_SOT_SMD:SOT-23" H 3450 3625 50  0001 L CIN
F 3 "https://www.fairchildsemi.com/datasheets/BS/BSS138.pdf" H 3250 3700 50  0001 L CNN
	1    3250 3700
	0    -1   -1   0   
$EndComp
Wire Wire Line
	3550 3700 3550 3900
Wire Wire Line
	3550 3900 3450 3900
$Comp
L Device:R R7
U 1 1 5E2F6D13
P 3450 3750
F 0 "R7" H 3520 3796 50  0000 L CNN
F 1 "10k" H 3520 3705 50  0000 L CNN
F 2 "Resistor_SMD:R_1206_3216Metric_Pad1.42x1.75mm_HandSolder" V 3380 3750 50  0001 C CNN
F 3 "~" H 3450 3750 50  0001 C CNN
	1    3450 3750
	1    0    0    -1  
$EndComp
Connection ~ 3450 3900
Wire Wire Line
	3450 3900 3250 3900
$Comp
L Device:R R1
U 1 1 5E2F6D1B
P 3050 3450
F 0 "R1" H 3120 3496 50  0000 L CNN
F 1 "10k" H 3120 3405 50  0000 L CNN
F 2 "Resistor_SMD:R_1206_3216Metric_Pad1.42x1.75mm_HandSolder" V 2980 3450 50  0001 C CNN
F 3 "~" H 3050 3450 50  0001 C CNN
	1    3050 3450
	1    0    0    -1  
$EndComp
Wire Wire Line
	3200 3600 3050 3600
Wire Wire Line
	2800 3600 2800 3700
Wire Wire Line
	2800 3700 2500 3700
Connection ~ 3050 3600
Wire Wire Line
	3050 3600 2800 3600
Wire Wire Line
	3050 3150 3050 3300
$Comp
L Transistor_FET:BSS138 Q2
U 1 1 5E2FA712
P 3250 4400
F 0 "Q2" V 3592 4400 50  0000 C CNN
F 1 "BSS138" V 3501 4400 50  0000 C CNN
F 2 "Package_TO_SOT_SMD:SOT-23" H 3450 4325 50  0001 L CIN
F 3 "https://www.fairchildsemi.com/datasheets/BS/BSS138.pdf" H 3250 4400 50  0001 L CNN
	1    3250 4400
	0    -1   -1   0   
$EndComp
Wire Wire Line
	3550 4400 3550 4600
Wire Wire Line
	3550 4600 3450 4600
$Comp
L Device:R R8
U 1 1 5E2FA71A
P 3450 4450
F 0 "R8" H 3520 4496 50  0000 L CNN
F 1 "10k" H 3520 4405 50  0000 L CNN
F 2 "Resistor_SMD:R_1206_3216Metric_Pad1.42x1.75mm_HandSolder" V 3380 4450 50  0001 C CNN
F 3 "~" H 3450 4450 50  0001 C CNN
	1    3450 4450
	1    0    0    -1  
$EndComp
Connection ~ 3450 4600
Wire Wire Line
	3450 4600 3250 4600
$Comp
L Device:R R2
U 1 1 5E2FA722
P 3050 4150
F 0 "R2" H 3120 4196 50  0000 L CNN
F 1 "10k" H 3120 4105 50  0000 L CNN
F 2 "Resistor_SMD:R_1206_3216Metric_Pad1.42x1.75mm_HandSolder" V 2980 4150 50  0001 C CNN
F 3 "~" H 3050 4150 50  0001 C CNN
	1    3050 4150
	1    0    0    -1  
$EndComp
Wire Wire Line
	3200 4300 3050 4300
Wire Wire Line
	2800 4300 2800 4400
Connection ~ 3050 4300
Wire Wire Line
	3050 4300 2800 4300
Wire Wire Line
	3050 3850 3050 4000
$Comp
L Transistor_FET:BSS138 Q3
U 1 1 5E30143E
P 3250 5200
F 0 "Q3" V 3592 5200 50  0000 C CNN
F 1 "BSS138" V 3501 5200 50  0000 C CNN
F 2 "Package_TO_SOT_SMD:SOT-23" H 3450 5125 50  0001 L CIN
F 3 "https://www.fairchildsemi.com/datasheets/BS/BSS138.pdf" H 3250 5200 50  0001 L CNN
	1    3250 5200
	0    -1   -1   0   
$EndComp
Wire Wire Line
	3550 5200 3550 5400
Wire Wire Line
	3550 5400 3450 5400
$Comp
L Device:R R9
U 1 1 5E301446
P 3450 5250
F 0 "R9" H 3520 5296 50  0000 L CNN
F 1 "10k" H 3520 5205 50  0000 L CNN
F 2 "Resistor_SMD:R_1206_3216Metric_Pad1.42x1.75mm_HandSolder" V 3380 5250 50  0001 C CNN
F 3 "~" H 3450 5250 50  0001 C CNN
	1    3450 5250
	1    0    0    -1  
$EndComp
Connection ~ 3450 5400
Wire Wire Line
	3450 5400 3250 5400
$Comp
L Device:R R3
U 1 1 5E30144E
P 3050 4950
F 0 "R3" H 3120 4996 50  0000 L CNN
F 1 "10k" H 3120 4905 50  0000 L CNN
F 2 "Resistor_SMD:R_1206_3216Metric_Pad1.42x1.75mm_HandSolder" V 2980 4950 50  0001 C CNN
F 3 "~" H 3050 4950 50  0001 C CNN
	1    3050 4950
	1    0    0    -1  
$EndComp
Wire Wire Line
	3550 5200 4000 5200
Wire Wire Line
	3200 5100 3050 5100
Connection ~ 3050 5100
Wire Wire Line
	3050 4650 3050 4800
$Comp
L Transistor_FET:BSS138 Q4
U 1 1 5E3072FA
P 3250 6050
F 0 "Q4" V 3592 6050 50  0000 C CNN
F 1 "BSS138" V 3501 6050 50  0000 C CNN
F 2 "Package_TO_SOT_SMD:SOT-23" H 3450 5975 50  0001 L CIN
F 3 "https://www.fairchildsemi.com/datasheets/BS/BSS138.pdf" H 3250 6050 50  0001 L CNN
	1    3250 6050
	0    -1   -1   0   
$EndComp
Wire Wire Line
	3550 6050 3550 6250
Wire Wire Line
	3550 6250 3450 6250
$Comp
L Device:R R10
U 1 1 5E307302
P 3450 6100
F 0 "R10" H 3520 6146 50  0000 L CNN
F 1 "10k" H 3520 6055 50  0000 L CNN
F 2 "Resistor_SMD:R_1206_3216Metric_Pad1.42x1.75mm_HandSolder" V 3380 6100 50  0001 C CNN
F 3 "~" H 3450 6100 50  0001 C CNN
	1    3450 6100
	1    0    0    -1  
$EndComp
Connection ~ 3450 6250
Wire Wire Line
	3450 6250 3250 6250
$Comp
L Device:R R4
U 1 1 5E30730A
P 3050 5800
F 0 "R4" H 3120 5846 50  0000 L CNN
F 1 "10k" H 3120 5755 50  0000 L CNN
F 2 "Resistor_SMD:R_1206_3216Metric_Pad1.42x1.75mm_HandSolder" V 2980 5800 50  0001 C CNN
F 3 "~" H 3050 5800 50  0001 C CNN
	1    3050 5800
	1    0    0    -1  
$EndComp
Wire Wire Line
	3550 6050 4000 6050
Wire Wire Line
	3200 5950 3050 5950
Wire Wire Line
	2800 5950 2800 6050
Wire Wire Line
	2800 6050 2500 6050
Connection ~ 3050 5950
Wire Wire Line
	3050 5950 2800 5950
Wire Wire Line
	3050 5500 3050 5650
$Comp
L Transistor_FET:BSS138 Q5
U 1 1 5E30D328
P 3250 6850
F 0 "Q5" V 3592 6850 50  0000 C CNN
F 1 "BSS138" V 3501 6850 50  0000 C CNN
F 2 "Package_TO_SOT_SMD:SOT-23" H 3450 6775 50  0001 L CIN
F 3 "https://www.fairchildsemi.com/datasheets/BS/BSS138.pdf" H 3250 6850 50  0001 L CNN
	1    3250 6850
	0    -1   -1   0   
$EndComp
Wire Wire Line
	3550 6850 3550 7050
Wire Wire Line
	3550 7050 3450 7050
$Comp
L Device:R R11
U 1 1 5E30D330
P 3450 6900
F 0 "R11" H 3520 6946 50  0000 L CNN
F 1 "10k" H 3520 6855 50  0000 L CNN
F 2 "Resistor_SMD:R_1206_3216Metric_Pad1.42x1.75mm_HandSolder" V 3380 6900 50  0001 C CNN
F 3 "~" H 3450 6900 50  0001 C CNN
	1    3450 6900
	1    0    0    -1  
$EndComp
Connection ~ 3450 7050
Wire Wire Line
	3450 7050 3250 7050
$Comp
L Device:R R5
U 1 1 5E30D338
P 3050 6600
F 0 "R5" H 3120 6646 50  0000 L CNN
F 1 "10k" H 3120 6555 50  0000 L CNN
F 2 "Resistor_SMD:R_1206_3216Metric_Pad1.42x1.75mm_HandSolder" V 2980 6600 50  0001 C CNN
F 3 "~" H 3050 6600 50  0001 C CNN
	1    3050 6600
	1    0    0    -1  
$EndComp
Wire Wire Line
	3550 6850 4000 6850
Wire Wire Line
	3200 6750 3050 6750
Wire Wire Line
	2800 6750 2800 6850
Connection ~ 3050 6750
Wire Wire Line
	3050 6750 2800 6750
Wire Wire Line
	3050 6300 3050 6450
$Comp
L Connector:Conn_01x04_Male J2
U 1 1 5E325D6C
P 1850 5200
F 0 "J2" H 1958 5481 50  0000 C CNN
F 1 "Z Axis" H 1958 5390 50  0000 C CNN
F 2 "Connector_PinHeader_2.54mm:PinHeader_1x04_P2.54mm_Vertical" H 1850 5200 50  0001 C CNN
F 3 "~" H 1850 5200 50  0001 C CNN
	1    1850 5200
	1    0    0    -1  
$EndComp
Text Label 1550 5100 0    50   ~ 0
ENA
Text Label 1550 5200 0    50   ~ 0
OPTO
Text Label 1550 5300 0    50   ~ 0
DIR
Text Label 1550 5400 0    50   ~ 0
PUL
Wire Wire Line
	4800 2400 5000 2400
Wire Wire Line
	5000 2400 5000 1900
Wire Wire Line
	5000 1900 1750 1900
Wire Wire Line
	1750 1900 1750 3350
Wire Wire Line
	1750 4850 2150 4850
Wire Wire Line
	2150 4850 2150 5200
Wire Wire Line
	2150 5200 2050 5200
Connection ~ 5000 2400
Wire Wire Line
	5000 2400 6250 2400
Wire Wire Line
	1750 3350 2650 3350
Wire Wire Line
	2650 3350 2650 2950
Wire Wire Line
	2650 2950 2550 2950
Connection ~ 1750 3350
Wire Wire Line
	1750 3350 1750 4850
Wire Wire Line
	2500 3700 2500 3050
Wire Wire Line
	2500 3050 2550 3050
Wire Wire Line
	2550 3150 2550 4400
Wire Wire Line
	2550 4400 2800 4400
Wire Wire Line
	3750 3150 3750 2300
Connection ~ 3750 3150
Wire Wire Line
	3750 3150 3050 3150
Connection ~ 3750 2300
Wire Wire Line
	3750 2300 3100 2300
Wire Wire Line
	3550 4400 4000 4400
Wire Wire Line
	3750 3150 3750 3850
Connection ~ 3750 3850
Wire Wire Line
	3750 3850 3050 3850
Wire Wire Line
	3750 3850 3750 4650
Connection ~ 3750 4650
Wire Wire Line
	3750 4650 3050 4650
Wire Wire Line
	3750 4650 3750 5500
Connection ~ 3750 5500
Wire Wire Line
	3750 5500 3050 5500
Wire Wire Line
	3750 5500 3750 6300
Wire Wire Line
	3750 6300 3050 6300
Wire Wire Line
	2050 5100 3050 5100
Wire Wire Line
	2500 6050 2500 5300
Wire Wire Line
	2500 5300 2050 5300
Wire Wire Line
	2050 6850 2050 5400
Wire Wire Line
	2050 6850 2800 6850
NoConn ~ 5950 2950
NoConn ~ 5950 3450
Wire Wire Line
	3850 2950 3850 2750
Wire Wire Line
	3850 2750 3500 2750
Connection ~ 3500 2750
Wire Wire Line
	3450 3450 3450 3600
Connection ~ 3450 3600
Wire Wire Line
	3450 4000 3450 4300
Connection ~ 3450 4300
Wire Wire Line
	3950 3150 5450 3150
Wire Wire Line
	3850 5100 3450 5100
Connection ~ 3450 5100
Wire Wire Line
	4800 3450 5450 3450
Connection ~ 3450 5950
Wire Wire Line
	5450 3550 4850 3550
Wire Wire Line
	4850 3550 4850 6750
Wire Wire Line
	4850 6750 3450 6750
Connection ~ 3450 6750
NoConn ~ 5450 3650
NoConn ~ 5450 3750
NoConn ~ 5450 3850
NoConn ~ 5450 3950
NoConn ~ 5450 4150
NoConn ~ 5450 4250
NoConn ~ 5450 4350
NoConn ~ 5450 4450
NoConn ~ 5450 4550
NoConn ~ 5450 4650
NoConn ~ 5450 4750
NoConn ~ 5950 4750
NoConn ~ 5950 4650
NoConn ~ 5950 4550
NoConn ~ 5950 4350
NoConn ~ 5950 4250
NoConn ~ 5950 4150
NoConn ~ 5950 4050
NoConn ~ 5950 3950
NoConn ~ 5950 3750
Wire Wire Line
	5950 3050 7150 3050
Wire Wire Line
	7150 3050 7150 2450
Wire Wire Line
	7150 2450 8350 2450
Wire Wire Line
	8350 2450 8350 3050
Wire Wire Line
	7350 3050 8350 3050
Wire Wire Line
	8350 3050 8350 3550
Connection ~ 8350 3050
Wire Wire Line
	7350 3550 8350 3550
Wire Wire Line
	6500 4950 7700 4950
$Comp
L Device:R R13
U 1 1 5E41F61B
P 6600 2600
F 0 "R13" V 6393 2600 50  0000 C CNN
F 1 "10K" V 6484 2600 50  0000 C CNN
F 2 "Resistor_SMD:R_1206_3216Metric_Pad1.42x1.75mm_HandSolder" V 6530 2600 50  0001 C CNN
F 3 "~" H 6600 2600 50  0001 C CNN
	1    6600 2600
	0    1    1    0   
$EndComp
Wire Wire Line
	6750 2600 6900 2600
Wire Wire Line
	3750 2300 4800 2300
$Comp
L Transistor_BJT:MMBT3904 Q7
U 1 1 5E5ACEDD
P 4400 3650
F 0 "Q7" H 4591 3696 50  0000 L CNN
F 1 "MMBT3904" H 4591 3605 50  0000 L CNN
F 2 "Package_TO_SOT_SMD:SOT-23" H 4600 3575 50  0001 L CIN
F 3 "https://www.fairchildsemi.com/datasheets/2N/2N3904.pdf" H 4400 3650 50  0001 L CNN
	1    4400 3650
	1    0    0    -1  
$EndComp
Wire Wire Line
	3600 2850 4000 2850
Wire Wire Line
	3550 3700 4000 3700
Wire Wire Line
	4000 2850 4000 3700
Connection ~ 4000 2850
Connection ~ 4000 3700
Wire Wire Line
	4000 3700 4000 4400
Connection ~ 4000 4400
Wire Wire Line
	4000 4400 4000 5200
Connection ~ 4000 5200
Wire Wire Line
	3450 5950 4800 5950
Wire Wire Line
	4000 5200 4000 6050
Connection ~ 4000 6050
Wire Wire Line
	4000 6050 4000 6850
Wire Wire Line
	4000 2850 5450 2850
Wire Wire Line
	4500 3850 4750 3850
Wire Wire Line
	4750 3850 4750 3250
Wire Wire Line
	4750 3250 5450 3250
Wire Wire Line
	3850 2950 4500 2950
Wire Wire Line
	4500 2950 4500 3450
Wire Wire Line
	5450 2950 4600 2950
Wire Wire Line
	4600 2950 4600 3000
Wire Wire Line
	4600 3000 4200 3000
Wire Wire Line
	4200 3000 4200 3650
$Comp
L Transistor_BJT:MMBT3904 Q10
U 1 1 5E66A060
P 4400 5150
F 0 "Q10" H 4591 5196 50  0000 L CNN
F 1 "MMBT3904" H 4591 5105 50  0000 L CNN
F 2 "Package_TO_SOT_SMD:SOT-23" H 4600 5075 50  0001 L CIN
F 3 "https://www.fairchildsemi.com/datasheets/2N/2N3904.pdf" H 4400 5150 50  0001 L CNN
	1    4400 5150
	1    0    0    -1  
$EndComp
Wire Wire Line
	4500 5350 4700 5350
Wire Wire Line
	4700 5350 4700 4050
Wire Wire Line
	4700 4050 5450 4050
Wire Wire Line
	4200 3800 4150 3800
Wire Wire Line
	4200 3800 4200 5150
Wire Wire Line
	4800 5950 4800 3450
Wire Wire Line
	3850 5100 3850 4950
Wire Wire Line
	3850 4950 4500 4950
Wire Wire Line
	6800 3950 7650 3950
Wire Wire Line
	7050 4300 7050 4150
Wire Wire Line
	7650 4050 7350 4050
Wire Wire Line
	7350 4050 7350 4100
Wire Wire Line
	7350 4500 8350 4500
Wire Wire Line
	7050 5250 7050 5450
Wire Wire Line
	6800 5050 7700 5050
Wire Wire Line
	7350 5650 8350 5650
Wire Wire Line
	8350 4500 8350 5650
Wire Wire Line
	7700 5150 7350 5150
Wire Wire Line
	7350 5150 7350 5250
Wire Wire Line
	5450 3050 4150 3050
Wire Wire Line
	4150 3050 4150 3800
Wire Wire Line
	3950 3150 3950 3450
Wire Wire Line
	3450 3450 3950 3450
Wire Wire Line
	5450 3350 4050 3350
Wire Wire Line
	4050 3350 4050 4000
Wire Wire Line
	4050 4000 3450 4000
Wire Wire Line
	7350 5650 6250 5650
Wire Wire Line
	6250 5650 6250 4450
Wire Wire Line
	6250 4450 5950 4450
Connection ~ 7350 5650
$EndSCHEMATC
