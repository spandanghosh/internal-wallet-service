package com.dinoventures.wallet.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateAccountRequest {

    @NotBlank(message = "type is required")
    @Pattern(regexp = "^(user|system)$", message = "type must be 'user' or 'system'")
    private String type;

    @NotBlank(message = "name is required")
    private String name;
}
