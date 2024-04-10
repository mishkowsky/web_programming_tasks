package icmpPinger

import java.net.InetAddress
import kotlin.time.Duration
import kotlin.time.measureTimedValue

fun main() {
    val url = "192.168.10.10"
    launch(url)
}

fun launch(uri: String, numberOfIterations: Int = 4, timeout: Int = 1000) {
    val ipAddress = InetAddress.getByName(uri)

    val listOfRTT = mutableListOf<Duration>()
    println("Packets exchange with $uri [${ipAddress.hostAddress}]")
    repeat(numberOfIterations) {
        val (success, duration) = measureTimedValue {
            ipAddress.isReachable(timeout)
        }
        if (success) {
            listOfRTT += duration
            println("Reply from ${ipAddress.hostAddress}: time = ${duration.inWholeMilliseconds} ms")
        } else {
            println("Request timed out")
        }
    }

    val fails = numberOfIterations - listOfRTT.size

    println("\nPing stats for ${ipAddress.hostAddress}:")
    println("\tPackets: sent = $numberOfIterations, received = ${numberOfIterations - fails}, lost = $fails (${(fails.toDouble() / numberOfIterations) * 100}% loss)")
    println("Approximate round trip time in ms:")
    val minTime = if (listOfRTT.isEmpty()) 0 else listOfRTT.min().inWholeMilliseconds
    val maxTime = if (listOfRTT.isEmpty()) 0 else listOfRTT.max().inWholeMilliseconds
    val averageTime = if (listOfRTT.isEmpty()) 0 else listOfRTT.sumOf { it.inWholeMilliseconds } / listOfRTT.size
    println("\tMinimum time = ${minTime}ms, Maximum time = ${maxTime}ms, Average time = ${averageTime}ms")
}
