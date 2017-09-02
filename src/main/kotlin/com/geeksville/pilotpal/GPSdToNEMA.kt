package com.geeksville.pilotpal

import de.taimos.gpsd4java.api.ObjectListener
import de.taimos.gpsd4java.backend.GPSdEndpoint
import de.taimos.gpsd4java.backend.ResultParser
import de.taimos.gpsd4java.types.TPVObject
import jssc.SerialPort
import jssc.SerialPortList
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.PrintStream

/**
 * Listen to a local GPSd server and emit a low bitrate version of position data. Useful for APRS and ELT
 * devices which can only work at 9600 baud input.
 *
 * FIXME close gpsd and serial port connections on exit/failure
 */
fun startNEMAEmitter(destDevice: String, baud: Int) {
    SerialPortList.getPortNames().forEach { name -> println("serial port: $name") }

    val useSerial = true
    val outStream = PrintStream(BufferedOutputStream(if (useSerial) {
        val outPort = SerialPort(destDevice)

        outPort.openPort()
        outPort.setParams(baud, SerialPort.DATABITS_8,
                SerialPort.STOPBITS_1,
                SerialPort.PARITY_NONE)
        outPort.flowControlMode = SerialPort.FLOWCONTROL_NONE

        SerialOutputStream(outPort)
    } else
        FileOutputStream("/tmp/nemaout.txt")
    ))

    val ep = GPSdEndpoint("localhost", 2947, ResultParser())

    val nemaOut = NEMAWriter(outStream)
    val debugOut = NEMAWriter(System.out)

    class MyListener : ObjectListener() {

        override fun handleTPV(tpv: TPVObject) {
            try {
                // println("tpv: $tpv")
                nemaOut.emit(tpv)
                debugOut.emit(tpv)
            }
            catch(ex: Exception) {
                System.err.println("FIXME: Unexpected exception in GPSD callback: $ex")
            }
        }

    }

    ep.addListener(MyListener())
    ep.start()
    ep.watch(true, true)
    ep.poll()
}