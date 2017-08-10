package org.jetbrains.ktor.servlet.tests

import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.servlet.*
import org.junit.*
import org.junit.rules.*
import java.io.*
import java.nio.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import javax.servlet.*
import kotlin.test.*

class ServletWriteChannelTest {

    private val os = TestServletOutputStream()
    private val ch = ServletWriteChannel(os)

    @get:Rule
    val errors = ErrorCollector()

    @get:Rule
    val to = Timeout(5L, TimeUnit.SECONDS)

    private val exec = Executors.newSingleThreadExecutor()
    private val ctx = exec.asCoroutineDispatcher()

    private val current = AtomicInteger()

    @After
    fun tearDown() {
        exec.shutdown()
    }

    @Test
    fun ensureTestImpl1() {
        var notified = false

        os.setWriteListener(object: WriteListener {
            override fun onWritePossible() {
                notified = true
            }

            override fun onError(t: Throwable?) {
                throw t!!
            }
        })

        assertFalse { os.isReady }
        os.ready(1)

        assertTrue { notified }
        assertTrue { os.isReady }
        os.write(1)
        assertFalse { os.isReady }
    }

    @Test
    fun testWriteBeforeReady() {
        launch {
            assertCheckpoint(1)

            ch.write(ByteBuffer.allocate(1))

            assertCheckpoint(3)
        }

        runBlocking {
            assertCheckpoint(2)
            os.ready(1)
        }

        finish(3)
    }

    private fun finish(n: Int) {
        val l = CountDownLatch(1)

        finish(n, l)

        if (!l.await(5L, TimeUnit.SECONDS)) {
            fail()
        }
    }

    private fun finish(n: Int, l: CountDownLatch) {
        launch {
            if (current.get() == n)
                l.countDown()
            else finish(n)
        }
    }

    private fun assertCheckpoint(n: Int) {
        assertEquals (n, current.incrementAndGet())
    }

    private fun launch(block: suspend () -> Unit): Job {
        val j = launch(ctx) {
            block()
        }

        j.invokeOnCompletion { t ->
            if (t != null) {
                errors.addError(t)
            }
        }

        return j
    }

    private class TestServletOutputStream : ServletOutputStream() {
        private val listener = AtomicReference<WriteListener?>(null)
        private val bytes = ByteArrayOutputStream()

        private val ready = AtomicInteger(0)

        private val isReadyCalled = AtomicBoolean(false)
        private val notifyWhenReady = AtomicBoolean(false)

        fun get() = bytes.toByteArray()

        fun ready(n: Int) {
            require(n > 0)

            ready.addAndGet(n)
            tryNotify()
        }

        private fun tryNotify() {
            if (notifyWhenReady.compareAndSet(true, false)) {
                listener.get()?.onWritePossible()
            }
        }

        override fun isReady(): Boolean {
            isReadyCalled.set(true)
            val rc = ready.get()

            if (rc == 0) {
                notifyWhenReady.set(true)
                if (ready.get() > 0) { // volatile read
                    tryNotify()
                }
            }

            return rc > 0
        }

        override fun write(b: Int) {
            if (!isReadyCalled.compareAndSet(true, false)) throw IllegalStateException("isReady wasn't called")

            ready.getAndUpdate { it ->
                if (it == 0) throw IllegalStateException("Not ready")
                it - 1
            }

            bytes.write(b)
        }

        override fun setWriteListener(writeListener: WriteListener?) {
            requireNotNull(writeListener)
            if (!listener.compareAndSet(null, writeListener)) {
                throw IllegalStateException()
            }
        }
    }
}