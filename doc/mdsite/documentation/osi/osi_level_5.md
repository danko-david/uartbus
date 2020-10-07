[back to the documentation index](../)  

# UARTBus OSI-5: Session layer

No session layer was implemented.  

Session tracking is a really resource-intensive component, especially
considering the target devices are microcontrollers.  

If you need to "establish connections", exchange file streams (with repeat when
packet loss occurs) you need to implement at the application level.  

However, if I find/develop suitable code patterns to generalize these tasks and
implement some kind of session layer, I will include in the library.
But these utilities still will work on the application layer.
