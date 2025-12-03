package com.AiResearcher.backend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.lang.classfile.instruction.SwitchCase;
import java.time.Duration;

@Service
public class ResearchService {



    private final String geminiApiKey;

    private final WebClient webclient;

    public ResearchService(WebClient.Builder webClientBuilder ,   @Value("${gemini.api.url}")
    String geminiApiUrl , @Value("${gemini.api.key}") String geminiApiKey) {
        this.webclient = webClientBuilder.baseUrl(geminiApiUrl).build();
        this.geminiApiKey = geminiApiKey;
    }

    public String processContent(ResearchRequest researchRequest) {
//    Build Prompt
        String prompt = buildPrompt(researchRequest);
//    Query The Ai Modal Api
        String requestBody = String.format("""
                {
                    "contents": [
                      {
                        "parts": [
                          {
                            "text": "%s"
                          }
                        ]
                      }
                    ]
                  }
                """,prompt);
//    Parse the response
        String response = webclient.post()
                .uri(uriBuilder -> uriBuilder.path("/v1beta/models/gemini-2.0-flash:generateContent").build())
                .header("x-goog-api-key",geminiApiKey)
                .header("Content-Type","application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(60))
                .block();
//   Extract and  Return Response
        return extractedResponse(response);

    }

    private String extractedResponse(String response) {
    try {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response);
       if(root.path("candidates").isMissingNode() || !root.path("candidates").isArray() || root.path("candidates").size()==0){
           return "Content is null or empty";
       }
        return root.path("candidates")
                .get(0)
                .path("content")
                .path("parts")
                .get(0)
                .path("text")
                .asText();

    }catch (JsonProcessingException e){
        throw new RuntimeException(e);
    }
    }

    private String buildPrompt(ResearchRequest request){
        StringBuilder prompt = new StringBuilder();
        switch (request.getOperation()){
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
