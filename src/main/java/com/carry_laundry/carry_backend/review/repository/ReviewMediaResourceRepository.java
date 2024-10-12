package com.carry_laundry.carry_backend.review.repository;

import com.carry_laundry.carry_backend.review.domain.entity.ReviewMediaResource;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewMediaResourceRepository extends
    ReactiveCrudRepository<ReviewMediaResource, Long> {

}
