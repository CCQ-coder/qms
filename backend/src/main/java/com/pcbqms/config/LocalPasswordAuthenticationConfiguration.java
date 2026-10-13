package com.pcbqms.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(LocalPasswordAuthenticationProperties.class)
public class LocalPasswordAuthenticationConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    @ConditionalOnProperty(prefix = "qms.security.local-password-login", name = "enabled", havingValue = "true")
    JwtEncoder localPasswordJwtEncoder(LocalPasswordAuthenticationProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecret(properties)));
    }

    @Bean
    @ConditionalOnProperty(prefix = "qms.security.local-password-login", name = "enabled", havingValue = "true")
    JwtDecoder localPasswordJwtDecoder(LocalPasswordAuthenticationProperties properties) {
        return NimbusJwtDecoder.withSecretKey(jwtSecret(properties)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private SecretKey jwtSecret(LocalPasswordAuthenticationProperties properties) {
        if (!StringUtils.hasText(properties.getJwtSecret())) {
            throw new IllegalStateException("启用本地账号登录必须设置 QMS_LOCAL_LOGIN_JWT_SECRET");
        }
        try {
            byte[] secret = Base64.getDecoder().decode(properties.getJwtSecret().trim());
            if (secret.length < 32) {
                throw new IllegalStateException("QMS_LOCAL_LOGIN_JWT_SECRET 解码后至少需要 32 字节");
            }
            return new SecretKeySpec(secret, "HmacSHA256");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("QMS_LOCAL_LOGIN_JWT_SECRET 必须是 Base64 编码的密钥", exception);
        }
    }
}
