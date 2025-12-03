package com.AiResearcher.backend;

import lombok.Data;

@Data
public class ResearchRequest {
    private String content;
    private String operation;
    private String tone;
    private String targetLanguage;
}
