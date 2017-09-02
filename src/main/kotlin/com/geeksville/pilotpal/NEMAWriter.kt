package com.geeksville.pilotpal

import de.taimos.gpsd4java.types.TPVObject
import java.io.PrintStream
import java.util.*
import kotlin.experimental.xor

/*
 A tool to emit NMEA records based on position

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
            """%02d%02.4f,%s""".format(deg, min, if (isNeg) "S" else "N")
        else
            """%03d%02.4f,%s""".format(deg, min, if (isNeg) "W" else "E")
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
        val packetHasLocation = !tpv.latitude.isNaN() && !tpv.longitude.isNaN() && tpv.latitude != 0.0 && tpv.longitude != 0.0
        if (packetHasLocation && now - lastEmitTimestamp >= 2000L) {
            // FIXME - crude throttling to once per 2 sec
            lastEmitTimestamp = now
            emitGGA(tpv.timestamp, tpv.latitude, tpv.longitude, tpv.altitude)
            // emitGSA()
            emitRMC(tpv.timestamp, tpv.latitude, tpv.longitude, tpv.speed, tpv.course)
            emitVTG(tpv.speed, tpv.course)
            s.flush()
        }
    }
}