package com.billage.common.mail;

/**
 * 메일 발송. 로컬·테스트는 로그로 남기는 {@link LogMailSender}, dev·prod 는 SES 를 쓰는
 * {@link SesMailSender} 이며 {@code billage.mail.sender} 설정으로 고른다.
 *
 * <p>파일 저장소({@code FileStorage})와 같은 모양이다 — 인프라 의존을 인터페이스 뒤에 두어
 * 로컬 개발이 AWS 없이 굴러가게 한다.
 */
public interface MailSender {

	/**
	 * 메일 한 통을 보낸다.
	 *
	 * @param to      받는 사람 주소
	 * @param subject 제목
	 * @param body    본문(평문)
	 */
	void send(String to, String subject, String body);
}
