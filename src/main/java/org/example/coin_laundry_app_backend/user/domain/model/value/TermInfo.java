package org.example.coin_laundry_app_backend.user.domain.model.value;

import java.util.Objects;
import lombok.Getter;

@Getter
public class TermInfo {

    private final String title;
    private final Integer version;

    private TermInfo(String title, int version) {
        this.title = verifyTitle(title);
        this.version = version;
    }

    public static TermInfo of(String title, int version) {
        return new TermInfo(title, version);
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TermInfo termInfo)) {
            return false;
        }

        return Objects.equals(getTitle(), termInfo.getTitle()) && Objects.equals(
            getVersion(), termInfo.getVersion());
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(getTitle());
        result = 31 * result + Objects.hashCode(getVersion());
        return result;
    }

    private String verifyTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be null or empty");
        }
        return title;
    }
}
