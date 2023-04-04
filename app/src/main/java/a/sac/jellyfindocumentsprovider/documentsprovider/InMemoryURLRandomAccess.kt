package a.sac.jellyfindocumentsprovider.documentsprovider

import io.ktor.utils.io.errors.IOException
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class InMemoryURLRandomAccess(private val url: URL) : URLRandomAccess(url) {
    private val data: ByteArray

    init {
        data = downloadUrlToByteArrayOutputStream().toByteArray()
    }

    @Throws(IOException::class)
    private fun downloadUrlToByteArrayOutputStream(): ByteArrayOutputStream {
        val httpConnection = url.openConnection() as HttpURLConnection
        if (httpConnection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException("Failed to download file. ${httpConnection.responseCode}")
        }

        val inputStream: InputStream = httpConnection.inputStream
        val byteArrayOutputStream = ByteArrayOutputStream()

        try {
            inputStream.copyTo(byteArrayOutputStream)
        } finally {
            inputStream.close()
            httpConnection.disconnect()
        }

        return byteArrayOutputStream
    }

    override val length: Long
        get() = data.size.toLong()

    override fun read(offset: Long, length: Int, data: ByteArray): Int {
        System.arraycopy(data, offset.toInt(), data, 0, length)
        return length
    }

    override fun release() {
    }
}