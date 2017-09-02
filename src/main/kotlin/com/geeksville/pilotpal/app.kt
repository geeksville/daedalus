package com.geeksville.pilotpal

import de.taimos.gpsd4java.api.ObjectListener
import de.taimos.gpsd4java.backend.GPSdEndpoint
import de.taimos.gpsd4java.backend.ResultParser
import de.taimos.gpsd4java.types.TPVObject
import jssc.SerialPort
import jssc.SerialPortList
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream


fun main(args: Array<String>) {
    println("my crude NEMA baud rate converter!")

    startNEMAEmitter("/dev/ttyUSB1", 9600)
    // Wait for 20 years before exiting ;-)
    Thread.sleep(20 * 365 * 24 * 60 * 60 * 1000L)
}