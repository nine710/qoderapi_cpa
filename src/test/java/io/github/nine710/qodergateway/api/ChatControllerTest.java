package io.github.nine710.qodergateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nine710.qodergateway.application.ChatGatewayService;
import io.github.nine710.qodergateway.support.model.OpenAiChatRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChatGatewayService chatGatewayService;

    @Test
    void returnsNonStreamCompletion() throws Exception {
        var response = objectMapper.createObjectNode();
        response.put("id", "chatcmpl-1");
        response.put("object", "chat.completion");
        response.put("created", 1);
        response.put("model", "lite");
        var choices = objectMapper.createArrayNode();
        var choice = objectMapper.createObjectNode();
        choice.put("index", 0);
        var message = objectMapper.createObjectNode();
        message.put("role", "assistant");
        message.put("content", "hi");
        choice.set("message", message);
        choice.put("finish_reason", "stop");
        choices.add(choice);
        response.set("choices", choices);
        response.set("usage", objectMapper.createObjectNode().put("prompt_tokens", 0).put("completion_tokens", 0).put("total_tokens", 0));

        when(chatGatewayService.complete(any(OpenAiChatRequest.class))).thenReturn(response);

        var request = objectMapper.createObjectNode();
        request.put("model", "lite");
        request.put("stream", false);
        var messages = objectMapper.createArrayNode();
        messages.add(objectMapper.createObjectNode().put("role", "user").put("content", "hello"));
        request.set("messages", messages);

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object").value("chat.completion"))
                .andExpect(jsonPath("$.choices[0].message.content").value("hi"));
    }

    @Test
    void returnsStreamEmitterDirectly() throws Exception {
        var emitter = new SseEmitter(0L);
        when(chatGatewayService.stream(any(OpenAiChatRequest.class))).thenReturn(emitter);

        var request = objectMapper.createObjectNode();
        request.put("model", "lite");
        request.put("stream", true);
        var messages = objectMapper.createArrayNode();
        messages.add(objectMapper.createObjectNode().put("role", "user").put("content", "hello"));
        request.set("messages", messages);

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    @Test
    void acceptsDoubleEncodedJsonBody() throws Exception {
        var response = objectMapper.createObjectNode();
        response.put("id", "chatcmpl-1");
        response.put("object", "chat.completion");
        response.put("created", 1);
        response.put("model", "lite");
        var choices = objectMapper.createArrayNode();
        var choice = objectMapper.createObjectNode();
        choice.put("index", 0);
        var message = objectMapper.createObjectNode();
        message.put("role", "assistant");
        message.put("content", "hi");
        choice.set("message", message);
        choice.put("finish_reason", "stop");
        choices.add(choice);
        response.set("choices", choices);
        response.set("usage", objectMapper.createObjectNode().put("prompt_tokens", 0).put("completion_tokens", 0).put("total_tokens", 0));

        when(chatGatewayService.complete(any(OpenAiChatRequest.class))).thenReturn(response);

        var request = objectMapper.createObjectNode();
        request.put("model", "lite");
        request.put("stream", false);
        var messages = objectMapper.createArrayNode();
        messages.add(objectMapper.createObjectNode().put("role", "user").put("content", "hello"));
        request.set("messages", messages);
        String escapedBody = objectMapper.writeValueAsString(request.toString());

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(escapedBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object").value("chat.completion"))
                .andExpect(jsonPath("$.choices[0].message.content").value("hi"));
    }
}
