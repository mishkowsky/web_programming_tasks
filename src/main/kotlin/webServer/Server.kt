package webServer

import kotlinx.coroutines.*
import mu.KotlinLogging
import java.io.*
import java.lang.Long.valueOf
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit


class Server internal constructor(private val socket: ServerSocket) : Closeable {
    constructor(port: Int = 80, opTimeout: Duration = 5000.milliseconds) : this(ServerSocket(port)) {
        socket.soTimeout = opTimeout.toInt(DurationUnit.MILLISECONDS)
    }

    private lateinit var job: Job
    var isClosed = false
        private set

    private var activeConnectionsCount = AtomicInteger(0)

    suspend fun start() = coroutineScope {
        if (isClosed) return@coroutineScope

        job = launch {
            outer@ while (isActive) {
                logger.debug { "Listening on port ${socket.localPort}" }
                val deferredClient = async { socket.acceptCancellable() }

                val clientConnection = deferredClient.await() ?: break@outer
                activeConnectionsCount.incrementAndGet()
                logger.debug { "Established a connection with a client: ${clientConnection.inetAddress}:${clientConnection.port}" }
                handleClient(clientConnection)
            }
        }
    }

    private fun CoroutineScope.handleClient(client: Socket) = launch {
        BufferedReader(InputStreamReader(client.inputStream)).use { istream ->
            PrintWriter(OutputStreamWriter(client.outputStream)).use { out ->
                try {
                    val path = readFileRequest(client, istream)
                    logger.debug { "Request on file $path received from ${client.inetAddress.hostAddress}:${client.port}" }
                    if (path.isRegularFile()) {
                        logger.debug { "Sending response to ${client.inetAddress.hostAddress}:${client.port}" }
                        if (path.toString().endsWith("html", ignoreCase = true)) {
                            sendResponse(out, path.readText(), contentType = "text/html")
                        } else {
                            sendResponse(out, path.readText())
                        }
                    } else {
                        logger.debug { "Request $path from ${client.inetAddress.hostAddress}:${client.port}, not found" }
                        RequestException("Not Found", 404).sendError(out)
                    }
                } catch (e: RequestException) {
                    logger.error { "Exception during reading request from ${client.inetAddress.hostAddress}:${client.port}: $e" }
                    e.sendError(out)
                }
            }
        }
        delay(100)
        logger.debug { "Closing connection on port ${client.inetAddress}:${client.port}" }
        client.close()
        activeConnectionsCount.decrementAndGet()
    }

    private fun CoroutineScope.readFileRequest(client: Socket, istream: BufferedReader): Path {
        if (!isActive || client.isClosed) throw RequestException("Connection closed")
        val clientMethodLine = istream.readLine() ?: throw RequestException("No messages were received")

        return clientMethodLine.parseMethodLine()
    }

    private fun RequestException.sendError(out: PrintWriter) {
        val code = errorCode ?: INTERNAL_SERVER_ERROR_CODE
        out.println(
            """
HTTP/1.1 $code $message
            """.trimIndent()
        )
    }

    private fun sendResponse(out: PrintWriter, content: String, contentType: String = "text/plain") {
        out.println(
            """
HTTP/1.1 200 OK
Content-Type: $contentType 

$content
""".trimIndent()
        )
    }

    override fun close() {
        isClosed = true
        runBlocking {
            job.cancel()
            socket.close()
        }
    }

    fun stop() = close()
}

private val logger = KotlinLogging.logger { }

private const val INTERNAL_SERVER_ERROR_CODE = 500

private fun ServerSocket.acceptCancellable() = try {
    accept()
} catch (e: IOException) {
    null
}

private fun String.parseMethodLine(): Path {
    val trimmed = trimIndent()
    if (trimmed.isEmpty()) {
        throw RequestException("Invalid empty request")
    }

    val split = trimmed.split("""\s+""".toRegex())
    if (!split.first().equals("GET", ignoreCase = true)) {
        throw RequestException("Unknown method: ${split.first()}")
    }

    if (!split.last().startsWith("HTTP/", ignoreCase = true)) {
        throw RequestException("Unknown scheme: ${split.last()}")
    }

    if (split.last().substringAfter("HTTP/") != "1.1") {
        throw RequestException("Unknown HTTP version: ${split.last().substringAfter("HTTP/")}")
    }
    val absolutePathString = Path("").absolutePathString()
    val relativePath = split.subList(1, split.lastIndex).joinToString(" ")
    val fullPath = absolutePathString.plus("/src/main/resources").plus(relativePath)
    return Path(fullPath)
}

class RequestException(message: String, val errorCode: Int? = null) : Exception(message) {
    override fun toString(): String = "RequestException(code=${errorCode}, message: ${message})"
}

fun main() {
    runBlocking {
        val serverPort = 8080
        val serverWorkTime = valueOf(100000000000L).milliseconds

        launch {
            startServer(serverPort, timeToWork = serverWorkTime)
        }
    }
}

fun CoroutineScope.startServer(port: Int, timeToWork: Duration? = null): Server {
    val server = Server(port = port)
    launch {
        withContext(Dispatchers.IO) {
            server.start()
        }
    }
    launch {
        timeToWork?.let { time ->
            delay(time)
            server.stop()
        }
    }
    return server
}
