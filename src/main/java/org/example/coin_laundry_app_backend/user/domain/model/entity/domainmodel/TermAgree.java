package org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.coin_laundry_app_backend.user.domain.model.entity.data.TermAgreeData;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TermAgree {

    Long id;
    User user;
    Term term;
    Boolean agreeYn;
    LocalDateTime updatedAt;

    public static TermAgree from(TermAgreeData termAgreeData) {
        User user = User.from(termAgreeData.getUser());
        Term term = Term.from(termAgreeData.getTerm());
        return new TermAgree(termAgreeData.getId(), user, term, termAgreeData.getAgreeYn(),
            termAgreeData.getUpdatedAt());
    }

    public static TermAgree of(User user, Term term, boolean agreeYn, LocalDateTime updatedAt) {
        return new TermAgree(null, user, term, agreeYn, updatedAt);
    }

    public TermAgreeData toData() {
        return new TermAgreeData(id, user.toData(), term.toData(), agreeYn, updatedAt);
    }
}
