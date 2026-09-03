package com.billage.common.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.services.sesv2.SesV2Client;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

	/** 자격 증명·리전은 기본 체인에서 해석한다(EC2 인스턴스 역할). */
	@Bean
	@ConditionalOnProperty(name = "billage.mail.sender", havingValue = "SES")
	SesV2Client sesV2Client() {
		return SesV2Client.create();
	}
}
