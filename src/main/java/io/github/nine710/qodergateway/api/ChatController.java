package io.github.nine710.qodergateway.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nine710.qodergateway.application.ChatGatewayService;
import io.github.nine710.qodergateway.support.model.OpenAiChatRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/chat")
public class ChatController {

    private final ChatGatewayService chatGatewayService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public ChatController(ChatGatewayService chatGatewayService, ObjectMapper objectMapper, Validator validator) {
        this.chatGatewayService = chatGatewayService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @PostMapping("/completions")
    public Object complete(@RequestBody String rawRequest) {
        OpenAiChatRequest request = parseRequest(rawRequest);
        if (Boolean.TRUE.equals(request.stream())) {
            return chatGatewayService.stream(request);
        }
        return ResponseEntity.ok(chatGatewayService.complete(request));
    }

    private OpenAiChatRequest parseRequest(String rawRequest) {
        OpenAiChatRequest request = tryParse(rawRequest);
        if (request == null) {
            request = tryParseNestedString(rawRequest);
        }
        if (request == null && rawRequest.contains("\\\"")) {
            request = tryParse(rawRequest.replace("\\\"", "\""));
        }
        if (request == null) {
            throw new IllegalArgumentException("Invalid chat request body");
        }

        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(message.isBlank() ? "Invalid chat request body" : message);
        }
        return request;
    }

    private OpenAiChatRequest tryParse(String rawRequest) {
        try {
            return objectMapper.readValue(rawRequest, OpenAiChatRequest.class);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private OpenAiChatRequest tryParseNestedString(String rawRequest) {
        try {
            String nested = objectMapper.readValue(rawRequest, String.class);
            return objectMapper.readValue(nested, OpenAiChatRequest.class);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }
}
