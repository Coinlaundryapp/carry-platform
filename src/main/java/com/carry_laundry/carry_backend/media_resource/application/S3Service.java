package com.carry_laundry.carry_backend.media_resource.application;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.async.ResponsePublisher;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@Service
public class S3Service {

    private final S3AsyncClient s3AsyncClient;
    private final String bucket;
    private final ExecutorService executorService;

    public S3Service(S3AsyncClient s3AsyncClient, @Value("${aws.s3.bucket}") String bucket) {
        this.s3AsyncClient = s3AsyncClient;
        this.bucket = bucket;
        this.executorService = ForkJoinPool.commonPool();
    }

    public Flux<Mono<PutObjectResponse>> uploadFile(Flux<DataBuffer> dataBufferFlux,
        String filename, MediaType contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(filename)
            .contentType(contentType.toString())
            .build();
        return dataBufferFlux.map(buffer -> Mono.fromFuture(
            s3AsyncClient.putObject(putObjectRequest, AsyncRequestBody.fromInputStream(builder -> {
                builder.contentLength((long) buffer.readableByteCount());
                builder.inputStream(buffer.asInputStream());
                builder.executor(executorService);
            }))));
    }

    public Mono<ResponsePublisher<GetObjectResponse>> downloadFile(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();
        return Mono.fromFuture(
            s3AsyncClient.getObject(getObjectRequest, AsyncResponseTransformer.toPublisher()));
    }

}
