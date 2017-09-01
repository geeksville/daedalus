package com.geeksville.pilotpal

import de.taimos.gpsd4java.types.TPVObject
import java.io.PrintStream
import java.util.*
import kotlin.experimental.xor

/*
 FIXME todo
 make nemawriter
 throttle sending to once per second (per msg type)
 test with stdout
 test with a real serial port and APRS

 */
class NEMAWriter(private val s: PrintStream = System.out) {

    private val cal = GregorianCalendar(SimpleTimeZone(0, "UTC"), Locale.US)

    private var lastEmitTimestamp = 0L

    /*
    Each sentence begins with a '$' and ends with a carriage return/line feed
    sequence and can be no longer than 80 characters of visible text (plus the
    line terminators). The data is contained within this single line with data
    items separated by commas. The data itself is just ascii text and may
    extend over multiple sentences in certain specialized instances but is
    normally fully contained in one variable length sentence. The data may vary in
    the amount of precision contained in the message. For example time might be
    indicated to decimal parts of a second or location may be show with 3 or even
    4 digits after the decimal point. Programs that read the data should only
    use the commas to determine the field boundaries and not depend on column
    positions. There is a provision for a checksum at the end of each sentence
    which may or may not be checked by the unit that reads the data. The checksum
    field consists of a '*' and two hex digits representing an 8 bit exclusive OR
    of all characters between, but not including, the '$' and '*'. A checksum is
    required on some sentences.



     */

    /*
        GGA - essential fix data which provide 3D location and accuracy data.

 $GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47

Where:
     GGA          Global Positioning System Fix Data
     123519       Fix taken at 12:35:19 UTC
     4807.038,N   Latitude 48 deg 07.038' N
     01131.000,E  Longitude 11 deg 31.000' E
     1            Fix quality: 0 = invalid
                               1 = GPS fix (SPS)
                               2 = DGPS fix
                               3 = PPS fix
			       4 = Real Time Kinematic
			       5 = Float RTK
                               6 = estimated (dead reckoning) (2.3 feature)
			       7 = Manual input mode
			       8 = Simulation mode
     08           Number of satellites being tracked
     0.9          Horizontal dilution of position
     545.4,M      Altitude, Meters, above mean sea level
     46.9,M       Height of geoid (mean sea level) above WGS84
                      ellipsoid
     (empty field) time in seconds since last DGPS update
     (empty field) DGPS station ID number
     *47          the checksum data, always begins with *



     */
    private fun emitGGA(ts: Double, lat: Double, lon: Double, alt: Double) {
        val msg = "GGA,%s,%s,%s,1,08,0.9,%s,M,0,M,,0000".format(toTimeStr(ts),
                toLatStr(lat), toLonStr(lon), toAltStr(alt))
        s.println(addChecksum(msg))
    }

    private fun addChecksum(msg: String): String {
        val fullPacket = "GP" + msg
        val sum = fullPacket.fold(0.toByte(), { sum, next -> sum xor next.toByte() })
        return "\$%s*%02X\r".format(fullPacket, sum)
    }

    private fun toDMSStr(l: Double, isLat: Boolean): String {
        if(l.isNaN())
            return ","

        val isNeg = l < 0
        val lPos = Math.abs(l)

        val deg = lPos.toInt()
        val min = ((lPos - deg) * 60)

        return if (isLat)
            """%02d%02.3f,%s""".format(deg, min, if (isNeg) "S" else "N")
        else
            """%03d%02.3f,%s""".format(deg, min, if (isNeg) "W" else "E")
    }

    private fun toLatStr(lat: Double) = toDMSStr(lat, true)
    private fun toLonStr(lon: Double) = toDMSStr(lon, false)
    private fun toAltStr(alt: Double) = if (alt.isNaN()) "" else "%.1f".format(alt)

