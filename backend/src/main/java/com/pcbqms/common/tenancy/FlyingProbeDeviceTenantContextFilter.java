package com.pcbqms.common.tenancy;

import com.pcbqms.equipment.application.EquipmentGatewayMappingApplicationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

/** Device credentials establish their own tenant scope, independently of user-supplied headers. */
public class FlyingProbeDeviceTenantContextFilter extends OncePerRequestFilter {
    public static final String IDENTITY_ATTRIBUTE = "flyingProbeDeviceIdentity";
    private static final int MAX_REQUEST_BYTES = 11_500_000;
    private final JdbcTemplate jdbc;

    public FlyingProbeDeviceTenantContextFilter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static boolean isDeviceRequest(HttpServletRequest request) {
        return request.getMethod().equals("POST") && (
            request.getRequestURI().equals("/api/v1/flying-probe/device-files")
            || request.getRequestURI().equals("/api/v1/flying-probe/device-host-status"));
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isDeviceRequest(request);
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        String token = request.getHeader("X-Device-Token");
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            response.sendError(401, "设备上传认证失败");
            return;
        }
        var identities = jdbc.query("""
            SELECT mapping.tenant_code, mapping.machine_name
            FROM qms_equipment_gateway_mapping mapping
            JOIN qms_tenant tenant ON tenant.code = mapping.tenant_code AND tenant.status = 'ACTIVE'
            JOIN qms_equipment equipment ON equipment.id = mapping.equipment_id
                AND equipment.tenant_code = mapping.tenant_code
            WHERE mapping.upload_token_hash = ? AND mapping.machine_name IS NOT NULL
            """, (row, index) -> new DeviceIdentity(row.getString("tenant_code"), row.getString("machine_name")),
            EquipmentGatewayMappingApplicationService.hashUploadToken(token));
        if (identities.size() != 1) {
            response.sendError(401, "设备上传认证失败");
            return;
        }
        if (request.getContentLengthLong() > MAX_REQUEST_BYTES) {
            response.sendError(413, "设备上传请求过大");
            return;
        }
        byte[] body = request.getInputStream().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) {
            response.sendError(413, "设备上传请求过大");
            return;
        }
        DeviceIdentity identity = identities.getFirst();
        request.setAttribute(IDENTITY_ATTRIBUTE, identity);
        TenantContext.set(identity.tenantCode(), "flying-probe.device");
        try {
            chain.doFilter(new HttpServletRequestWrapper(request) {
                @Override public ServletInputStream getInputStream() {
                    ByteArrayInputStream input = new ByteArrayInputStream(body);
                    return new ServletInputStream() {
                        @Override public int read() { return input.read(); }
                        @Override public boolean isFinished() { return input.available() == 0; }
                        @Override public boolean isReady() { return true; }
                        @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                    };
                }
            }, response);
        } finally {
            TenantContext.clear();
        }
    }

    public record DeviceIdentity(String tenantCode, String machineName) {
        public void requireDevice(String deviceId) {
            if (!machineName.equals(EquipmentGatewayMappingApplicationService.normalizeIdentifier(deviceId))) {
                throw new org.springframework.security.access.AccessDeniedException("设备编号与上传令牌不匹配");
            }
        }
    }
}
