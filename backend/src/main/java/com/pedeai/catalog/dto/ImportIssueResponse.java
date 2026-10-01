package com.pedeai.catalog.dto;

/** Um problema da importação: onde ({@code origin}, ex.: "linha 5") e o quê. */
public record ImportIssueResponse(String origin, String message) {
}
