[back to the documentation index](../)  

# UARTBus OSI-6: Presentation layer

I've created a small footprint RPC library for microcontrollers. The
namespace path and function parameters just placement in the packet after each
other. The dispatcher function relays the request to the proper RPC path, and
the registered RPC call handler extracts the parameters executes the query and
responses to the packet.

A request parsed into a `struct rpc_request` located in
[./source/uc/utils/lib/rpc/rpc.h](/documentation/doxygen/rpc_8h_source.html).  

This request is dispatched through a RPC namespace chain. For example see:
[./source/uc/bootloader/ub_bootloader.c](/documentation/doxygen/ub__bootloader_8c_source.html)
how RPC_NS_FUNCTIONS constructed and where this chain used as a parameter.

When a namespace matched `procPtr` incremented, so in next processing stage
it will be continued from the next byte (pointed in the payload by
the procPtr which is incremented right now)

The pointed function may dispatch again, or it can be a function that processes
the request. The `reply` function pointer in the `struct rpc_request` used
to assemble and send a response packet.

For example: in the bootloader (aka: UART host application) there's some pre-defined
RPC namespace and functions which provides unified functionality for all devices
on the bus.

- 0: the 0.th namespace is reserved for response packets.
- 1: Bus functions
	- 0 ping: responses and empty message as a pong packet
	- 1 replay: responses the request payload
	- 2 user_led: controls the PORTB5 LED (arduino 13 port led) 0 = off, 1 = on,
		2 = toggle: always replays the current (after the request) state of the
		led
- 2: Bootloader functions:
	- 0 power functions:
		- 0 hardware reset (using wdt timeout)
		- 1 software reset (jmp 0x0)
	...
	- 4: flash functions:
		- getFlashStage: is the device under code upload?
		- getAppStartAddress: returns the application start address
		....
		
And so on, for full namespace specification see the documentation.

To turn on the user led you have to send a packet with payload of:    
1:2:1 => (1) Bus functions => (2) user led handler function => (1) parameter    
this returns with the response:    
0:1:2:1 => (0) response packet => (1:2) namespace and function path => (1)
	response parameter aka the led is turned on.   
   
   
To invoke a hardware reset:
2:0:0 => (2) Bootloader functions => (0) power functions => (0) hardware reset   
No response generated, because committed a hardware reset immediately.

This namespace tree is extendible to any depth and designed for the application
developer can attach a new function into this tree.

On the other side, on the PC in java, this RPC namespace tree is modeled with
objects, see: `UartBusDevice.getRpcRoot()` . But the best thing: I've created
a mechanism to describe interfaces for this RPC namespaces and functions with
the known types, and java (using proxies) generates namespace object and
function you can call right from java. For example see: UbBootloaderFunctions
and for a real application that uses this see: UartbusCodeUploader.
