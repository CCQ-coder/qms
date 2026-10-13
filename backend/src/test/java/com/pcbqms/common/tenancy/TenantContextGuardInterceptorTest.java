package com.pcbqms.common.tenancy;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;

import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextGuardInterceptorTest {
    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void rejectsDatabaseWorkOutsideTenantScope() throws Exception {
        Invocation invocation = mock(Invocation.class);

        assertThatIllegalStateException().isThrownBy(() -> new TenantContextGuardInterceptor().intercept(invocation))
            .withMessage("未建立租户上下文");
    }
}
