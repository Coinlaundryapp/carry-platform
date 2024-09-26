package org.example.coin_laundry_app_backend.laundromat.domain.entity;

import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.locationtech.jts.geom.Point;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table("laundromats")
@AllArgsConstructor
public class Laundromat {

    @Id
    private Long id;
    private String name;
    private String address;
    private Point locationCoordinate;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    // 생성용
    public Laundromat(String name, String address, Point locationCoordinate) {
        this.name = Objects.requireNonNull(name, "세탁소 이름은 필수입니다.");
        this.address = Objects.requireNonNull(address, "세탁소 주소는 필수입니다.");
        this.locationCoordinate = Objects.requireNonNull(locationCoordinate, "세탁소 위치는 필수입니다.");
    }
}
