package com.AiResearcher.backend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
public class ResearchService {

    private final String geminiApiKey;
    private final WebClient webclient;
    private final ObjectMapper objectMapper;
    private final Bucket rateLimiter;

    public ResearchService(
            WebClient.Builder webClientBuilder,
            @Value("${gemini.api.url}") String geminiApiUrl,
            @Value("${gemini.api.key}") String geminiApiKey) {
        this.webclient = webClientBuilder.baseUrl(geminiApiUrl).build();
        this.geminiApiKey = geminiApiKey;
        this.objectMapper = new ObjectMapper();

        // Free tier: 10 requests per minute (one request every 6 seconds)
        // Set to 8 RPM to be safe
        Bandwidth limit = Bandwidth.classic(8, Refill.intervally(8, Duration.ofMinutes(1)));
        this.rateLimiter = Bucket.builder().addLimit(limit).build();
    }

    public String processContent(ResearchRequest researchRequest) {
        // Build Prompt
        String prompt = buildPrompt(researchRequest);

        // Build request body using proper JSON serialization
        Map<String, Object> requestBody = buildRequestBody(prompt);

        // Wait for rate limit permit (blocks until available)
        try {
            rateLimiter.asBlocking().consume(1);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        log.info("Rate limit permit acquired, making API call");

        // Query The AI Model API
        String response = webclient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1beta/models/gemini-2.5-flash:generateContent")
                        .build())
                .header("x-goog-api-key", geminiApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(Retry.backoff(5, Duration.ofSeconds(12))  // 12 seconds = 60s / 5 requests
                        .maxBackoff(Duration.ofMinutes(2))
                        .jitter(0.5)
                        .filter(throwable -> {
                            if (throwable instanceof WebClientResponseException.TooManyRequests) {
                                log.warn("429 Rate limit hit, retrying with backoff");
                                return true;
                            }
                            return false;
                        })
                        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                            log.error("Rate limit retry exhausted after {} attempts", retrySignal.totalRetries());
                            throw new RuntimeException(
                                    "Gemini API rate limit exceeded after " + retrySignal.totalRetries() +
                                            " retries. Please try again in 1-2 minutes.");
                        }))
                .timeout(Duration.ofSeconds(60))
                .doOnError(e -> log.error("Error calling Gemini API: {}", e.getMessage()))
                .block();

        // Extract and Return Response
        return extractedResponse(response);
    }

    private Map<String, Object> buildRequestBody(String prompt) {
        // Proper JSON serialization - handles escaping automatically
        return Map.of(
                "contents", java.util.List.of(
                        Map.of(
                                "parts", java.util.List.of(
                                        Map.of("text", prompt)
                                )
                        )
                )
        );
    }

    private String extractedResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

            // Check for error responses
            if (root.has("error")) {
                String errorMessage = root.path("error").path("message").asText();
                log.error("Gemini API error: {}", errorMessage);
                return "Error: " + errorMessage;
            }

            // Validate candidates exist
            if (root.path("candidates").isMissingNode() ||
                    !root.path("candidates").isArray() ||
                    root.path("candidates").size() == 0) {
                log.warn("No candidates in response");
                return "No response generated. Content may have been filtered.";
            }

            // Extract text from response
            JsonNode textNode = root.path("candidates")
                    .get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text");

            if (textNode.isMissingNode() || textNode.asText().isEmpty()) {
                log.warn("Empty text in response");
                return "Empty response from AI model.";
            }

            return textNode.asText();

        } catch (JsonProcessingException e) {
            log.error("Failed to parse Gemini API response", e);
            throw new RuntimeException("Failed to parse AI response", e);
        }
    }

    private String buildPrompt(ResearchRequest request) {
        StringBuilder prompt = new StringBuilder();

        switch (request.getOperation()) {
            case "summarize":
                prompt.append("Please provide a comprehensive summary of the following content. ")
                        .append("Focus on the main points, key findings, and essential information. ")
                        .append("Keep it concise but informative.\n\n")
                        .append(request.getContent());
                break;

            case "analyze":
                prompt.append("Please analyze the following content in detail. ")
                        .append("Identify key themes, patterns, strengths, weaknesses, and implications. ")
                        .append("Provide insights and critical evaluation.\n\n")
                        .append(request.getContent());
                break;

            case "extract_key_points":
                prompt.append("Extract and list the key points from the following content. ")
                        .append("Present them as clear, actionable bullet points.\n\n")
                        .append(request.getContent());
                break;

            case "fact_check":
                prompt.append("Review the following content and verify the factual claims made. ")
                        .append("Identify any statements that may be inaccurate, misleading, or require verification. ")
                        .append("Provide sources or context where possible.\n\n")
                        .append(request.getContent());
                break;

            case "sentiment_analysis":
                prompt.append("Analyze the sentiment and tone of the following content. ")
                        .append("Identify whether it's positive, negative, or neutral, and explain the emotional undertones.\n\n")
                        .append(request.getContent());
                break;

            case "translate":
                prompt.append("Translate the following content to ")
                        .append(request.getTargetLanguage() != null ? request.getTargetLanguage() : "English")
                        .append(". Maintain the original meaning and tone.\n\n")
                        .append(request.getContent());
                break;

            case "expand":
                prompt.append("Expand on the following content by adding more detail, examples, and explanations. ")
                        .append("Make it more comprehensive while maintaining accuracy.\n\n")
                        .append(request.getContent());
                break;

            case "simplify":
                prompt.append("Simplify the following content to make it easier to understand. ")
                        .append("Use plain language and break down complex concepts.\n\n")
                        .append(request.getContent());
                break;

            case "generate_questions":
                prompt.append("Generate thoughtful questions based on the following content. ")
                        .append("Create questions that test understanding and encourage deeper thinking.\n\n")
                        .append(request.getContent());
                break;

            case "rewrite":
                prompt.append("Rewrite the following content to improve clarity, style, and readability. ")
                        .append(request.getTone() != null ? "Use a " + request.getTone() + " tone. " : "")
                        .append("Maintain the original meaning.\n\n")
                        .append(request.getContent());
                break;

            case "categorize":
                prompt.append("Categorize and organize the following content into logical sections or themes. ")
                        .append("Identify the main categories and explain the classification.\n\n")
                        .append(request.getContent());
                break;

            case "validate":
                prompt.append("Validate the following content for accuracy, completeness, and logical consistency. ")
                        .append("Identify any gaps, errors, or areas that need improvement.\n\n")
                        .append(request.getContent());
                break;

            case "outline":
                prompt.append("Create a structured outline of the following content. ")
                        .append("Organize it hierarchically with main topics and subtopics.\n\n")
                        .append(request.getContent());
                break;

            default:
                prompt.append("Please process the following content:\n\n")
                        .append(request.getContent());
                break;
        }

        return prompt.toString();
    }
}
