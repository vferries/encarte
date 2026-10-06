package io.github.vferries.encarte.importing

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import io.github.vferries.encarte.core.barcode.BarcodeEncoder
import io.github.vferries.encarte.core.barcode.BarcodeFormat
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** Test PDFs built in the test, so no binary fixture hides how they were made. */
object TestPdfs {
    /** One A4 page per entry of [pages], each code drawn below the previous one. */
    fun withCodes(vararg pages: List<Pair<BarcodeFormat, String>>): ByteArray {
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, codes ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                var top = 60f
                for ((format, value) in codes) {
                    val bitmap = render(format, value)
                    page.canvas.drawBitmap(bitmap, 60f, top, Paint())
                    top += bitmap.height + 40f
                }
                document.finishPage(page)
            }
            return ByteArrayOutputStream().also(document::writeTo).toByteArray()
        } finally {
            document.close()
        }
    }

    /** Black modules on white, 3 px per module (1 pt per pixel in the page), with a quiet zone. */
    private fun render(format: BarcodeFormat, value: String): Bitmap {
        val matrix = BarcodeEncoder.encode(value, format)
        val scale = 3
        val border = 30
        val height = if (format.isTwoDimensional) matrix.height * scale else 90
        val bitmap = Bitmap.createBitmap(matrix.width * scale + 2 * border, height + 2 * border, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        for (y in 0 until height) for (x in 0 until matrix.width * scale) {
            val moduleY = if (format.isTwoDimensional) y / scale else 0
            if (matrix.get(x / scale, moduleY)) bitmap.setPixel(x + border, y + border, Color.BLACK)
        }
        return bitmap
    }

    /**
     * A one-page PDF encrypted by the standard security handler (revision 2, RC4 40-bit) with the user password
     * "secret": PdfDocument cannot encrypt. The O and U entries follow the PDF 1.7 reference, algorithms 3.2 to 3.4.
     */
    fun passwordProtected(): ByteArray {
        val id = "encarte-test-pdf".toByteArray(Charsets.ISO_8859_1)
        val permissions = -44
        val owner = rc4(md5(pad("owner")).copyOf(5), pad("secret"))
        val key = md5(pad("secret") + owner + littleEndian(permissions) + id).copyOf(5)
        val user = rc4(key, PASSWORD_PADDING)
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] >>",
            "<< /Filter /Standard /V 1 /R 2 /O <${owner.hex()}> /U <${user.hex()}> /P $permissions >>",
        )
        val pdf = StringBuilder("%PDF-1.4\n")
        val offsets = objects.mapIndexed { index, body ->
            pdf.length.also { pdf.append("${index + 1} 0 obj\n$body\nendobj\n") }
        }
        val xref = pdf.length
        pdf.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { pdf.append("%010d 00000 n \n".format(it)) }
        pdf.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R /Encrypt 4 0 R /ID [<${id.hex()}> <${id.hex()}>] >>\n")
        pdf.append("startxref\n$xref\n%%EOF\n")
        return pdf.toString().toByteArray(Charsets.ISO_8859_1)
    }

    private val PASSWORD_PADDING = intArrayOf(
        0x28, 0xBF, 0x4E, 0x5E, 0x4E, 0x75, 0x8A, 0x41, 0x64, 0x00, 0x4E, 0x56, 0xFF, 0xFA, 0x01, 0x08,
        0x2E, 0x2E, 0x00, 0xB6, 0xD0, 0x68, 0x3E, 0x80, 0x2F, 0x0C, 0xA9, 0xFE, 0x64, 0x53, 0x69, 0x7A,
    ).map { it.toByte() }.toByteArray()

    private fun pad(password: String): ByteArray =
        (password.toByteArray(Charsets.ISO_8859_1) + PASSWORD_PADDING).copyOf(32)

    private fun md5(bytes: ByteArray): ByteArray = MessageDigest.getInstance("MD5").digest(bytes)

    private fun littleEndian(value: Int) = ByteArray(4) { (value shr (8 * it)).toByte() }

    private fun rc4(key: ByteArray, data: ByteArray): ByteArray {
        val state = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + state[i] + (key[i % key.size].toInt() and 0xFF)) and 0xFF
            state[i] = state[j].also { state[j] = state[i] }
        }
        var i = 0
        j = 0
        return ByteArray(data.size) { n ->
            i = (i + 1) and 0xFF
            j = (j + state[i]) and 0xFF
            state[i] = state[j].also { state[j] = state[i] }
            (data[n].toInt() xor state[(state[i] + state[j]) and 0xFF]).toByte()
        }
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}
