package com.billage.helpcenter;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(HelpCenterProperties.class)
public class HelpCenterConfig {
}
