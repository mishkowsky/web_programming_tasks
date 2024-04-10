
package proxy

import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.Path
import mu.KotlinLogging
import java.net.UnknownHostException
import java.nio.file.InvalidPathException


fun main() {
    val server = ProxyServer()
    server.run()
}

private val logger = KotlinLogging.logger { }

class ProxyServer {

    fun run() {

        // Создаем серверный сокет, привязываем его к порту и начинаем слушать
        val serverSocket = ServerSocket()
        val address = "127.0.0.1"
        val port = 8080
        serverSocket.bind(InetSocketAddress(address, port))
        serverSocket.soTimeout = 100000000

        while (true) {
            // Начинаем получать данные от клиента
            logger.info("Ready, waiting for connections...")
            val incomingConnection = serverSocket.accept()
            logger.info("New connection with ${incomingConnection.inetAddress}")

            val clientRequest = BufferedReader(InputStreamReader(incomingConnection.inputStream)).readLine() ?: continue
            logger.info("Incoming request: $clientRequest")

            // Извлекаем имя файла из сообщения
            val filename = clientRequest.split(" ")[1].substring(1)

            val cacheDir = File(
                javaClass.classLoader.getResource("cache")?.toURI() ?: throw IOException()
            ).parentFile.toString()
            try {
                val cachePath = Path("$cacheDir/$filename")
            } catch (e: InvalidPathException) {
                continue
            }


            try {
                // Проверяем, есть ли файл в кэше
                val cachePath = Path("$cacheDir/$filename")
                Files.newBufferedReader(cachePath).use { reader ->
                    logger.info("Requested file was cached before, returning cached file")
                    val requestedContent = reader.readLines()
                    incomingConnection.outputStream.write("HTTP/1.1 200 OK\n".toByteArray())
                    incomingConnection.outputStream.write("Content-Type: text/html\n\n".toByteArray())
                    requestedContent.forEach { line ->
                        incomingConnection.outputStream.write(line.toByteArray())
                    }
                }
            } catch (e: InvalidPathException) {
                // ignore unacceptable path chars
                continue
            } catch (e: IOException) {
                // Создаем сокет на прокси-сервере
                val remoteSocket = Socket()
                try {
                    // Соединяемся с сокетом по порту 80
                    val hostname = filename.split("/")[0]
                    val requestedFileNameWithoutHostname = filename.substring(hostname.length)
                    logger.info("File wasn't cached before, so connecting with $hostname")
                    remoteSocket.connect(InetSocketAddress(hostname, 80))

                    val remoteWriter = PrintWriter(OutputStreamWriter(remoteSocket.outputStream), true)
                    remoteWriter.println("GET $requestedFileNameWithoutHostname")

                    val remoteReader = BufferedReader(InputStreamReader(remoteSocket.inputStream))
                    val requestedContent = remoteReader.readLines().dropWhile { it.isNotBlank() }.joinToString("\n")
                    logger.info("Got response from original server: $requestedContent")

                    // Создаем новый файл в кэше для запрашиваемого файла
                    // А также отправляем ответ из буфера и соответствующий файл на сокет клиента
                    val folderPath = filename.substring(0, filename.lastIndexOf("/"))
                    Files.createDirectory(Paths.get("$cacheDir/$folderPath"))

                    val file = File("$cacheDir/$filename")
                    file.createNewFile()

                    file.writeText("HTTP/1.1 200 OK \n\n$requestedContent")
                    remoteSocket.close()
                } catch (e: UnknownHostException) {
                    // ignore browser requests for favicon.ico & etc
                    logger.info { "UnknownHostException: $e" }
                }
            } finally {
                incomingConnection.outputStream.flush()
                incomingConnection.close()
            }
        }
    }
}
