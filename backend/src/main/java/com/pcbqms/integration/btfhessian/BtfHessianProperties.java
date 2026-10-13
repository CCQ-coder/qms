package com.pcbqms.integration.btfhessian;

import jakarta.annotation.PostConstruct;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** Security and tenant binding for the unauthenticated legacy monitor protocol. */
@ConfigurationProperties(prefix = "qms.integration.btf-hessian")
public class BtfHessianProperties {
    private String allowedIps = "";
    private String tenantCode;
    private List<InetAddress> allowedAddresses = List.of();

    public String getAllowedIps() {
        return allowedIps;
    }

    public void setAllowedIps(String allowedIps) {
        this.allowedIps = allowedIps;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    @PostConstruct
    public void validateAndInitialize() {
        if (!StringUtils.hasText(tenantCode)) {
            throw new IllegalStateException("必须配置 qms.integration.btf-hessian.tenant-code");
        }
        tenantCode = tenantCode.trim();
        if (!StringUtils.hasText(allowedIps)) {
            allowedAddresses = List.of();
            return;
        }
        String[] values = allowedIps.split(",", -1);
        List<InetAddress> parsed = new ArrayList<>();
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                throw new IllegalStateException("qms.integration.btf-hessian.allowed-ips 不能包含空白地址");
            }
            parsed.add(parseExactIp(value.trim(), "qms.integration.btf-hessian.allowed-ips"));
        }
        allowedAddresses = Collections.unmodifiableList(parsed);
    }

    public boolean permits(String remoteAddress) {
        if (allowedAddresses.isEmpty() || !StringUtils.hasText(remoteAddress)) {
            return false;
        }
        InetAddress remote;
        try {
            remote = parseExactIp(remoteAddress.trim(), "请求来源地址");
        } catch (IllegalArgumentException exception) {
            return false;
        }
        return allowedAddresses.stream().anyMatch(allowed -> Arrays.equals(allowed.getAddress(), remote.getAddress()));
    }

    private static InetAddress parseExactIp(String value, String propertyName) {
        try {
            if (value.indexOf(':') >= 0 && value.indexOf('%') < 0) {
                InetAddress parsed = InetAddress.getByName(value);
                if (parsed instanceof Inet6Address) {
                    return parsed;
                }
            }
            if (isStrictIpv4(value)) {
                String[] parts = value.split("\\.", -1);
                byte[] bytes = new byte[4];
                for (int index = 0; index < parts.length; index++) {
                    bytes[index] = (byte) Integer.parseInt(parts[index]);
                }
                InetAddress parsed = InetAddress.getByAddress(bytes);
                if (parsed instanceof Inet4Address) {
                    return parsed;
                }
            }
        } catch (UnknownHostException | NumberFormatException ignored) {
            // All accepted values are numerical addresses; host names must never be resolved.
        }
        throw new IllegalArgumentException(propertyName + " 必须是精确的 IPv4 或 IPv6 地址：" + value);
    }

    private static boolean isStrictIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) return false;
            try {
                if (Integer.parseInt(part) > 255) return false;
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return true;
    }
}
