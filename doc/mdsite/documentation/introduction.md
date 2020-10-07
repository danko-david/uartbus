
# 1 Introduction

## 1.1 <a name="why_created">Why is this project created?</a>

There are more reason that lead me to start this project:
- There was no easy and simple to use bus for microcontrollers over long
distances with multiple nodes.
- There's only messy ways to communicate between computers and microcontrollers.

### 1.1.1 <a name="influenced_by">What by this project is influenced?</a>

- Mainly by [KNX](https://en.wikipedia.org/wiki/KNX_(standard)): It is a
building automation system with standalone sensor, actuator, controller nodes.
This nodes can communicate with each-other and can be programmed over the bus.
By this way, it can implement a decentralized automation system.
- Open source philosophy: However KNX is an open standard, it is expensive and
hard to make the components for you own.
- Arduino/wiring with it's simplicity: Microcontrollers can be more just
register bit-banging devices. With some creativity over C++, it can
represents devices and peripherals well. 


## 1.2 <a name="design_reqirements">Design requirements</a>


### 1.2.1 <a name="dr_bus">The bus</a>

The implementation aims to achieve a device-agnostic bus system with cheap price
[1], capable at least medium speed [2], less wire as possible[3] and long
ranges[4].


[1]: Cheaper than using CAN (MCP2515 & TJA1050) or Ethernet (ENC28J60)  
[2]: Faster than 1-Wire (16.3Kbps) but slower than  I2C mac (3.2 Mbps)  
[3]: 1 write when there's common ground.  
[4]: Connect nodes on 100 m with twisted pair.


### 1.2.2 <a name="dr_infra_uc">Microcontroller side infrastructure</a>

The infrastructure should make capable to manage the nodes in an abstract way
[1] to implement common functionalities like discovering and identify the nodes,
and the most important: upload application code to the microcontroller[2].


[1]: Regardless of the manufacturer, family, and model, every microcontroller
must have some basic functionalities. This also requires a common protocol.   
[2]: Of course the uploaded binary code is built especially for the target device
considering the exact family, model, CPU frequency and other possible parametric
dependencies, but these parameters must be capable to be known by requesting
them.


### 1.2.3 <a name="dr_infra_computer">Computer side infrastructure</a>

The bus can be connected through a gateway to a computer. At the computer side,
we can work with heavy software and algorithms to request and command the
nodes and thereby achieve complex behavior by coordination. This connection is
designed to make it possible for multiple applications to connect the bus at the
time and dynamically. At this software level, we form higher-level objects
like bus and device objects, RPC calls, and transactions.
Some basic management utility is also part of the project.


### 1.2.4 <a name="dr_modularity">Modularity</a>

The project and its features are written as don't form a single
monolithic block. Every module is written with keep in mind to be flexible to
reuse in other projects. For instance, if don't need the whole infrastructure just
the bus library itself, you can use only that part. If you find the infrastructure
worth to work with, but already use another packet-based network,
you can use some part of this infrastructure. If you find only the RPC
management useful, it's separated from the other part of code, so you can use
only that.

