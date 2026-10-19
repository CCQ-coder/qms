package com.pcbqms.integration.btfhessian;

import com.caucho.hessian.io.AbstractHessianInput;
import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import com.caucho.hessian.io.HessianInput;
import com.caucho.hessian.io.HessianOutput;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Minimal, allow-listed Hessian endpoint retained solely for WorkingMonitor.exe compatibility. */
public class BtfHessianMonitorServlet extends HttpServlet {
    static final int MAX_REQUEST_BYTES = 1024 * 1024;
    private static final long CLIENT_FAILURE_LOG_INTERVAL_MILLIS = 60_000;
    private static final Logger log = LoggerFactory.getLogger(BtfHessianMonitorServlet.class);

    private final BtfHessianProperties properties;
    private final BtfHessianMonitorApplicationService applicationService;
    private final AtomicLong lastClientFailureLogMillis = new AtomicLong();

    public BtfHessianMonitorServlet(BtfHessianProperties properties,
                                    BtfHessianMonitorApplicationService applicationService) {
        this.properties = properties;
        this.applicationService = applicationService;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HessianVersion version = HessianVersion.ONE;
        String remoteAddress = request.getRemoteAddr();
        try {
            byte[] body = readBody(request);
            version = HessianVersion.from(body);
            if (!properties.permits(remoteAddress)) {
                throw new RejectedRequestException("来源 IP 未在白名单内");
            }
            Invocation invocation = parseInvocation(body, version);
            if (!"Monitor".equals(invocation.method())) {
                throw new RejectedRequestException("不支持的 Hessian 方法：" + invocation.method());
            }
            applicationService.receive(invocation.payload());
            writeSuccess(response, version);
            log.info("Accepted BTF legacy monitor snapshot from {}", remoteAddress);
        } catch (RejectedRequestException exception) {
            logClientFailure("request", remoteAddress, exception.getMessage());
            writeFault(response, version, "请求已拒绝");
        } catch (IllegalArgumentException exception) {
            logClientFailure("payload", remoteAddress, exception.getMessage());
            writeFault(response, version, "设备数据处理失败");
        } catch (Exception exception) {
            log.error("Failed BTF Hessian monitor request from {}", remoteAddress, exception);
            writeFault(response, version, "设备数据处理失败");
        }
    }

    private byte[] readBody(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > MAX_REQUEST_BYTES) {
            throw new RejectedRequestException("请求体超过允许大小");
        }
        try (InputStream input = request.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_REQUEST_BYTES) {
                    throw new RejectedRequestException("请求体超过允许大小");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private Invocation parseInvocation(byte[] body, HessianVersion version) throws IOException {
        AbstractHessianInput input = version == HessianVersion.TWO
            ? new Hessian2Input(version.requestStream(body))
            : new HessianInput(version.requestStream(body));
        input.startCall();
        String method = input.getMethod();
        int argumentLength = input.readMethodArgLength();
        if (argumentLength != -1 && argumentLength != 1) {
            throw new RejectedRequestException("Monitor 必须且只能包含一个参数");
        }
        Object value = input.readObject(String.class);
        input.completeCall();
        if (!(value instanceof String payload)) {
            throw new RejectedRequestException("Monitor 参数必须是字符串");
        }
        return new Invocation(method, payload);
    }

    private void writeSuccess(HttpServletResponse response, HessianVersion version) throws IOException {
        prepareResponse(response);
        if (version == HessianVersion.TWO) {
            Hessian2Output output = new Hessian2Output(response.getOutputStream());
            output.startReply();
            output.writeString("OK");
            output.completeReply();
            output.flush();
        } else {
            HessianOutput output = new HessianOutput(response.getOutputStream());
            output.startReply();
            output.writeString("OK");
            output.completeReply();
            output.flush();
        }
    }

    private void writeFault(HttpServletResponse response, HessianVersion version, String message) throws IOException {
        prepareResponse(response);
        if (version == HessianVersion.TWO) {
            Hessian2Output output = new Hessian2Output(response.getOutputStream());
            output.writeFault("ServiceException", message, null);
            output.flush();
        } else {
            HessianOutput output = new HessianOutput(response.getOutputStream());
            output.writeFault("ServiceException", message, null);
            output.flush();
        }
    }

    private void prepareResponse(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/x-hessian");
    }

    private void logClientFailure(String category, String remoteAddress, String message) {
        long now = System.currentTimeMillis();
        long last = lastClientFailureLogMillis.get();
        if (now - last < CLIENT_FAILURE_LOG_INTERVAL_MILLIS
            || !lastClientFailureLogMillis.compareAndSet(last, now)) {
            return;
        }
        log.warn("Rejected BTF Hessian monitor {} from {}: {}", category, remoteAddress, message);
    }

    private record Invocation(String method, String payload) {
    }

    private enum HessianVersion {
        ONE,
        TWO;

        private static HessianVersion from(byte[] body) {
            return body.length > 0 && (body[0] == 'C' || body[0] == 'H') ? TWO : ONE;
        }

        private ByteArrayInputStream requestStream(byte[] body) throws RejectedRequestException {
            if (this == ONE) {
                return new ByteArrayInputStream(body);
            }
            if (body.length >= 4 && body[0] == 'H' && body[1] == 2 && body[2] == 0) {
                return new ByteArrayInputStream(body, 3, body.length - 3);
            }
            if (body.length > 0 && body[0] == 'C') {
                return new ByteArrayInputStream(body);
            }
            throw new RejectedRequestException("无效的 Hessian 2 请求头");
        }
    }

    private static class RejectedRequestException extends IOException {
        private RejectedRequestException(String message) {
            super(message);
        }
    }
}
