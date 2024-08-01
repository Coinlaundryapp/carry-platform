package org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.coin_laundry_app_backend.user.domain.model.entity.data.TermData;
import org.example.coin_laundry_app_backend.user.domain.model.enums.TermType;
import org.example.coin_laundry_app_backend.user.domain.model.value.TermInfo;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Term {

    private Long id;
    private final TermType termType;
    private final TermInfo termInfo;
    private final String context;
    private final LocalDateTime createdAt;

    public static Term from(TermData termData) {
        TermInfo info = TermInfo.of(termData.getTermInfoTitle(), termData.getTermInfoVersion());
        return new Term(termData.getId(), termData.getTermType(), info, termData.getContext(),
            termData.getCreatedAt());
    }

    public static Term of(TermType termType, TermInfo termInfo, String context,
        LocalDateTime createdAt) {
        return new Term(null, termType, termInfo, context, createdAt);
    }

    public TermData toData() {
        return new TermData(id, termType, termInfo.getTitle(), termInfo.getVersion(), context,
            createdAt);
    }

    public boolean isMandatory() {
        return termType == TermType.MANDATORY;
    }

    public int getVersion() {
        return termInfo.getVersion();
    }
}
