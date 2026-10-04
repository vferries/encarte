package io.github.vferries.encarte.backup

import android.util.Log
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.FileHeader
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private const val TAG = "CatimaArchive"
private val IMAGE_ENTRY = Regex("""^card_(\d+)_(front|back|icon)\.png$""")

enum class ImageSide { FRONT, BACK, ICON }

data class CatimaImageRef(val cardId: Int, val side: ImageSide) {
    val entryName: String get() = "card_${cardId}_${side.name.lowercase()}.png"
}

class ArchiveImage(val ref: CatimaImageRef, val writeTo: (OutputStream) -> Unit)

class PasswordRequiredException : Exception("Archive is encrypted")

class WrongPasswordException(cause: Throwable) : Exception("Wrong password", cause)

class CatimaArchive(
    private val maxCards: Int = 10_000,
    private val maxUncompressedBytes: Long = 500L * 1024 * 1024,
) {
    fun readCards(file: File, password: CharArray?): List<CatimaCard> {
        val cards = open(file, password).use { zip ->
            if (!zip.isValidZipFile) {
                CatimaCsv.read(file.readText())
            } else {
                checkReadable(zip, password)
                val csvHeader = zip.fileHeaders.firstOrNull { it.baseName == CatimaCsv.FILE_NAME }
                    ?: throw CatimaFormatException("No ${CatimaCsv.FILE_NAME} in archive")
                CatimaCsv.read(readEntry(zip, csvHeader) { it.readBytes().toString(Charsets.UTF_8) })
            }
        }
        if (cards.size > maxCards) throw CatimaFormatException("Too many cards: ${cards.size}")
        return cards
    }

    fun forEachImage(file: File, password: CharArray?, action: (CatimaImageRef, InputStream) -> Unit) {
        open(file, password).use { zip ->
            if (!zip.isValidZipFile) return
            checkReadable(zip, password)
            for (header in zip.fileHeaders) {
                if (header.isDirectory || header.baseName == CatimaCsv.FILE_NAME) continue
                val ref = imageRef(header.baseName)
                if (ref == null) {
                    Log.w(TAG, "Ignoring unexpected archive entry ${header.baseName}")
                    continue
                }
                readEntry(zip, header) { action(ref, it) }
            }
        }
    }

    /** Writes catima.csv then the images; closes [output]. Catima rejects any other entry. */
    fun write(output: OutputStream, csv: String, images: List<ArchiveImage>, password: CharArray?) {
        val encrypt = password != null
        val zip = if (encrypt) ZipOutputStream(output, password) else ZipOutputStream(output)
        zip.use {
            it.putNextEntry(parameters(CatimaCsv.FILE_NAME, encrypt))
            it.write(csv.toByteArray(Charsets.UTF_8))
            it.closeEntry()
            for (image in images) {
                it.putNextEntry(parameters(image.ref.entryName, encrypt))
                image.writeTo(it)
                it.closeEntry()
            }
        }
    }

    private fun open(file: File, password: CharArray?): ZipFile =
        if (password != null) ZipFile(file, password) else ZipFile(file)

    private fun checkReadable(zip: ZipFile, password: CharArray?) {
        if (zip.isEncrypted && password == null) throw PasswordRequiredException()
        if (zip.fileHeaders.sumOf { it.uncompressedSize } > maxUncompressedBytes) {
            throw CatimaFormatException("Archive too large")
        }
    }

    private fun <T> readEntry(zip: ZipFile, header: FileHeader, block: (InputStream) -> T): T = try {
        zip.getInputStream(header).use(block)
    } catch (e: ZipException) {
        Log.w(TAG, "Cannot read archive entry ${header.baseName}: ${e.javaClass.simpleName}")
        if (e.type == ZipException.Type.WRONG_PASSWORD) throw WrongPasswordException(e) else throw e
    } catch (e: IOException) {
        Log.w(TAG, "Cannot read archive entry ${header.baseName}: ${e.javaClass.simpleName}")
        // AES's 2-byte verifier lets ~1/65536 wrong passwords through; they fail later as a plain IOException.
        if (header.isEncrypted) throw WrongPasswordException(e) else throw e
    }

    private fun imageRef(name: String): CatimaImageRef? {
        val match = IMAGE_ENTRY.matchEntire(name) ?: return null
        val id = match.groupValues[1].toIntOrNull() ?: return null
        return CatimaImageRef(id, ImageSide.valueOf(match.groupValues[2].uppercase()))
    }

    private val FileHeader.baseName: String get() = fileName.substringAfterLast('/')

    private fun parameters(name: String, encrypt: Boolean) = ZipParameters().apply {
        fileNameInZip = name
        compressionMethod = CompressionMethod.DEFLATE
        if (encrypt) {
            isEncryptFiles = true
            encryptionMethod = EncryptionMethod.AES
            aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
        }
    }
}
