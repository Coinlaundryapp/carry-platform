package com.carry.infra.s3

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI

@Configuration
class S3Config {

    @Value("\${cloud.aws.s3.endpoint:}")
    private lateinit var endpoint: String

    @Value("\${cloud.aws.credentials.access-key:}")
    private lateinit var accessKey: String

    @Value("\${cloud.aws.credentials.secret-key:}")
    private lateinit var secretKey: String

    @Value("\${cloud.aws.region.static:ap-northeast-2}")
    private lateinit var region: String

    @Bean
    fun s3Client(): S3Client {
        val builder = S3Client.builder()
            .region(Region.of(region))

        if (accessKey.isNotBlank() && secretKey.isNotBlank()) {
            builder.credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)
                )
            )
        }
        if (endpoint.isNotBlank()) {
            builder.endpointOverride(URI.create(endpoint))
                .forcePathStyle(true)
        }
        return builder.build()
    }
}
