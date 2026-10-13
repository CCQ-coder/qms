package com.pcbqms.common.tenancy;

import java.sql.Connection;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.stereotype.Component;

/**
 * MySQL has no native row-level tenant isolation. The application always
 * scopes repository queries by tenant_code; this guard prevents a MyBatis
 * statement from executing outside the request or scheduled-job tenant scope.
 */
@Component
@Intercepts({
    @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class TenantContextGuardInterceptor implements Interceptor {
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        TenantContext.tenantCode();
        return invocation.proceed();
    }
}
