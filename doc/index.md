
# Official site of UARTBus.

## What is UARTBus?

UARTBus is a library, toolbox and infrastructure that enables you to create
decentralized multi master microcontroller networks whith devices has UART peripheral.
Essentally it bounds the device's RX and TX UART peripheral and forms a bus connector.

This project is aim to fill the gap between other electrical bus systems:

- Form a bus, not just point to point (Not like UART, SPI, but like Ethernet, 1-Wire, CAN)
- Capability for multi master nodes (Not like 1-Wire, SPI, I2C, but like Ethernet, CAN)
- Use least wire as possible for communication (Not like I2C or SPI, but like Ethernet, 1-Wire or CAN)
- Fast as possible (Not like 1-wire but others) and maybe with configurable speed.
- Avoid complex external peripheral, use only basic components. (Not like Ethernet or CAN but others)
- Be cheap as possible.

And some not electrical goals:

- Be the communication protocol acceptable both low (microcontrollers) a high (PC applications) level softwares.
- Provide elevated functions like discovering and uploading code to the nodes.
- Integrate with popular softwares and libraries like arduino.

If you are interested, welcome!





