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

    SerialPortList.getPortNames().forEach { name -> println("serial port: $name") }

    val useSerial = true
    val outStream = PrintStream(BufferedOutputStream(if (useSerial) {
        val outPort = SerialPort("/dev/ttyUSB2")

        outPort.openPort()
        outPort.setParams(9600, SerialPort.DATABITS_8,
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
    Thread.sleep(60 * 60 * 1000)
}