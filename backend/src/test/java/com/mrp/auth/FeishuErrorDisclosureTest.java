package com.mrp.auth;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrp.auth.controller.AuthController;
import com.mrp.auth.service.FeishuService;
import com.mrp.auth.service.JwtService;
import com.mrp.auth.service.UserService;
import com.mrp.common.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FeishuErrorDisclosureTest {

    private static final String SENSITIVE = "sensitive-upstream-token-marker";

    @Test
    void upstreamFailureDoesNotExposeResponseInLogsOrErrors() {
        var service = new FeishuService("app", "secret", "redirect", mock(JdbcTemplate.class),
                mock(PasswordEncoder.class), mock(JwtService.class), new ObjectMapper());
        var restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"error\":\"" + SENSITIVE + "\"}"));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"error\":\"" + SENSITIVE + "\"}"));

        var logger = (Logger) LoggerFactory.getLogger(FeishuService.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertFalse(assertThrows(RuntimeException.class,
                    () -> service.exchangeCodeForToken("code")).getMessage().contains(SENSITIVE));
            assertFalse(assertThrows(RuntimeException.class,
                    () -> service.getUserInfo("token")).getMessage().contains(SENSITIVE));
            assertTrue(appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .noneMatch(message -> message.contains(SENSITIVE)));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void callbackDoesNotReturnInternalFailureDetails() {
        var feishu = mock(FeishuService.class);
        when(feishu.exchangeCodeForToken("code")).thenThrow(new RuntimeException(SENSITIVE));
        var controller = new AuthController(mock(JdbcTemplate.class), mock(PasswordEncoder.class),
                mock(JwtService.class), mock(UserService.class), feishu);

        var response = controller.handleFeishuCallback(Map.of("code", "code"));
        assertFalse(response.success());
        assertEquals("FEISHU_AUTH_ERROR", response.error().code());
        assertFalse(response.error().message().contains(SENSITIVE));
    }

    @Test
    void passwordChangeFailureDoesNotExposeDatabaseDetails() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), anyLong())).thenThrow(new RuntimeException(SENSITIVE));
        var controller = new AuthController(jdbc, mock(PasswordEncoder.class), mock(JwtService.class),
                mock(UserService.class), mock(FeishuService.class));
        var logger = (Logger) LoggerFactory.getLogger(AuthController.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentUser.UserPrincipal(1L, "tester"), null));
        try {
            var response = controller.changePassword(Map.of("password", "ChangedPassword123!"));
            assertFalse(response.success());
            assertEquals("MRP_ERROR", response.error().code());
            assertFalse(response.error().message().contains(SENSITIVE));
            assertTrue(appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .noneMatch(message -> message.contains(SENSITIVE)));
            assertTrue(appender.list.stream().allMatch(event -> event.getThrowableProxy() == null));
        } finally {
            SecurityContextHolder.clearContext();
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
