package com.carry_laundry.carry_backend.review.application;

import com.carry_laundry.carry_backend.review.domain.entity.ReviewMediaResource;
import com.carry_laundry.carry_backend.review.repository.ReviewMediaResourceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

@Service
@Transactional
@RequiredArgsConstructor
public class ReviewMediaResourceService {

    private final ReviewMediaResourceRepository reviewMediaResourceRepository;

    public Flux<ReviewMediaResource> saveAll(Long laundromatId, List<String> mediaUris) {
        return reviewMediaResourceRepository.saveAll(Flux.fromIterable(mediaUris)
            .map(mediaUri -> ReviewMediaResource.builder()
                .reviewId(laundromatId)
                .mediaUri(mediaUri)
                .build())
        );
    }
}
