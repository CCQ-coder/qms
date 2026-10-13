package com.pcbqms.common.tenancy;

public final class TenantContext {
    private static final ThreadLocal<String> TENANT_CODE = new ThreadLocal<>();
    private static final ThreadLocal<String> ACTOR = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(String tenantCode, String actor) {
        TENANT_CODE.set(tenantCode);
        ACTOR.set(actor);
    }

    public static String tenantCode() {
        String tenantCode = TENANT_CODE.get();
        if (tenantCode == null) {
            throw new IllegalStateException("未建立租户上下文");
        }
        return tenantCode;
    }

    public static String actor() {
        return ACTOR.get() == null ? "system" : ACTOR.get();
    }

    public static void clear() {
        TENANT_CODE.remove();
        ACTOR.remove();
    }
}

