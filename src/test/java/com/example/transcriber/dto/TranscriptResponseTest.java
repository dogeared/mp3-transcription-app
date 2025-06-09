package com.example.transcriber.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TranscriptResponseTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testTranscriptResponseDeserialization() throws Exception {
        String json = """
            {
                "id": "transcript-123",
                "status": "completed",
                "text": "Hello world",
                "utterances": [
                    {
                        "speaker": "A",
                        "text": "Hello",
                        "start": 0,
                        "end": 1000,
                        "confidence": 0.95
                    },
                    {
                        "speaker": "B",
                        "text": "world",
                        "start": 1000,
                        "end": 2000,
                        "confidence": 0.98
                    }
                ]
            }
            """;

        TranscriptResponse response = objectMapper.readValue(json, TranscriptResponse.class);

        assertEquals("transcript-123", response.getId());
        assertEquals("completed", response.getStatus());
        assertEquals("Hello world", response.getText());
        assertNotNull(response.getUtterances());
        assertEquals(2, response.getUtterances().size());

        TranscriptResponse.Utterance firstUtterance = response.getUtterances().get(0);
        assertEquals("A", firstUtterance.getSpeaker());
        assertEquals("Hello", firstUtterance.getText());
        assertEquals(Integer.valueOf(0), firstUtterance.getStart());
        assertEquals(Integer.valueOf(1000), firstUtterance.getEnd());
        assertEquals(0.95, firstUtterance.getConfidence());

        TranscriptResponse.Utterance secondUtterance = response.getUtterances().get(1);
        assertEquals("B", secondUtterance.getSpeaker());
        assertEquals("world", secondUtterance.getText());
        assertEquals(Integer.valueOf(1000), secondUtterance.getStart());
        assertEquals(Integer.valueOf(2000), secondUtterance.getEnd());
        assertEquals(0.98, secondUtterance.getConfidence());
    }

    @Test
    void testTranscriptResponseSerialization() throws Exception {
        TranscriptResponse response = new TranscriptResponse();
        response.setId("transcript-456");
        response.setStatus("processing");
        response.setText("Test transcript");

        TranscriptResponse.Utterance utterance = new TranscriptResponse.Utterance();
        utterance.setSpeaker("A");
        utterance.setText("Test");
        utterance.setStart(0);
        utterance.setEnd(500);
        utterance.setConfidence(0.9);

        response.setUtterances(List.of(utterance));

        String json = objectMapper.writeValueAsString(response);

        assertTrue(json.contains("\"id\":\"transcript-456\""));
        assertTrue(json.contains("\"status\":\"processing\""));
        assertTrue(json.contains("\"text\":\"Test transcript\""));
        assertTrue(json.contains("\"speaker\":\"A\""));
        assertTrue(json.contains("\"confidence\":0.9"));
    }

    @Test
    void testTranscriptResponseWithError() throws Exception {
        String json = """
            {
                "id": "transcript-error",
                "status": "error",
                "error": "Audio file format not supported"
            }
            """;

        TranscriptResponse response = objectMapper.readValue(json, TranscriptResponse.class);

        assertEquals("transcript-error", response.getId());
        assertEquals("error", response.getStatus());
        assertEquals("Audio file format not supported", response.getError());
        assertNull(response.getText());
        assertNull(response.getUtterances());
    }

    @Test
    void testTranscriptResponseIgnoresUnknownProperties() throws Exception {
        String json = """
            {
                "id": "transcript-123",
                "status": "completed",
                "text": "Hello world",
                "unknown_field": "should be ignored",
                "another_unknown": 12345
            }
            """;

        // Should not throw exception due to @JsonIgnoreProperties(ignoreUnknown = true)
        TranscriptResponse response = objectMapper.readValue(json, TranscriptResponse.class);

        assertEquals("transcript-123", response.getId());
        assertEquals("completed", response.getStatus());
        assertEquals("Hello world", response.getText());
    }

    @Test
    void testUtteranceStandaloneDeserialization() throws Exception {
        String json = """
            {
                "speaker": "C",
                "text": "Standalone utterance",
                "start": 5000,
                "end": 7000,
                "confidence": 0.87
            }
            """;

        TranscriptResponse.Utterance utterance = objectMapper.readValue(json, TranscriptResponse.Utterance.class);

        assertEquals("C", utterance.getSpeaker());
        assertEquals("Standalone utterance", utterance.getText());
        assertEquals(Integer.valueOf(5000), utterance.getStart());
        assertEquals(Integer.valueOf(7000), utterance.getEnd());
        assertEquals(0.87, utterance.getConfidence());
    }

    @Test
    void testEmptyTranscriptResponse() throws Exception {
        String json = "{}";

        TranscriptResponse response = objectMapper.readValue(json, TranscriptResponse.class);

        assertNotNull(response);
        assertNull(response.getId());
        assertNull(response.getStatus());
        assertNull(response.getText());
        assertNull(response.getUtterances());
        assertNull(response.getError());
    }
} 