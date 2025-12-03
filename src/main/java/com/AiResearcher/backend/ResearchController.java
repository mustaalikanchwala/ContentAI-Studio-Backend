package com.AiResearcher.backend;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/research")
@AllArgsConstructor
public class ResearchController {
//    Service Object Reference
    private final ResearchService researchService;

//    Process Content
    @PostMapping("/process")
    public ResponseEntity<ResearchResponse> processContent(@RequestBody ResearchRequest researchRequest){
        String result = researchService.processContent(researchRequest);
        ResearchResponse response = new ResearchResponse(result);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

}
