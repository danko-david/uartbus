[back to the documentation index](../)  

# UARTBus OSI-2: Transport layer

Every packet on the bus is a datagramm and it works like the [UDP](https://en.wikipedia.org/wiki/User_Datagram_Protocol) packets over the [Internet Protocol](https://en.wikipedia.org/wiki/Internet_Protocol) layer.
So there's no transmission control[1]  implmented yet. 


---
[1] Except the packet integrity, which is an OSI-2 level optional - used by default - option.