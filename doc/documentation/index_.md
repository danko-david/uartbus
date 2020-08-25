# Documentation of UARTBus system and infrastructure

## Components


- bus driver (OSI-1)
- libub (OSI-2)

- Gateway

- jvx-uartbus-library
This RPC server is written in java but language agnostic

- Networking

- RPC

- UARTBus host

A node is a microcontroller capable to connect to the bus and flashed with the basic `uartbus host` application. This application provides the basic 

- ub_app_wrapper

- ub_app_wrapper_arduino

- ub_utilities

## Examples

TODO:

- create an example with direct applications and minimal devices on 5 V bus
	without the bus gateway.


## The UARTBus 

The best way to explain the details of the different aspect of the bus design is to
use the OSI model.

See: [OSI based documentation index](./osi/index.md)

## UARTBus bus gateway

TODO:

- how to build a gateway
- required software (Linux: socat)
- how to start an RPC server and connect applications to bus

## UARTBus RPC features and overview 

In this aspect, the documentation describes how the RPC layer works, how can you
call function, provided by a microcontroller and how you get the response
of an RPC call. 

See: [RPC documentation index](./rpc/index.md)


## UARTBus host application, application upload and supported devices

TODO:

- ub host memory layout
- compilation scripts and uploading method
- Arduino/wiring compilation and upload
- add some general-purpose example app code


## UARTBus BusDevice object models 

TODO

## Documentation TODOs

