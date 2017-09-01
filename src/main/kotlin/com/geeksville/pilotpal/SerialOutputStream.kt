package com.geeksville.pilotpal

import jssc.SerialPort
import java.io.OutputStream

class SerialOutputStream(val port: SerialPort): OutputStream() {
    override fun write(p0: Int) {
        port.writeByte(p0.toByte())
    }

    // FIXME - someday provide a version of writeBytes

    override fun close() {
        port.closePort()
    }
}