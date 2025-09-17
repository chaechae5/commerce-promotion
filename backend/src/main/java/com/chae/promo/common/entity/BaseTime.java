package com.chae.promo.common.entity;


import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

@MappedSuperclass
@Getter
public abstract class BaseTime extends CreatedAtBase{
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