    private fun toTimeStr(ts: Double): String {
        cal.timeInMillis = (ts * 1000).toLong()
        return if (ts == 0.0)
            ""
        else
            "%02d%02d%02d.%03d".format(cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE),
        cal.get(Calendar.SECOND),
        cal.get(Calendar.MILLISECOND)
        )
    }

    private fun toDateStr(ts: Double): String {
        cal.timeInMillis = (ts * 1000).toLong()
        return if (ts == 0.0)
            ""
        else
            "%02d%02d%02d".format(cal.get(Calendar.DAY_OF_MONTH),
                    cal.get(Calendar.MONTH) + 1, // nema months are 1 based, java 0 based
                    cal.get(Calendar.YEAR) % 100
            )
    }

    /*
    RMC - NMEA has its own version of essential gps pvt (position, velocity, time) data. It is called RMC, The Recommended Minimum, which will look similar to:

$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A

Where:
     RMC          Recommended Minimum sentence C
     123519       Fix taken at 12:35:19 UTC
     A            Status A=active or V=Void.
     4807.038,N   Latitude 48 deg 07.038' N
     01131.000,E  Longitude 11 deg 31.000' E
     022.4        Speed over the ground in knots
     084.4        Track angle in degrees True
     230394       Date - 23rd of March 1994
     003.1,W      Magnetic Variation
     *6A          The checksum data, always begins with *
     */
    private fun emitRMC(ts: Double, lat: Double, lon: Double, speed: Double, track: Double) {
        val msg = "RMC,%s,A,%s,%s,%s,%s,%s,,,A".format(toTimeStr(ts),
                toLatStr(lat), toLonStr(lon), toKnotStr(speed), toTrackStr(track), toDateStr(ts))
        s.println(addChecksum(msg))
    }

    /*
    VTG - Velocity made good. The gps receiver may use the LC prefix instead of GP if it is emulating Loran output.

  $GPVTG,054.7,T,034.4,M,005.5,N,010.2,K*48

where:
        VTG          Track made good and ground speed
        054.7,T      True track made good (degrees)
        034.4,M      Magnetic track made good
        005.5,N      Ground speed, knots
        010.2,K      Ground speed, Kilometers per hour
        *48          Checksum
     */
    private fun emitVTG(speed: Double, track: Double) {
        val msg = "VTG,%s,T,,M,%s,N,%s,K,A".format(
                toTrackStr(track), toKnotStr(speed), toKmhStr(speed))
        s.println(addChecksum(msg))
    }

    private fun toTrackStr(track: Double) = if (track.isNaN()) "" else "%03.1f".format(track)

    /**
     * Convert from m/s to knots
     */
    private fun toKnotStr(speed: Double) = if (speed.isNaN()) "" else "%03.1f".format(speed * 1.94384)

    /**
     * Convert from m/s to km/h
     */
    private fun toKmhStr(speed: Double) = if (speed.isNaN()) "" else "%03.1f".format(speed * 3.6)


    /*
      GSA - GPS DOP and active satellites. This sentence provides details on the nature of the fix. It includes the numbers of the satellites being used in the current solution and the DOP. DOP (dilution of precision) is an indication of the effect of satellite geometry on the accuracy of the fix. It is a unitless number where smaller is better. For 3D fixes using 4 satellites a 1.0 would be considered to be a perfect number, however for overdetermined solutions it is possible to see numbers below 1.0.

There are differences in the way the PRN's are presented which can effect the ability of some programs to display this data. For example, in the example shown below there are 5 satellites in the solution and the null fields are scattered indicating that the almanac would show satellites in the null positions that are not being used as part of this solution. Other receivers might output all of the satellites used at the beginning of the sentence with the null field all stacked up at the end. This difference accounts for some satellite display programs not always being able to display the satellites being tracked. Some units may show all satellites that have ephemeris data without regard to their use as part of the solution but this is non-standard.

  $GPGSA,A,3,04,05,,09,12,,,24,,,,,2.5,1.3,2.1*39

Where:
     GSA      Satellite status
     A        Auto selection of 2D or 3D fix (M = manual)
     3        3D fix - values include: 1 = no fix
                                       2 = 2D fix
                                       3 = 3D fix
     04,05... PRNs of satellites used for fix (space for 12)
     2.5      PDOP (dilution of precision)
     1.3      Horizontal dilution of precision (HDOP)
     2.1      Vertical dilution of precision (VDOP)
     *39      the checksum data, always begins with *
     */
    fun emitGSA() {
        // FIXME - make real
        s.println(addChecksum("GSA,A,3,15,16,26,27,10,29,18,20,13,21,,,1.6,0.9,1.3"))
    }

    fun emit(tpv: TPVObject) {
        val now = System.currentTimeMillis()
        val packetHasLocation = !tpv.latitude.isNaN() && !tpv.longitude.isNaN()
        if (packetHasLocation && now - lastEmitTimestamp >= 2000L) {
            // FIXME - crude throttling to once per 2 sec
            lastEmitTimestamp = now
            emitGGA(tpv.timestamp, tpv.latitude, tpv.longitude, tpv.altitude)
            emitGSA()
            emitRMC(tpv.timestamp, tpv.latitude, tpv.longitude, tpv.speed, tpv.course)
            emitVTG(tpv.speed, tpv.course)
            s.flush()

            /*
            valid:
$GPGSA,A,3,14,22,32,01,11,03,31,,,,,,3.3,1.5,3.0*31
$GPGSV,3,1,12,31,63,109,15,01,55,279,27,22,52,313,30,14,51,042,36*7C
$GPGSV,3,2,12,11,38,246,29,32,34,053,29,03,28,306,31,10,15,110,*7B
$GPGSV,3,3,12,23,12,254,,25,08,057,,26,03,145,,17,02,318,*7F
$GPRMC,041643.000,A,3731.2568,N,12218.5420,W,0.33,156.74,300817,,,A*70
$GPGGA,041644.000,3731.2569,N,12218.5421,W,1,07,1.5,146.8,M,-25.7,M,,0000*6C
$GPGSA,A,3,14,22,32,01,11,03,31,,,,,,3.3,1.5,3.0*31
$GPRMC,041644.000,A,3731.2569,N,12218.5421,W,0.33,157.52,300817,,,A*72
$GPGGA,041645.000,3731.2569,N,12218.5421,W,1,07,1.5,146.4,M,-25.7,M,,0000*61
$GPGSA,A,3,14,22,32,01,11,03,31,,,,,,3.3,1.5,3.0*31
$GPRMC,041645.000,A,3731.2569,N,12218.5421,W,0.30,159.22,300817,,,A*79
$GPGGA,041646.000,3731.2569,N,12218.5421,W,1,07,1.5,146.1,M,-25.7,M,,0000*67
$GPGSA,A,3,14,22,32,01,11,03,31,,,,,,3.3,1.5,3.0*31
$GPRMC,041646.000,A,3731.2569,N,12218.5421,W,0.44,155.95,300817,,,A*79
$GPGGA,041647.000,3731.2569,N,12218.5421,W,1,07,1.5,145.8,M,-25.7,M,,0000*6C
$GPGSA,A,3,14,22,32,01,11,03,31,,,,,,3.3,1.5,3.0*31
$GPRMC,041647.000,A,3731.2569,N,12218.5421,W,0.74,155.18,300817,,,A*7E
$GPGGA,041648.000,3731.2568,N,12218.5420,W,1,07,1.5,145.6,M,-25.7,M,,0000*6D
$GPGSA,A,3,14,22,32,01,11,03,31,,,,,,3.3,1.5,3.0*31
$GPGSV,3,1,12,31,63,109,16,01,55,279,27,22,52,313,30,14,51,042,36*7F
$GPGSV,3,2,12,11,38,246,30,32,34,053,29,03,28,306,31,10,15,110,*73
$GPGSV,3,3,12,23,12,254,,25,08,057,,26,03,145,,17,02,318,*7F
$GPRMC,041648.000,A,3731.2568,N,12218.5420,W,0.74,156.03,300817,,,A*78
$GPGGA,041649.000,3731.2568,N,12218.5421,W,1,07,1.5,145.4,M,-25.7,M,,0000*6F
$GPGSA,A,3,14,22,32,01,11,03,31,,,,,,3.3,1.5,3.0*31
$GPRMC,041649.000,A,3731.2568,N,12218.5421,W,0.53,160.50,300817,,,A*7E
$GPGGA,041650.000,3731.2568,N,12218.5421,W,1,07,1.5,145.3,M,-25.7,M,,0000*60
$GPGSA,A,3,14,22,32,01,11,03,31,,,,,,3.3,1.5,3.0*31
$GPRMC,041650.000,A,3731.2568,N,12218.5421,W,0.40,157.06,300817,,,A*73
$GPGGA,041651.000,3731.2568,N,12218.5421,W,1,07,1.5,145.2,M,-25.7,M,,0000*60
$GPGSA,A,3,14,22,32,01,11,03,31,,,,,,3.3,1.5,3.0*31
$GPRMC,041651.000,A,3731.2568,N,12218.5421,W,0.39,158.31,300817,,,A*77
$GPGGA,041652.000,3731.2568,N,12218.5421,W,1,07,1.5,145.1,M,-25.7,M,,0000*60
$GPGSA,A,3,14,22,32,01,11,03,31,,,,,,3.3,1.5,3.0*31
$GPRMC,041652.000,A,3731.2568,N,12218.5421,W,0.59,157.40,300817,,,A*7B
$GPGGA,041653.000,3731.2567,N,12218.5420,W,1,07,1.5,145.1,M,-25.7,M,,0000*6F
$GPGSA,A,3,14,22,32,01,11,03,31,,,,,,3.3,1.5,3.0*31
$GPGSV,3,1,12,31,63,109,17,01,55,279,27,22,52,313,31,14,51,042,36*7F
$GPGSV,3,2,12,11,38,246,30,32,34,053,29,03,28,306,31,10,15,110,*73
$GPGSV,3,3,12,23,12,254,,25,08,057,,26,03,145,,17,02,318,*7F
$GPRMC,041653.000,A,3731.2567,N,12218.5420,W,0.82,156.47,300817,,,A*74
$GPGGA,041654.000,3731.2566,N,12218.5420,W,1,07,1.5,145.2,M,-25.7,M,,0000*6A
$GPGSA,A,3,14,22,32,01,11,03,31,,,,,,3.3,1.5,3.0*31
$GPRMC,041654.000,A,3731.2566,N,12218.5420,W,0.67,157.90,300817,,,A*72
$GPGGA,041655.000,3731.2566,N,12218.5420,W,1,07,1.5,145.2,M,-25.7,M,,0000*6B
$GPGSA,A,3,14,22,32,01,11,03,31,,,,,,3.3,1.5,3.0*31
$GPRMC,041655.000,A,3731.2566,N,12218.5420,W,0.49,156.51,300817,,,A*73
$GPGGA,041656.000,3731.2566,N,12218.5420,W,1,07,1.5,145.2,M,-25.7,M,,0000*68
$GPGSA,A,3,14,22,32,01,11,03,31,,,,,,3.3,1.5,3.0*31
$GPRMC,041656.000,A,3731.2566,N,12218.5420,W,0.49,158.16,300817,,,A*7D
$GPGGA,041657.000,3731.2567,N,12218.5421,W,1,07,1.5,145.1,M,-25.7,M,,0000*6A
$GPGSA,A,3,14,22,32,01,11,03,31,,,,,,3.3,1.5,3.0*31
$GPRMC,041657.000,A,3731.2567,N,12218.5421,W,0.33,157.61,300817,,,A*7E
$GPGGA,041658.000,3731.2567,N,12218.5421,W,1,07,1.5,145.1,M,-25.7,M,,0000*65
$GPGSA,A,3,14,22,32,01,11,03,31,,,,,,3.3,1.5,3.0*31
$GPGSV,3,1,12,31,63,109,19,01,55,279,27,22,52,313,32,14,51,042,36*72
$GPGSV,3,2,12,11,38,246,30,32,34,053,29,03,28,306,31,10,15,110,*73
$GPGSV,3,3,12,23,12,254,,25,08,057,,26,03,145,,17,02,318,*7F
$GPRMC,041658.000,A,3731.2567,N,12218.5421,W,0.45,156.57,300817,,,A*74
$GPGGA,041659.000,3731.2566,N,12218.5421,W,1,07,1.5,145.1,M,-25.7,M,,0000*65
$GPGSA,A,3,14,22,32,01,11,03,31,,,,,,3.3,1.5,3.0*31
$GPRMC,041659.000,A,3731.2566,N,12218.5421,W,0.67,158.95,300817,,,A*74
$GPGGA,041700.000,3731.2566,N,12218.5421,W,1,07,1.5,145.1,M,-25.7,M,,0000*68
$GPGSA,A,3,14,22,32,01,11,03,31,,,,,,3.3,1.5,3.0*31
$GPRMC,041700.000,A,3731.2566,N,12218.5421,W,0.43,155.15,300817,,,A*7A
$GPGGA,041701.000,3731.2566,N,12218.5421,W,1,07,1.5,145.1,M,-25.7,M,,0000*69
$GPGSA,A,3,14,22,32,01,11,03,31,,,,,,3.3,1.5,3.0*31
$GPRMC,041701.000,A,3731.2566,N,12218.5421,W,0.30,162.76,300817,,,A*7E
$GPGGA,041702.000,3731.2566,N,12218.5421,W,1,08,1.4,145.0,M,-25.7,M,,0000*65
$GPGSA,A,3,14,22,23,32,01,11,03,31,,,,,2.4,1.4,1.9*3C
$GPRMC,041702.000,A,3731.2566,N,12218.5421,W,0.60,170.01,300817,,,A*7B
$GPGGA,041703.000,3731.2565,N,12218.5421,W,1,08,1.4,144.8,M,-25.7,M,,0000*6E
$GPGSA,A,3,14,22,23,32,01,11,03,31,,,,,2.4,1.4,1.9*3C
$GPGSV,3,1,12,31,63,109,20,01,55,279,28,22,52,313,33,14,51,042,36*76
$GPGSV,3,2,12,11,38,246,30,32,34,053,28,03,28,306,30,10,15,110,*73
$GPGSV,3,3,12,23,12,254,16,25,08,057,,26,03,145,,17,02,318,*78
$GPRMC,041703.000,A,3731.2565,N,12218.5421,W,0.75,173.04,300817,,,A*7B
$GPGGA,041704.000,3731.2564,N,12218.5421,W,1,08,1.4,144.6,M,-25.7,M,,0000*66
$GPGSA,A,3,14,22,23,32,01,11,03,31,,,,,2.4,1.4,1.9*3C
$GPRMC,041704.000,A,3731.2564,N,12218.5421,W,0.80,168.02,300817,,,A*7B
$GPGGA,041705.000,3731.2565,N,12218.5422,W,1,08,1.4,144.3,M,-25.7,M,,0000*60
$GPGSA,A,3,14,22,23,32,01,11,03,31,,,,,2.4,1.4,1.9*3C
$GPRMC,041705.000,A,3731.2565,N,12218.5422,W,0.44,190.90,300817,,,A*7C
$GPGGA,041706.000,3731.2566,N,12218.5422,W,1,08,1.4,144.1,M,-25.7,M,,0000*62
$GPGSA,A,3,14,22,23,32,01,11,03,31,,,,,2.4,1.4,1.9*3C
$GPRMC,041706.000,A,3731.2566,N,12218.5422,W,0.32,142.17,300817,,,A*7D
$GPGGA,041707.000,3731.2567,N,12218.5423,W,1,08,1.4,143.9,M,-25.7,M,,0000*6C
$GPGSA,A,3,14,22,23,32,01,11,03,31,,,,,2.4,1.4,1.9*3C
$GPRMC,041707.000,A,3731.2567,N,12218.5423,W,0.15,95.22,300817,,,A*44

invalid:
$GPGGA,041053.000,3731.258,N,12218.543,W,1,08,0.9,162.5,M,0,M,,*61
$GPGSA,A,3,15,16,26,27,10,29,18,20,13,21,,,1.6,0.9,1.3*3C
$GPRMC,041053.000,A,3731.258,N,12218.543,W,0.1,114.0,300817,,,A*74
$GPVTG,114.0,T,,M,0.1,N,0.2,K,A*0A
             */
        }
    }
}