package com.pcbqms.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingFilterTest {
    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    void keepsProxyRequestIdAndLogsStatusWithoutQueryOrCredentials() throws Exception {
        String requestId = "0123456789abcdef0123456789abcdef";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/work-orders");
        request.addHeader("X-Request-ID", requestId);
        request.addHeader("Authorization", "Bearer secret-token");
        request.setQueryString("token=secret-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            filter.doFilter(request, response, (servletRequest, servletResponse) -> {
                assertThat(MDC.get("requestId")).isEqualTo(requestId);
                ((MockHttpServletResponse) servletResponse).setStatus(503);
            });

            assertThat(response.getHeader("X-Request-ID")).isEqualTo(requestId);
            assertThat(MDC.get("requestId")).isNull();
            assertThat(appender.list).anySatisfy(event ->
                assertThat(event.getMDCPropertyMap()).containsEntry("requestId", requestId));
            List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertThat(messages).anySatisfy(message -> {
                assertThat(message).contains("GET /api/v1/work-orders", "503", "ms");
                assertThat(message).doesNotContain("secret-token", "token=");
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void replacesInvalidRequestIdAndClearsContextAfterFailure() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/work-orders");
        request.addHeader("X-Request-ID", "untrusted\nvalue");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            assertThat(MDC.get("requestId")).matches("[0-9a-f]{32}");
            throw new IllegalStateException("database unavailable");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(response.getHeader("X-Request-ID")).matches("[0-9a-f]{32}");
        assertThat(response.getHeader("X-Request-ID")).isNotEqualTo("untrusted\nvalue");
        assertThat(MDC.get("requestId")).isNull();
    }
}
