package com.pcbqms.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@ExtendWith(MockitoExtension.class)
class QmsBootstrapConfigurationTest {
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void doesNothingWhenBootstrapIsNotConfigured() throws Exception {
        ApplicationRunner runner = runner(new QmsBootstrapProperties());

        runner.run(null);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createsOnlyMissingTenantAndAdministrator() throws Exception {
        QmsBootstrapProperties properties = new QmsBootstrapProperties();
        properties.setTenantCode("customer-a");
        properties.setTenantName("客户 A");
        properties.setAdminUserName("admin-subject");
        ApplicationRunner runner = runner(properties);

        runner.run(null);

        verify(jdbcTemplate, times(2)).update(anyString(), any(Object[].class));
    }

    private ApplicationRunner runner(QmsBootstrapProperties properties) {
        return new QmsBootstrapConfiguration(properties, new LocalPasswordAuthenticationProperties(), passwordEncoder)
            .qmsBootstrapInitializer(jdbcTemplate, new TestTransactionManager());
    }

    private static class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // No database resource is required; JdbcTemplate is mocked in this unit test.
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            // No-op.
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            // No-op.
        }
    }
}
