package com.carry_laundry.carry_backend.media_resource.application;

import com.carry_laundry.carry_backend.media_resource.application.record.FileMetadata;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
@RequiredArgsConstructor
public class MediaResourceDownloadService {

    private final S3Service s3Service;
    private final ResourceMetadataService resourceMetadataService;

    public Mono<Tuple2<FileMetadata, Flux<DataBuffer>>> downloadFile(UUID id) {
        return resourceMetadataService.findById(id)
            .flatMap(resourceMetadata -> s3Service.downloadFile(resourceMetadata.getFilePath())
                .map(getObjectResponseResponsePublisher -> {
                    String contentType = getObjectResponseResponsePublisher.response()
                        .contentType();
                    long contentLength = getObjectResponseResponsePublisher.response()
                        .contentLength();
                    FileMetadata metadata = new FileMetadata(resourceMetadata.getId().toString(),
                        contentType,
                        contentLength);
                    Flux<DataBuffer> dataBufferFlux = Flux.from(getObjectResponseResponsePublisher)
                        .map(ByteBuffer::rewind)
                        .map(buffer -> {
                            byte[] bytes = new byte[buffer.remaining()];
                            buffer.get(bytes);
                            return new DefaultDataBufferFactory().wrap(bytes);
                        });
                    return Tuples.of(metadata, dataBufferFlux);
                }))
            .switchIfEmpty(Mono.fromCallable(() -> {
                throw new FileNotFoundException("File not found");
            }));
    }
}
