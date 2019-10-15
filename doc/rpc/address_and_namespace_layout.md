# UARTBus microcontroller RPC address and namespace layout

## Recommended address layout for UARTBus (When using group and broadcast addresses)

- -1 - -64: 1 byte long device addresses
- -2 - -32: reserved namespace for future uartbus infrastrucure services    
- -1: power on channel (nodes publish a packet when they go online)
- 0: generic broadcast address
- 1-63: 1 byte long devices address space
- 63: recommended for PC gateway device address (highest address in 1 byte addressing)
- 64-8191: 2 byte long device address space
- 8192-1048575: 3 long byte device address space
- {2^((b-1)*7)/2} - {2^(b-1*7)/2-1}: b byte long device address space



## Recommended RPC number namespace layout for UARTBus.

In embedded systems to identify namespaces we use numbers instead of "long"
strings to identify namespaces like the SNMP/MIB does.

- 0: reponse "namespace" every response packet payload starts with the "0" namespace sequence.  
- 1: network, discovering utility functions (ping, replay, user_led)  
- 2: bootloader, host functions (reboot soft/hard, enable/disable app, app code read/upload)  
- 3: debug functions  
- 4-31: not used but reserved for ub_bootloader
- 32 and over - user defined namespaces
- 255: reflection namespace. (An extra library support to get the node's function,
	extra details, available namespaces, function and signatures)


