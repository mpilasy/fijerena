package org.njarasoa.fijerena.core.network.smb

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.EnumSet

class SmbClient(
    private val host: String,
    private val shareName: String,
    private val domain: String = "WORKGROUP",
    private val username: String? = null,
    private val password: String? = null
) {
    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null

    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client = SMBClient()
            connection = client!!.connect(host)
            val authContext = if (username != null && password != null) {
                AuthenticationContext(username, password.toCharArray(), domain)
            } else {
                AuthenticationContext.anonymous()
            }
            session = connection!!.authenticate(authContext)
            share = session!!.connectShare(shareName) as DiskShare
            Result.success(Unit)
        } catch (e: Exception) {
            disconnect()
            Result.failure(e)
        }
    }

    fun disconnect() {
        try { share?.close() } catch (_: Exception) {}
        try { session?.close() } catch (_: Exception) {}
        try { connection?.close() } catch (_: Exception) {}
        try { client?.close() } catch (_: Exception) {}
        share = null
        session = null
        connection = null
        client = null
    }

    fun isConnected(): Boolean = share != null

    fun listDirectory(path: String): List<FileIdBothDirectoryInformation> {
        val diskShare = share ?: throw IllegalStateException("Not connected")
        return diskShare.list(path).filter {
            it.fileName != "." && it.fileName != ".."
        }
    }

    fun isDirectory(path: String): Boolean {
        val diskShare = share ?: return false
        return try {
            val info = diskShare.getFileInformation(path)
            val attrs = info.basicInformation.fileAttributes
            attrs and 0x10L != 0L // FILE_ATTRIBUTE_DIRECTORY
        } catch (_: Exception) {
            false
        }
    }

    fun openInputStream(path: String): InputStream {
        val diskShare = share ?: throw IllegalStateException("Not connected")
        val file = diskShare.openFile(
            path,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
        return file.inputStream
    }
}
