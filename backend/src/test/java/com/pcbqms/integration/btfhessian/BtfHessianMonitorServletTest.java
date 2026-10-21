package com.pcbqms.integration.btfhessian;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import com.caucho.hessian.io.HessianInput;
import com.caucho.hessian.io.HessianOutput;
import com.pcbqms.equipment.api.EquipmentTelemetryResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class BtfHessianMonitorServletTest {
    private static final String PAYLOAD = "[{\"type\":\"RealtimeInf\"}]";

    @Mock private BtfHessianMonitorApplicationService applicationService;

    private BtfHessianMonitorServlet servlet;

    @BeforeEach
    void setUp() {
        BtfHessianProperties properties = new BtfHessianProperties();
        properties.setTenantCode("btf-customer");
        properties.setAllowedIps("10.20.169.142,2001:db8::12");
        properties.validateAndInitialize();
        servlet = new BtfHessianMonitorServlet(properties, applicationService);
    }

    @Test
    void acceptsAWhitelistedHessianOneMonitorCall() throws Throwable {
        when(applicationService.receive(PAYLOAD)).thenReturn(new EquipmentTelemetryResponse(0, 0));

        MockHttpServletResponse response = invoke(hessianOneCall("Monitor", PAYLOAD), "10.20.169.142");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(decodeHessianOneReply(response.getContentAsByteArray())).isEqualTo("OK");
        verify(applicationService).receive(PAYLOAD);
    }

    @Test
    void acceptsAWhitelistedHessianTwoMonitorCall() throws Throwable {
        when(applicationService.receive(PAYLOAD)).thenReturn(new EquipmentTelemetryResponse(0, 0));

        MockHttpServletResponse response = invoke(hessianTwoCall("Monitor", PAYLOAD), "2001:db8::12");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(decodeHessianTwoReply(response.getContentAsByteArray())).isEqualTo("OK");
        verify(applicationService).receive(PAYLOAD);
    }

    @Test
    void returnsHessianFaultAndSkipsProcessingForDisallowedIp() throws Throwable {
        MockHttpServletResponse response = invoke(hessianOneCall("Monitor", PAYLOAD), "10.20.169.99");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8)).contains("请求已拒绝");
        verify(applicationService, never()).receive(PAYLOAD);
    }

    @Test
    void returnsHessianFaultForAnUnsupportedMethod() throws Throwable {
        MockHttpServletResponse response = invoke(hessianOneCall("Other", PAYLOAD), "10.20.169.142");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8)).contains("请求已拒绝");
        verify(applicationService, never()).receive(PAYLOAD);
    }

    @Test
    void acknowledgesOverlappingMonitorCallsWithoutStartingMoreDatabaseWork() throws Throwable {
        CountDownLatch firstCallStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstCall = new CountDownLatch(1);
        AtomicInteger invocationCount = new AtomicInteger();
        when(applicationService.receive(PAYLOAD)).thenAnswer(ignored -> {
            if (invocationCount.incrementAndGet() == 1) {
                firstCallStarted.countDown();
                assertThat(releaseFirstCall.await(2, TimeUnit.SECONDS)).isTrue();
            }
            return new EquipmentTelemetryResponse(0, 0);
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<MockHttpServletResponse> firstResponse = executor.submit(
                () -> invoke(hessianOneCall("Monitor", PAYLOAD), "10.20.169.142"));
            assertThat(firstCallStarted.await(1, TimeUnit.SECONDS)).isTrue();

            MockHttpServletResponse overlappingResponse =
                invoke(hessianOneCall("Monitor", PAYLOAD), "10.20.169.142");

            assertThat(overlappingResponse.getStatus()).isEqualTo(200);
            assertThat(decodeHessianOneReply(overlappingResponse.getContentAsByteArray())).isEqualTo("OK");
            verify(applicationService, times(1)).receive(PAYLOAD);

            releaseFirstCall.countDown();
            assertThat(decodeHessianOneReply(firstResponse.get(1, TimeUnit.SECONDS).getContentAsByteArray()))
                .isEqualTo("OK");
        } finally {
            releaseFirstCall.countDown();
            executor.shutdownNow();
        }
    }

    private MockHttpServletResponse invoke(byte[] content, String remoteAddress) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/hessian/monitor");
        request.setRemoteAddr(remoteAddress);
        request.setContentType("application/x-hessian");
        request.setContent(content);
        MockHttpServletResponse response = new MockHttpServletResponse();
        servlet.service(request, response);
        return response;
    }

    private byte[] hessianOneCall(String method, String payload) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        HessianOutput output = new HessianOutput(bytes);
        output.startCall(method, 1);
        output.writeString(payload);
        output.completeCall();
        output.flush();
        return bytes.toByteArray();
    }

    private byte[] hessianTwoCall(String method, String payload) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Hessian2Output output = new Hessian2Output(bytes);
        output.startCall(method, 1);
        output.writeString(payload);
        output.completeCall();
        output.flush();
        return bytes.toByteArray();
    }

    private String decodeHessianOneReply(byte[] response) throws Throwable {
        HessianInput input = new HessianInput(new ByteArrayInputStream(response));
        input.startReply();
        String result = input.readString();
        input.completeReply();
        return result;
    }

    private String decodeHessianTwoReply(byte[] response) throws Throwable {
        ByteArrayInputStream bytes = new ByteArrayInputStream(response);
        assertThat(bytes.read()).isEqualTo((int) 'H');
        assertThat(bytes.read()).isEqualTo(2);
        assertThat(bytes.read()).isZero();
        return (String) new Hessian2Input(bytes).readReply(String.class);
    }
}
