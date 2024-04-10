package udpPinger

import kotlinx.coroutines.*
import java.io.Closeable
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket


class Server internal constructor(private val socket: DatagramSocket) : Closeable {
    constructor(port: Int) : this(DatagramSocket(port))

    private lateinit var serverJob: Job
    var isClosed = false
        private set

    private val buffer = ByteArray(256)

    suspend fun start() = coroutineScope {
        if (isClosed) return@coroutineScope

        serverJob = launch {
            outer@ while (isActive && !isClosed) {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receiveCancellable(packet) ?: break

                val receivedMassage = String(packet.data, 0, packet.length)

                if (getDropWithChance(30)) {
                    continue@outer
                }

                val responseMassage = "\"$receivedMassage\""

                socket.send(
                    DatagramPacket(
                        responseMassage.toByteArray(),
                        responseMassage.length,
                        packet.address,
                        packet.port
                    )
                )
            }
        }
    }

    override fun close() {
        isClosed = true
        runBlocking {
            serverJob.cancel()
            socket.close()
        }
    }

}

private fun getDropWithChance(percent: Int): Boolean = (1..100).random() <= percent


private fun DatagramSocket.receiveCancellable(packet: DatagramPacket) = try {
    receive(packet)
} catch (e: IOException) {
    null
}