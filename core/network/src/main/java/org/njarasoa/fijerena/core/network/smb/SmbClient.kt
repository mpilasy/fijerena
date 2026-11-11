package org.njarasoa.fijerena.core.network.smb

import android.util.Log
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
import java.io.FilterInputStream
import java.io.InputStream
import java.util.EnumSet
import com.hierynomus.smbj.share.File as SmbFile

class SmbClient(
    private val host: String,
    private val shareName: String,
    private val domain: String = "WORKGROUP",
    private val username: String? = null,
    private val password: String? = null,
) {
    private val TAG = "SmbClient"

    // A monitor apart from `this`, held only for short field reads, writes and swaps, never across
    // blocking socket I/O: a disconnect() on Main (leaving the SMB screen while it connects) would
    // else wait out the 30-60 s handshake, an ANR.
    private val lock = Any()

    @Volatile private var generation = 0L

    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null

    suspend fun connect(): Result<Unit> =
        withContext(Dispatchers.IO) {
            val myGeneration = synchronized(lock) { ++generation }
            try {
                // The slow handshake runs outside the lock, on local objects only, so nothing here
                // can block a concurrent disconnect().
                val newClient = SMBClient()
                val newConnection = newClient.connect(host)
                val authContext =
                    if (username != null && password != null) {
                        AuthenticationContext(username, password.toCharArray(), domain)
                    } else {
                        AuthenticationContext.anonymous()
                    }
                val newSession = newConnection.authenticate(authContext)
                val newShare = newSession.connectShare(shareName) as DiskShare

                // Only the publish is under the lock: a field swap, no I/O. If disconnect() (or a
                // newer connect()) ran during the handshake, this generation is stale: discard what
                // was built, not bring back a connection the caller already closed.
                val superseded =
                    synchronized(lock) {
                        if (generation != myGeneration) {
                            true
                        } else {
                            client = newClient
                            connection = newConnection
                            session = newSession
                            share = newShare
                            false
                        }
                    }
                if (superseded) {
                    closeQuietly(newShare, newSession, newConnection, newClient)
                    Result.failure(IllegalStateException("Connection superseded by a concurrent disconnect()"))
                } else {
                    Result.success(Unit)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun disconnect() {
        var oldShare: DiskShare?
        var oldSession: Session?
        var oldConnection: Connection?
        var oldClient: SMBClient?
        synchronized(lock) {
            generation++
            oldShare = share
            oldSession = session
            oldConnection = connection
            oldClient = client
            share = null
            session = null
            connection = null
            client = null
        }
        closeQuietly(oldShare, oldSession, oldConnection, oldClient)
    }

    private fun closeQuietly(
        share: DiskShare?,
        session: Session?,
        connection: Connection?,
        client: SMBClient?,
    ) {
        try {
            share?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close share", e)
        }
        try {
            session?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close session", e)
        }
        try {
            connection?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close connection", e)
        }
        try {
            client?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close client", e)
        }
    }

    fun isConnected(): Boolean = synchronized(lock) { share != null }

    fun listDirectory(path: String): List<FileIdBothDirectoryInformation> {
        // Copy the reference out from under the lock, as connect()/disconnect() do: a slow host
        // would else block a concurrent disconnect() on this lock.
        val diskShare = synchronized(lock) { share } ?: throw IllegalStateException("Not connected")
        return diskShare.list(path).filter {
            it.fileName != "." && it.fileName != ".."
        }
    }

    fun isDirectory(path: String): Boolean {
        val diskShare = synchronized(lock) { share }
        return if (diskShare == null) {
            false
        } else {
            try {
                val info = diskShare.getFileInformation(path)
                val attrs = info.basicInformation.fileAttributes
                attrs and 0x10L != 0L // FILE_ATTRIBUTE_DIRECTORY
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check if directory: $path", e)
                false
            }
        }
    }

    fun openInputStream(path: String): InputStream {
        val diskShare = synchronized(lock) { share } ?: throw IllegalStateException("Not connected")
        val file =
            diskShare.openFile(
                path,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                null,
            )
        return SmbFileInputStream(file)
    }
}

/** Closes the underlying SMB [File] handle (not released by closing its stream alone). */
private class SmbFileInputStream(
    private val file: SmbFile,
) : FilterInputStream(file.inputStream) {
    override fun close() {
        super.close()
        file.close()
    }
}
