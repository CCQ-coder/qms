package com.pcbqms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** First-tenant settings used only to make a new isolated QMS deployment operable. */
@ConfigurationProperties(prefix = "qms.bootstrap")
public class QmsBootstrapProperties {
    private String tenantCode;
    private String tenantName;
    private String adminUserName;
    private String adminPassword;

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getAdminUserName() {
        return adminUserName;
    }

    public void setAdminUserName(String adminUserName) {
        this.adminUserName = adminUserName;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }
}
