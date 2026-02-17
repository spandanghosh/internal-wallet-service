package com.dinoventures.wallet.model;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class Account {
    private Long id;
    private String type;
    private String name;
    private OffsetDateTime createdAt;
}
