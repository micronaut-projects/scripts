package com.github.models;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record Owner(String login) {
}
