package com.billage.auth.social;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SocialAuthProperties.class)
class SocialAuthConfig {
}
