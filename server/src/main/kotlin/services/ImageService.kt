package services

import org.imgscalr.Scalr
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

class ImageService {
    private val uploadDir = "uploadDir"
    init {
        // Create upload directory if it doesn't exist
        val directory = File(uploadDir)
        if (!directory.exists()) {
            directory.mkdirs()
        }

        // Create thumbnails subdirectory
        val thumbnailsDir = File("$uploadDir/thumbnails")
        if (!thumbnailsDir.exists()) {
            thumbnailsDir.mkdirs()
        }
    }

    fun saveImage(fileBytes: ByteArray, originalFileName: String, mimeType: String): ImageSaveResult {
        try {

            val fileName = originalFileName
            val filePath = "$uploadDir/$fileName"

            // Save original image
            val file = File(filePath)
            file.writeBytes(fileBytes)

            // Generate thumbnail for images
            val thumbnailUrl = if (mimeType.startsWith("image/")) {
                generateThumbnail(fileBytes, fileName)
            } else {
                null
            }

            return ImageSaveResult(
                fileName = fileName,
                fileUrl = "/uploads/$fileName",
                thumbnailUrl = thumbnailUrl?.let { "/uploads/thumbnails/$fileName" },
                fileSize = fileBytes.size.toLong(),
                mimeType = mimeType
            )
        } catch (e: Exception) {
            throw ImageProcessingException("Failed to save image: ${e.message}")
        }
    }

    private fun generateThumbnail(fileBytes: ByteArray, fileName: String): String? {
        return try {
            val originalImage: BufferedImage = ImageIO.read(ByteArrayInputStream(fileBytes))
            val thumbnail = Scalr.resize(originalImage, Scalr.Method.QUALITY, Scalr.Mode.AUTOMATIC, 300, 200)
            val extension = getFileExtension(fileName)
            val outputStream = ByteArrayOutputStream()
            ImageIO.write(thumbnail, extension, outputStream)

            val thumbnailPath = "$uploadDir/thumbnails/$fileName"
            File(thumbnailPath).writeBytes(outputStream.toByteArray())

            thumbnailPath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteImage(fileName: String) {
        try {
            // Delete original
            val originalFile = File("$uploadDir/$fileName")
            if (originalFile.exists()) {
                originalFile.delete()
            }

            // Delete thumbnail
            val thumbnailFile = File("$uploadDir/thumbnails/$fileName")
            if (thumbnailFile.exists()) {
                thumbnailFile.delete()
            }
        } catch (e: Exception) {
            // Log error but don't throw
            e.printStackTrace()
        }
    }

    private fun getFileExtension(fileName: String): String {
        return fileName.substringAfterLast('.').lowercase()
    }

    fun validateImage(fileBytes: ByteArray, mimeType: String, maxSize: Long = 5 * 1024 * 1024) {
        // Check file size
        if (fileBytes.size > maxSize) {
            throw ImageValidationException("File size exceeds maximum allowed size of 5MB")
        }

        // Check MIME type
        val allowedTypes = listOf("image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp")
        if (!allowedTypes.contains(mimeType)) {
            throw ImageValidationException("Invalid file type. Allowed types: JPEG, PNG, GIF, WebP")
        }
    }
}

data class ImageSaveResult(
    val fileName: String,
    val fileUrl: String,
    val thumbnailUrl: String?,
    val fileSize: Long,
    val mimeType: String
)

class ImageProcessingException(message: String) : Exception(message)
class ImageValidationException(message: String) : Exception(message)
