package com.example.transcriber.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TranscriptResponse {
    private String id;
    private String status;
    private String text;
    private List<Utterance> utterances;
    private String error;
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Utterance {
        private String speaker;
        private String text;
        private Integer start;
        private Integer end;
        private Double confidence;
    }
} 