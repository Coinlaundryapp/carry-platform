package com.carry_laundry.carry_backend.term.repository;

import com.carry_laundry.carry_backend.term.application.record.TermDetail;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class TermInMemoryCache {

    private final Map<String, TermDetail> map;
    private final TermRepository termRepository;

    public TermInMemoryCache(TermRepository termRepository) {
        this.map = new ConcurrentHashMap<>();
        this.termRepository = termRepository;
    }

    @PostConstruct
    public void init() {
        termRepository.findTermDetailsByLastVersion()
            .doOnNext(termDetail -> map.put(termDetail.code(), termDetail))
            .subscribe();
    }

    public List<TermDetail> getMandatoryTerms() {
        return map.values().stream()
            .filter(TermDetail::isMandatory)
            .toList();
    }
}
