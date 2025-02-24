package arne.jellyfindocumentsprovider.hacks

import android.os.MemoryFile
import java.io.FileDescriptor
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.javaMethod

class MemoryFileFD(data: ByteArray) : MemoryFile("", data.size), AutoCloseable {
    init {
        this.writeBytes(data, 0, 0, data.size)
    }

    val fd get() = getFileDescriptor.invoke(this) as FileDescriptor
    override fun close() {
        super.close()
    }

    companion object {
        private val getFileDescriptor =
            MemoryFile::class.declaredMemberFunctions.first { it.name == "getFileDescriptor" }.javaMethod!!

    }
}