package com.geeksville.pilotpal

import de.taimos.gpsd4java.api.ObjectListener
import de.taimos.gpsd4java.backend.GPSdEndpoint
import de.taimos.gpsd4java.backend.ResultParser
import de.taimos.gpsd4java.types.TPVObject

fun main(args: Array<String>) {
    println("hello world!")

    val ep = GPSdEndpoint("localhost", 2947, ResultParser())

    class MyListener : ObjectListener() {

        override fun handleTPV(tpv: TPVObject) {
            println("tpv: $tpv")
        }
        
    }

    ep.addListener(MyListener())
    ep.start()
    println("GPSD version: " + ep.version())
    println("GPSD watch: " + ep.watch(true, true))
    println("start pollining" + ep.poll())
    Thread.sleep(60 * 1000)
}