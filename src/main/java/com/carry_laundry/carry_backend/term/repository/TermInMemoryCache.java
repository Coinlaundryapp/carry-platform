package com.carry_laundry.carry_backend.term.repository;

import com.carry_laundry.carry_backend.term.application.record.TermDetail;
import com.carry_laundry.carry_backend.term.domain.entity.Term;
import com.carry_laundry.carry_backend.term.domain.entity.TermMeta;
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

    public void updateCache(TermMeta termMeta, Term term) {
        TermDetail termDetail = new TermDetail(term.getId(), termMeta.getCode(),
            termMeta.getTermType(), term.getVersionCount(), term.getCreatedAt());
        map.put(termMeta.getCode(), termDetail);
    }

    public List<TermDetail> getMandatoryTerms() {
        return map.values().stream()
            .filter(TermDetail::isMandatory)
            .toList();
    }

    @PostConstruct
    protected void init() {
        termRepository.findTermDetailsByLastVersion()
            .doOnNext(termDetail -> map.put(termDetail.code(), termDetail))
            .subscribe();
    }

}
