package eu.javaexperience.electronic.uartbus.rpc.client.device;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This annotation mark that the function can be called again when we assume
 * that the request or response packet has been lost during the transaction
 * and can be safely repeat
 * 
 **/
@Retention(RetentionPolicy.RUNTIME)
public @interface UbRetransmittable
{}
