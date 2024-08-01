package org.example.coin_laundry_app_backend.user.application.service;


import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.Term;
import org.example.coin_laundry_app_backend.user.domain.model.value.TermInfo;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class TermAdminService {

    private final List<Term> requiredTerms;
    private final TermService termService;

    public TermAdminService(TermService termService) {
        this.termService = termService;
        this.requiredTerms = verityRequiredTerms();
    }

    public Mono<Term> createNewTerm(Term term) {
        TermInfo termInfo = term.getTermInfo();
        return Mono.from(termService.findTermsByTitle(termInfo.getTitle()).flatMap(
                terms -> Flux.defer(() -> Flux.error(
                    new IllegalArgumentException("이미 존재하는 약관입니다: " + termInfo.getTitle()))))
            .switchIfEmpty(Mono.defer(() -> {
                Mono<Term> newTerm = termService.addTerm(term);
                return newTerm.doOnNext(newTermData -> {
                    if (newTermData.isMandatory()) {
                        requiredTerms.add(newTermData);
                    }
                });
            }))).cast(Term.class);
    }

    public Mono<Term> updateTerm(Term term) {
        TermInfo termInfo = term.getTermInfo();
        return termService.findTermsByTitle(termInfo.getTitle()).collectList().flatMap(terms -> {
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
                if (newTermData.isMandatory()) {
                    requiredTerms.add(newTermData);
                }
            });
        });
    }

    public List<Term> getRequiredTerms() {
        return Collections.unmodifiableList(requiredTerms);
    }

    private List<Term> verityRequiredTerms() {
        List<Term> terms = new CopyOnWriteArrayList<>();
        termService.findAllTerms()
            .filter(Term::isMandatory)
            .groupBy(term -> term.getTermInfo().getTitle())
            .flatMap(
                group -> group.sort(Comparator.comparing(Term::getVersion)).last())
            .collectList().subscribe(terms::addAll);
        return terms;
    }
}
