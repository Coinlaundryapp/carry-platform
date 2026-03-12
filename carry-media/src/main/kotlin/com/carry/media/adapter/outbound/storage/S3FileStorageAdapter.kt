package com.carry.media.adapter.outbound.storage

import com.carry.media.application.port.outbound.FileStoragePort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.time.Duration

@Component
class S3FileStorageAdapter(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    @Value("\${cloud.aws.s3.bucket}") private val bucket: String,
) : FileStoragePort {

    override fun upload(filePath: String, content: ByteArray, contentType: String): Long {
        val request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(filePath)
            .contentType(contentType)
            .contentLength(content.size.toLong())
            .build()

        s3Client.putObject(request, RequestBody.fromBytes(content))
        return content.size.toLong()
    }

    override fun generatePresignedUrl(filePath: String, expirationMinutes: Int): String {
        val getObjectRequest = GetObjectRequest.builder()
            .bucket(bucket)
            .key(filePath)
            .build()

        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(expirationMinutes.toLong()))
            .getObjectRequest(getObjectRequest)
            .build()

        return s3Presigner.presignGetObject(presignRequest).url().toString()
    }

    override fun delete(filePath: String) {
        val request = DeleteObjectRequest.builder()
            .bucket(bucket)
            .key(filePath)
            .build()

        s3Client.deleteObject(request)
    }
}
