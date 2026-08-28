package `in`.pcncloud.hotel.tailscale.embed

import java.io.InputStream

class GoInputStreamAdapter(private val inputStream: InputStream) : libtailscale.InputStream {
    override fun read(): ByteArray? {
        val buffer = ByteArray(4096)
        val read = inputStream.read(buffer)
        if (read == -1) return null
        return buffer.copyOf(read)
    }

    override fun close() {
        inputStream.close()
    }
}
