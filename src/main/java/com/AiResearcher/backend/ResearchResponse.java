package com.AiResearcher.backend;

import lombok.Data;

@Data
public class ResearchResponse {
    private final String result;

    public ResearchResponse(String result) {
        this.result = result;
    }
}
