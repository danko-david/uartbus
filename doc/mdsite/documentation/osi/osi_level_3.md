[back to the documentation index](../)  

# UARTBus OSI-2: Network layer

## 2.1.3.1 Device and device group addresses

Every node receives every packets on bus. This makes possible to create
broadcast addresses (0 is a general broadcast address) and device groups by
using negative addresses for this purpose.

- 0 : common, special purpose broadcast address
- positive number: device address
- negative number: group address

## 2.1.3.2 Variable length addressing

Instead of using fixed int{8,16,32,64}_t for addressing, i've created a so
called variable length addressing:

- If we choose int8_t, every packet has only two byte of overhead at the
	addressing, but only 128 group 1 broadcast and 127 device address available.
- If we choose int32_t, every packet has 8 byte overhead, but addresses can go
	at group to 2_147_483_648, one broadcast and 2_147_483_647 device address.

To balance between this extreme cases i've created an encoder/decoder that
enables short address bytes when addresses are low (between -64 and +63) and 
increase address length when assign higher addresses.


|   | 7. bit | 6. bit | 5. bit | 4. bit | 3. bit | 2. bit | 1. bit | 0. bit |  
| --- |:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|  
| First byte | E | S | A5 | A4 | A3 | A2 | A1 | A0 |  
| Nth byte | E | A6 | A5 | A4 | A3 | A2 | A1 | A0 |  

Byte scheme:

- E: Extend: Address continued on the next byte. The in last address byte this
	bit is 0. 
- S: Signum: sign of the whole number. Used only in the first byte. If set
	address is converted as: -(addr+1)
- An: address bit.

Example for one byte addresses:

| Address | First byte |  
|:---:|:---:|  
| 0   | 0000_0000 |  
| -1  | 0100_0000 |  
| 16  | 0001_0000 |  
| -16 | 0100_1111 |  
| -64 | 0111_1111 |  
| 63  | 0011_1111 |  

Example for two byte addresses:

| Address | First byte | Second byte |  
|:---:|:---:|:---:|
| 128   | 1000_0001 | 0000_0000 |  
| -128  | 1100_0000 | 0111_1111 |  
| 8191  | 1011_1111 | 0111_1111 |  
| -8192 | 1111_1111 | 0111_1111 |  

These types described as (s)vint_t (signed variable int) but it is not a real
type which the compiler knows, just a notation. (There's an implementation for
uvint_t if you use only unsigned values.)

## 2.1.3.3 Default packet scheme

The default packet scheme of the project:

- svint_t destination address
- svint_t source address
- uint8_t[n] payload
- uint8_t crc8 packet checksum