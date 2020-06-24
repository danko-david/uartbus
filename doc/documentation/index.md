# Documentation of UARTBus system and infrastructure

## Introduction

### The bus

The UARTBus is microcontroller bus system built the top of the uart peripheral.
The implementation aims to achieve a device agnostic bus system with cheap price
[1], capable at least medium speed [2], less wire as possible[3] and long
ranges[4].


[1]: Cheaper than using CAN (MCP2515 & TJA1050) or Ethernet (ENC28J60)  
[2]: Faster than 1-Wire (16.3Kbps) but slower than  I2C mac (3.2 Mbps)  
[3]: 1 write when there's common ground.  
[4]: Connect nodes on 100 m with twisted pair.


### The infrastructure (Microcontroller side)

The infrastructure should make capable to manage the nodes in an abstract way
[1] to implement common functionalities like discover and identify the nodes,
and the most important: upload application code to the microcontroller[2].


[1]: Regardless of the manufacturer, family and model, every microcontroller
must have some basic functionalities. This also requires a common protocol.   
[2]: Of course the uploaded binary code is build specially for the target device
considering the exact family, model, CPU frequency and other possible parametric
dependencies, but these parameters must be capable to be known by requesting
them.


### The infrastructure (Computer side)

The bus can be connected through a gateway to a computer. At the computer side
we can work with heavy softwares and algorithms to request and command the
nodes and thereby achieve complex behavior by coordination. This connection is
designed to make possible for multiple application to connect the bus at the
time and dynamically. At this software level we form higher level objects
like bus and device object, RPC calls and transactions.
Some basic management utility is also part of the project.


### Modularity

The project and it's features previously written, doesn't form a single
monolithic block. Every module is written with keep in mind to be flexible to
reuse in other project. For instance if don't need the whole infrastructure just
the bus library itself, you can use only that part. It you find the 
infrastructure worth to work with, but already use other packet based network,
you can use some part of this infrastructure. If you find only the RPC
management useful, it's separated from the other part of code, so you can use
only that.  


## Components


- bus driver (OSI-1)
- libub (OSI-2)

- Gateway

- jvx-uartbus-library
This RPC server is written in java but language agnostic

- Networking

- RPC

- UARTBus host

A node is microcontoller capable to connect to the bus and flashed with the 
basic `uartbus host` application. This application provides the basic 

- ub_app_wrapper

- ub_app_wrapper_arduino

- ub_utilities

## Examples

TODO:

- create an example with direct applications and minimal devices on 5 V bus
	without bus gateway.


## The UARTBus 

The best way to extract the details of different aspect of the bus design is to
use the OSI model.

See: [OSI based documentation index](./osi/index.md)

## UARTBus bus gateway

TODO:

- how to build a gateway
- required software (linux: socat)
- how to start an RPC server and connect applications to bus

## UARTBus RPC features and overview 

In this aspect, the documentation describes how RPC layer works, how can you
call a function provided by a microcontroller and how you get the response
of an RPC call. 

See: [RPC documentation index](./rpc/index.md)


## UARTBus host application, application upload and supported devices

TODO:

- ub host memory layout
- compilation scripts and uploading method
- arduino/wiringc compilation and upload
- add some general purpose example app code


## UARTBus BusDevice object models 

TODO

## Documentation TODOs


