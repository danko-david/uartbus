[back to the documentation index](../)  

# UARTBus OSI-2: Data link layer

In this context the packet is interpreted as a sequence of byte in a time frame.
The time frame starts at the first sent byte and ends after the time of 2
(default value, but configurable) byte time transmission idle period.

![UARTBus frame and collision handling](/resources/image/collision_handling.jpg)

A demonstration of UARTBus packages with collision handling (and retransmission)
using two nodes plus one bus gateway.
(This is an early development oscillogramm, timing not properly optimized)

- Channel 1, Red: Bus wire signal 2 V/Div 
- Channel 2, Yellow: RX wire signal of the first device 1 V/Div

1) Bus gateway sends a broadcast ping packet
2) Collision occurred, both node cancels the transmission and step back for a
	random time (longer wait time to the next transmission start)
	(Ethernets style collision handling)
3) First node "wins the arbitration" and sends the response pong packet
4) Second node also retransmit the pong packet that collided previously
5) No packet to send. Back to idle state.
