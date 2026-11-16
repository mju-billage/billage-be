package com.billage.helpcenter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 고객지원 설정.
 *
 * @param inquiryEmail 문의가 들어오면 알림 메일을 받을 운영자 주소. 비어 있으면 저장만 한다.
 */
@ConfigurationProperties(prefix = "billage.help-center")
public record HelpCenterProperties(String inquiryEmail) {

	public boolean notifiesByMail() {
		return inquiryEmail != null && !inquiryEmail.isBlank();
	}
}
