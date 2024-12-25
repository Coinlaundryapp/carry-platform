package com.carry_laundry.carry_backend.term.repository;

import com.carry_laundry.carry_backend.term.domain.entity.Term;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TermRepository extends ReactiveCrudRepository<Term, Long> {

}
