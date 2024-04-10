package udpPinger

import kotlinx.coroutines.*
import java.net.SocketTimeoutException
import kotlin.time.Duration

fun main() {

    val port = 1234
    val numberOfPings = 8

    launch(port, numberOfPings)

}

fun launch(port: Int, numberOfPings: Int) {

    if (port < 0 || port > 65535)
        throw IllegalStateException("Port $port is illegal. Port should be in range 0..65535")

    if (numberOfPings < 0)
        throw IllegalStateException("Number of pings should be positive integer, but got $numberOfPings")
    
    val server = Server(port)
    val client = Client()

    runBlocking {
        launch {
            launch {
                withContext(Dispatchers.IO) {
                    server.start()
                }
            }

            launch {
                delay(100)
                var failures = 0
                val responseTimes = ArrayList<Duration>(numberOfPings)
                val serverAddress = "127.0.0.1"
                repeat(numberOfPings) {
                    try {
                        responseTimes += client.ping(serverAddress, port)
                    } catch (_: SocketTimeoutException) {
                        failures++
                    }
                }
                client.close()
                server.close()

                println("\nPing stats for ${serverAddress}:")
                println("\tPackets: sent = $numberOfPings, received = ${numberOfPings - failures}, lost = $failures (${(failures.toDouble() / numberOfPings) * 100}% loss)")
                println("Approximate round trip time in ms:")
                println("\tMinimum time = ${responseTimes.min().inWholeMilliseconds}ms, Maximum time = ${responseTimes.max().inWholeMilliseconds}ms, Average time= ${responseTimes.sumOf { it.inWholeMilliseconds } / responseTimes.size}ms")
            }

        }
    }
}