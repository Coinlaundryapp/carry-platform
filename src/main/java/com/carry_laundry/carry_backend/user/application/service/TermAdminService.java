package com.carry_laundry.carry_backend.user.application.service;


import com.carry_laundry.carry_backend.user.domain.entity.domainmodel.Term;
import com.carry_laundry.carry_backend.user.domain.value.TermInfo;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class TermAdminService {

    private final Map<String, Term> requiredTerms;
    private final TermService termService;

    public TermAdminService(TermService termService) {
        this.termService = termService;
        this.requiredTerms = verityRequiredTerms();
    }

    public Mono<Term> createNewTerm(Term term) {
        TermInfo termInfo = term.getTermInfo();
        return Mono.from(termService.getTermsByTitle(termInfo.getTitle()).flatMap(
                terms -> Flux.defer(() -> Flux.error(
                    new IllegalArgumentException("이미 존재하는 약관입니다: " + termInfo.getTitle()))))
            .switchIfEmpty(Mono.defer(() -> {
                Mono<Term> newTerm = termService.addTerm(term);
                return newTerm.doOnNext(newTermData -> {
                    String termTitle = newTermData.getTermInfo().getTitle();
                    if (newTermData.isMandatory()) {
                        requiredTerms.put(termTitle, newTermData);
                    }
                });
            }))).cast(Term.class);
    }

    public Mono<Term> updateTerm(Term term) {
        TermInfo termInfo = term.getTermInfo();
        return termService.getTermsByTitle(termInfo.getTitle()).collectList().flatMap(terms -> {
            if (terms.isEmpty()) {
                return Mono.defer(() -> Mono.error(
                    new IllegalArgumentException("업데이트할 약관이 존재하지 않습니다: " + termInfo.getTitle())));
            }
            boolean predict = terms.stream()
                .anyMatch(t -> t.getVersion() >= termInfo.getVersion());
            if (predict) {
                return Mono.defer(() -> Mono.error(new IllegalArgumentException(
                    "이미 존재하는 약관 버전이 같거나 더 높습니다: " + termInfo.getVersion())));
            }
            return termService.addTerm(term).doOnNext(newTermData -> {
                String termTitle = newTermData.getTermInfo().getTitle();
                if (newTermData.isMandatory()) {
                    requiredTerms.put(termTitle, newTermData);
                }
            });
        });
    }

    public List<Term> getRequiredTerms() {
        return requiredTerms.values().stream().toList();
    }

    private Map<String, Term> verityRequiredTerms() {
        Map<String, Term> map = new ConcurrentHashMap<>();
        termService.getAllTerms()
            .filter(Term::isMandatory)
            .groupBy(term -> term.getTermInfo().getTitle())
            .flatMap(
                group -> group.sort(Comparator.comparing(Term::getVersion)).last())
            .collectList()
            .subscribe(
                terms -> terms.forEach(term -> map.put(term.getTermInfo().getTitle(), term)));
        return map;
    }
}
