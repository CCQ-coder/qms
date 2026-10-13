package com.pcbqms.integration.btfhessian;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BtfHessianProperties.class)
public class BtfHessianConfiguration {
    @Bean
    ServletRegistrationBean<BtfHessianMonitorServlet> btfHessianMonitorServlet(
        BtfHessianProperties properties,
        BtfHessianMonitorApplicationService applicationService
    ) {
        ServletRegistrationBean<BtfHessianMonitorServlet> registration = new ServletRegistrationBean<>(
            new BtfHessianMonitorServlet(properties, applicationService), "/hessian/monitor"
        );
        registration.setName("btfHessianMonitorServlet");
        return registration;
    }
}
