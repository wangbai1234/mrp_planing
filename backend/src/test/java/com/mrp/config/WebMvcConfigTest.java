package com.mrp.config;

import com.mrp.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WebMvcConfigTest {
    @Configuration @EnableWebMvc @Import({WebMvcConfig.class, GlobalExceptionHandler.class})
    static class TestConfig {}

    @Test void missingApiReturnsJsonWhileSpaRouteStillLoads() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(TestConfig.class); context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            mvc.perform(get("/api/v1/mrp/nonexistent"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.success").value(false));
            mvc.perform(get("/mrp/stock-policies"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("text/html"));
        }
    }
}
