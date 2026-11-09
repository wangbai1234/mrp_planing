package com.mrp.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import java.util.Map;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new StatusController())
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void preservesForbiddenStatusAndApiErrorBody() throws Exception {
        mvc.perform(get("/forbidden")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("无权查看该工厂的排产结果"));
    }

    @Test
    void preservesNotFoundStatusAndApiErrorBody() throws Exception {
        mvc.perform(get("/missing")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MRP_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("排产版本不存在"));
    }

    @RestController
    static class StatusController {
        @GetMapping("/forbidden")
        void forbidden() { throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看该工厂的排产结果"); }
        @GetMapping("/missing")
        void missing() { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "排产版本不存在"); }
        @GetMapping("/stale")
        void stale() { throw new ConcurrentChangeException("日历已变化，请刷新后重试"); }
        @GetMapping("/fractional-machine")
        void fractionalMachine() { throw new IllegalArgumentException("整机转移数量必须为正整数台"); }
        @GetMapping("/query")
        String query(@RequestParam int page) { return Integer.toString(page); }
        @PostMapping("/json")
        Map<String, Object> json(@RequestBody Map<String, Object> body) { return body; }
    }

    @Test
    void staleWriteReturnsConflictAndRefreshMessage() throws Exception {
        mvc.perform(get("/stale")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MRP_CONCURRENT_CHANGE"))
                .andExpect(jsonPath("$.error.message").value("日历已变化，请刷新后重试"));
    }

    @Test
    void invalidMachineQuantityReturnsActionableBadRequest() throws Exception {
        mvc.perform(get("/fractional-machine")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MRP_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("整机转移数量必须为正整数台"));
    }

    @Test void malformedClientRequestsAreNotServerErrors() throws Exception {
        mvc.perform(get("/query")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MRP_VALIDATION_ERROR"));
        mvc.perform(get("/query").param("page", "invalid")).andExpect(status().isBadRequest());
        mvc.perform(post("/json").contentType("application/json").content("{broken"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/json").contentType("application/json"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/query")).andExpect(status().isMethodNotAllowed());
    }
}
