package com.billage.common.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

/**
 * Amazon SES 발송. 자격 증명·리전은 기본 체인에서 해석한다 — EC2 는 인스턴스 역할이라
 * 서버에 액세스 키를 두지 않는다(S3 저장소와 같은 방식).
 *
 * <p>샌드박스 상태에서는 검증된 주소로만 발송된다. 프로덕션 액세스 승인 전에는
 * 검증하지 않은 수신자에게 보내면 SES 가 거부한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "billage.mail.sender", havingValue = "SES")
public class SesMailSender implements MailSender {

	private final SesV2Client client;
	private final MailProperties properties;

	public SesMailSender(SesV2Client client, MailProperties properties) {
		this.client = client;
		this.properties = properties;
		if (properties.from() == null || properties.from().isBlank()) {
			throw new IllegalStateException("billage.mail.from 설정이 필요합니다.");
		}
	}

	@Override
	public void send(String to, String subject, String body) {
		try {
			client.sendEmail(SendEmailRequest.builder()
					.fromEmailAddress(properties.from())
					.destination(Destination.builder().toAddresses(to).build())
					.content(EmailContent.builder()
							.simple(Message.builder()
									.subject(Content.builder().data(subject).charset("UTF-8").build())
									.body(Body.builder()
											.text(Content.builder().data(body).charset("UTF-8").build())
											.build())
									.build())
							.build())
					.build());
		} catch (SesV2Exception e) {
			// 수신자에게 원인을 그대로 보여 줄 값이 아니다 — 로그로만 남기고 공통 오류로 바꾼다.
			log.error("SES 발송 실패. to={} reason={}", to, e.awsErrorDetails().errorMessage(), e);
			throw new BusinessException(ErrorCode.MAIL_SEND_FAILED);
		}
	}
}
