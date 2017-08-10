package org.jetbrains.ktor.websocket

import kotlinx.coroutines.experimental.channels.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.util.*
import org.junit.*
import org.junit.rules.*
import java.io.*
import java.net.*
import java.nio.*
import java.time.*
import java.util.*
import java.util.concurrent.*
import kotlin.test.*

abstract class WebSocketHostSuite<THost : ApplicationHost>(hostFactory: ApplicationHostFactory<THost>) : HostTestBase<THost>(hostFactory) {
    @get:Rule
    val errors = ErrorCollector()

    private val socketReadTimeout = timeout.seconds.toInt() * 1000

    @Test
    fun testWebSocketGenericSequence() {
        val collected = LinkedBlockingQueue<String>()

        createAndStartServer {
            application.install(WebSockets)
            webSocket("/") {
                try {
                    incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            collected.add(frame.readText())
                        }
                    }
                } catch (t: Throwable) {
                    errors.addError(t)
                }
            }
        }

        Socket("localhost", port).use { socket ->
            socket.soTimeout = socketReadTimeout

            // send upgrade request
            socket.outputStream.apply {
                write("""
                GET / HTTP/1.1
                Host: localhost:$port
                Upgrade: websocket
                Connection: Upgrade
                Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
                Origin: http://localhost:$port
                Sec-WebSocket-Protocol: chat
                Sec-WebSocket-Version: 13
                """.trimIndent().replace("\n", "\r\n").toByteArray())
                write("\r\n\r\n".toByteArray())
                flush()
            }

            val status = socket.inputStream.parseStatus()
            assertEquals(HttpStatusCode.SwitchingProtocols.value, status.value)

            val headers = socket.inputStream.parseHeaders()
            assertEquals("Upgrade", headers[HttpHeaders.Connection])
            assertEquals("websocket", headers[HttpHeaders.Upgrade])

            socket.outputStream.apply {
                // text message with content "Hello"
                writeHex("0x81 0x05 0x48 0x65 0x6c 0x6c 0x6f")
                flush()

                // close frame with code 1000
                writeHex("0x88 0x02 0x03 0xe8")
                flush()
            }

            socket.assertCloseFrame()
        }

        assertEquals("Hello", collected.take())
    }

    @Test
    fun testWebSocketPingPong() {
        val s = createServer(null) {
            install(CallLogging)
            install(WebSockets)

            routing {
                webSocket("/") {
                    timeout = Duration.ofSeconds(120)
                    pingInterval = Duration.ofMillis(50)

                    try {
                        incoming.consumeEach {
                        }
                    } catch (t: Throwable) {
                        errors.addError(t)
                    }
                }
            }
        }
        startServer(s)

        Socket("localhost", port).use { socket ->
            socket.soTimeout = socketReadTimeout

            // send upgrade request
            socket.outputStream.apply {
                write("""
                GET / HTTP/1.1
                Host: localhost:$port
                Upgrade: websocket
                Connection: Upgrade
                Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
                Origin: http://localhost:$port
                Sec-WebSocket-Protocol: chat
                Sec-WebSocket-Version: 13
                """.trimIndent().replace("\n", "\r\n").toByteArray())
                write("\r\n\r\n".toByteArray())
                flush()
            }

            socket.inputStream.parseStatus()
            socket.inputStream.parseHeaders()

            for (i in 1..5) {
                val frame = socket.inputStream.readFrame()
                assertEquals(FrameType.PING, frame.frameType)
                assertEquals(true, frame.fin)
                assertTrue { frame.buffer.hasRemaining() }

                Serializer().apply {
                    enqueue(Frame.Pong(frame.buffer.copy()))
                    val buffer = ByteArray(1024)
                    val bb = ByteBuffer.wrap(buffer)
                    serialize(bb)
                    bb.flip()

                    socket.getOutputStream().write(buffer, 0, bb.remaining())
                    socket.getOutputStream().flush()
                }
            }

            socket.outputStream.apply {
                // close frame with code 1000
                writeHex("0x88 0x02 0x03 0xe8")
                flush()
            }

            socket.assertCloseFrame()
        }
    }

    @Test
    fun testReceiveMessages() {
        val count = 125
        val template = (1..count).map { (it and 0x0f).toString(16) }.joinToString("")
        val bytes = template.toByteArray()

        val collected = LinkedBlockingQueue<String>()

        createAndStartServer {
            application.install(WebSockets)

            webSocket("/") {
                try {
                    incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            collected.add(frame.readText())
                        }
                    }
                } catch (t: Throwable) {
                    errors.addError(t)
                    collected.put(t.toString())
                }
            }
        }

        Socket("localhost", port).use { socket ->
            socket.soTimeout = socketReadTimeout

            // send upgrade request
            socket.outputStream.apply {
                write("""
                GET / HTTP/1.1
                Host: localhost:$port
                Upgrade: websocket
                Connection: Upgrade
                Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
                Origin: http://localhost:$port
                Sec-WebSocket-Protocol: chat
                Sec-WebSocket-Version: 13
                """.trimIndent().replace("\n", "\r\n").toByteArray())
                write("\r\n\r\n".toByteArray())
                flush()
            }

            val status = socket.inputStream.parseStatus()
            assertEquals(HttpStatusCode.SwitchingProtocols.value, status.value)

            val headers = socket.inputStream.parseHeaders()
            assertEquals("Upgrade", headers[HttpHeaders.Connection])
            assertEquals("websocket", headers[HttpHeaders.Upgrade])

            socket.outputStream.apply {
                for (i in 1..count) {
                    writeHex("0x81")
                    write(i)
                    write(bytes, 0, i)
                    flush()
                }

                // close frame with code 1000
                writeHex("0x88 0x02 0x03 0xe8")
                flush()
            }

            socket.assertCloseFrame()
        }

        for (i in 1..count) {
            val expected = template.substring(0, i)
            assertEquals(expected, collected.take())
        }

        assertNull(collected.poll())
    }

    @Test
    fun testProduceMessages() {
        val count = 125
        val template = (1..count).map { (it and 0x0f).toString(16) }.joinToString("")

        createAndStartServer {
            application.install(WebSockets)

            webSocket("/") {
                for (i in 1..count) {
                    send(Frame.Text(template.substring(0, i)))
                }
            }
        }

        Socket("localhost", port).use { socket ->
            socket.soTimeout = socketReadTimeout

            // send upgrade request
            socket.outputStream.apply {
                write("""
                GET / HTTP/1.1
                Host: localhost:$port
                Upgrade: websocket
                Connection: Upgrade
                Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
                Origin: http://localhost:$port
                Sec-WebSocket-Protocol: chat
                Sec-WebSocket-Version: 13
                """.trimIndent().replace("\n", "\r\n").toByteArray())
                write("\r\n\r\n".toByteArray())
                flush()
            }

            val status = socket.inputStream.parseStatus()
            assertEquals(HttpStatusCode.SwitchingProtocols.value, status.value)

            val headers = socket.inputStream.parseHeaders()
            assertEquals("Upgrade", headers[HttpHeaders.Connection])
            assertEquals("websocket", headers[HttpHeaders.Upgrade])

            socket.getInputStream().apply {
                for (i in 1..count) {
                    val f = readFrame()
                    assertEquals(FrameType.TEXT, f.frameType)
                    assertEquals(template.substring(0, i), f.buffer.getString(Charsets.ISO_8859_1))
                }
            }

            socket.outputStream.apply {
                // close frame with code 1000
                writeHex("0x88 0x02 0x03 0xe8")
                flush()
            }

            socket.assertCloseFrame()
        }
    }

    @Test
    fun testBigFrame() {
        val content = ByteArray(20 * 1024 * 1024)
        Random().nextBytes(content)

        val sendBuffer = ByteBuffer.allocate(content.size + 100)

        Serializer().apply {
            enqueue(Frame.Binary(true, ByteBuffer.wrap(content)))
            serialize(sendBuffer)

            sendBuffer.flip()
        }

        createAndStartServer {
            application.install(WebSockets)

            application.routing {
                webSocket("/") {
                    val f = incoming.receive()

                    val copied = f.copy()
                    outgoing.send(copied)

                    flush()
                }
            }
        }

        Socket("localhost", port).use { socket ->
            socket.soTimeout = socketReadTimeout

            // send upgrade request
            socket.outputStream.apply {
                write("""
                GET / HTTP/1.1
                Host: localhost:$port
                Upgrade: websocket
                Connection: Upgrade
                Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
                Origin: http://localhost:$port
                Sec-WebSocket-Protocol: chat
                Sec-WebSocket-Version: 13
                """.trimIndent().replace("\n", "\r\n").toByteArray())
                write("\r\n\r\n".toByteArray())
                flush()
            }

            val status = socket.inputStream.parseStatus()
            assertEquals(HttpStatusCode.SwitchingProtocols.value, status.value)

            val headers = socket.inputStream.parseHeaders()
            assertEquals("Upgrade", headers[HttpHeaders.Connection])
            assertEquals("websocket", headers[HttpHeaders.Upgrade])

            socket.getOutputStream().apply {
                write(sendBuffer.array(), 0, sendBuffer.remaining())
                flush()
            }

            socket.getInputStream().apply {
                val frame = readFrame()

                assertEquals(FrameType.BINARY, frame.frameType)
                assertEquals(content.size, frame.buffer.remaining())

                val bytes = ByteArray(content.size)
                frame.buffer.get(bytes)

                assertTrue { bytes.contentEquals(content) }
            }

            socket.getOutputStream().apply {
                Serializer().apply {
                    enqueue(Frame.Close())
                    sendBuffer.clear()
                    serialize(sendBuffer)
                    sendBuffer.flip()
                }

                write(sendBuffer.array(), 0, sendBuffer.remaining())
                flush()
            }

            socket.assertCloseFrame()
        }
    }

    @Test
    fun testALotOfFrames() {
        val expectedCount = 100000L

        createAndStartServer {
            application.install(WebSockets)

            application.routing {
                webSocket("/") {
                    try {
                        var counter = 1L
                        incoming.consumeEach { frame ->
                            if (frame is Frame.Text) {
                                val numberRead = frame.readText().toLong()
                                assertEquals(counter, numberRead, "Wrong packet received")

                                counter++
                            }
                        }

                        assertEquals(expectedCount, counter - 1, "Not all frames received")
                    } catch (t: Throwable) {
                        errors.addError(t)
                    }
                }
            }
        }

        Socket("localhost", port).use { socket ->
            socket.soTimeout = socketReadTimeout

            // send upgrade request
            socket.outputStream.apply {
                write("""
                GET / HTTP/1.1
                Host: localhost:$port
                Upgrade: websocket
                Connection: Upgrade
                Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
                Origin: http://localhost:$port
                Sec-WebSocket-Protocol: chat
                Sec-WebSocket-Version: 13
                """.trimIndent().replace("\n", "\r\n").toByteArray())
                write("\r\n\r\n".toByteArray())
                flush()
            }

            val status = socket.inputStream.parseStatus()
            assertEquals(HttpStatusCode.SwitchingProtocols.value, status.value)

            val headers = socket.inputStream.parseHeaders()
            assertEquals("Upgrade", headers[HttpHeaders.Connection])
            assertEquals("websocket", headers[HttpHeaders.Upgrade])

            val sendBuffer = ByteBuffer.allocate(64)
            socket.getOutputStream().apply {
                for (i in 1L..expectedCount) {
                    sendBuffer.clear()
                    Serializer().apply {
                        enqueue(Frame.Text(true, ByteBuffer.wrap(i.toString().toByteArray())))
                        serialize(sendBuffer)

                        sendBuffer.flip()
                    }

                    write(sendBuffer.array(), 0, sendBuffer.remaining())
                }

                sendBuffer.clear()
                Serializer().apply {
                    enqueue(Frame.Close())
                    sendBuffer.clear()
                    serialize(sendBuffer)
                    sendBuffer.flip()
                }

                write(sendBuffer.array(), 0, sendBuffer.remaining())
                flush()
            }

            socket.assertCloseFrame()
        }
    }

    private fun Socket.assertCloseFrame(closeCode: Short = CloseReason.Codes.NORMAL.code) {
        loop@
        while (true) {
            val frame = getInputStream().readFrame()

            when (frame) {
                is Frame.Ping -> continue@loop
                is Frame.Close -> {
                    assertEquals(closeCode, frame.readReason()?.code)
                    close()
                    break@loop
                }
                else -> fail("Unexpected frame $frame: \n${hex(frame.buffer.getAll())}")
            }
        }
    }

    private fun OutputStream.writeHex(hex: String) = write(fromHexDump(hex))

    private fun fromHexDump(hex: String) = hex(hex.replace("0x", "").replace("\\s+".toRegex(), ""))

    private fun InputStream.readFrame(): Frame {
        val opcodeAndFin = readOrFail()
        val lenAndMask = readOrFail()

        val frameType = FrameType[opcodeAndFin and 0x0f] ?: throw IllegalStateException("Wrong opcode ${opcodeAndFin and 0x0f}")
        val fin = (opcodeAndFin and 0x80) != 0

        val len1 = lenAndMask and 0x7f
        val mask = (lenAndMask and 0x80) != 0

        assertFalse { mask } // we are not going to use masking in these tests

        val length = when (len1) {
            126 -> readShortBE().toLong()
            127 -> readLongBE()
            else -> len1.toLong()
        }

        assertTrue { len1 < 100000 } // in tests we are not going to use bigger frames
        // so if we fail here it is likely we have encoded frame wrong or stream is broken

        val bytes = readFully(length.toInt())
        return Frame.byType(fin, frameType, ByteBuffer.wrap(bytes))
    }

    private fun InputStream.parseStatus(): HttpStatusCode {
        val line = readLineISOCrLf()

        assertTrue(line.startsWith("HTTP/1.1"), "status line should start with HTTP version, actual content: $line")

        val statusCodeAndMessage = line.removePrefix("HTTP/1.1").trimStart()
        val statusCodeString = statusCodeAndMessage.takeWhile(Char::isDigit)
        val message = statusCodeAndMessage.removePrefix(statusCodeString).trimStart()

        return HttpStatusCode(statusCodeString.toInt(), message)
    }

    private fun InputStream.parseHeaders(): ValuesMap {
        val builder = ValuesMapBuilder(caseInsensitiveKey = true)

        while (true) {
            val line = readLineISOCrLf()
            if (line.isEmpty()) {
                return builder.build()
            }

            val (name, value) = line.split(":").map(String::trim)
            builder.append(name, value)
        }
    }

    private fun InputStream.readLineISOCrLf(): String {
        val sb = StringBuilder(256)

        while (true) {
            val rc = read()
            if (rc == -1 || rc == 0x0a) {
                return sb.toString()
            } else if (rc == 0x0d) {
            } else {
                sb.append(rc.toChar())
            }
        }
    }

    private fun InputStream.readOrFail(): Int {
        val rc = read()
        if (rc == -1) {
            throw EOFException()
        }
        return rc
    }
    private fun InputStream.readShortBE() = (readOrFail() shl 8) or readOrFail()
    private fun InputStream.readLongBE() = (readOrFail().toLong() shl 56) or
            (readOrFail().toLong() shl 48) or
            (readOrFail().toLong() shl 40) or
            (readOrFail().toLong() shl 32) or
            (readOrFail().toLong() shl 24) or
            (readOrFail().toLong() shl 16) or
            (readOrFail().toLong() shl 8) or
            readOrFail().toLong()

    private fun InputStream.readFully(size: Int): ByteArray {
        val array = ByteArray(size)
        var wasRead = 0

        while (wasRead < size) {
            val rc = read(array, wasRead, size - wasRead)
            if (rc == -1) {
                throw IOException("Unexpected EOF")
            }
            wasRead += rc
        }

        return array
    }
}
