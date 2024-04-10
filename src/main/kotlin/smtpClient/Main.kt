package smtpClient

fun main() {

    val serverAddress = "smtp.gmail.com"
    val fromAddress = "a@gmail.com"
    val toAddress = "b@gmail.com"
    val message = "message from a to b"

    launch(true, serverAddress, fromAddress, toAddress, message)
}

fun launch(isSSL: Boolean, serverAddress: String, fromAddress: String, toAddress: String, message: String) {

    val client = if (isSSL) SecureClient() else SMTPClient()
    client.use { client1 ->
        client1.openConnectionWith(serverAddress)
        client1.send {
            this.from = fromAddress
            this.to = toAddress
            this.text = message
        }
    }
}
