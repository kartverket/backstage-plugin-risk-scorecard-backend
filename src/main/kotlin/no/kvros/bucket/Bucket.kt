package no.kvros.bucket

import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import no.kvros.ros.models.ROSWrapperObject
import org.apache.tomcat.util.http.fileupload.FileUploadException
import org.springframework.stereotype.Component


@Component
class Bucket(
    private val storage: Storage
) {
    val bucketName: String = "ros-tabellformat"
    val fileName: String = "kotlin-test"

    fun uploadFile(content: ROSWrapperObject) {
        try {
            val blobInfo = BlobInfo.newBuilder(bucketName, fileName).build()
            storage.create(blobInfo, content.ros.toByteArray())
        } catch (e: Exception) {
            throw FileUploadException("Failed to upload file")
        }
    }
}