package com.billage.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;

/**
 * SMTP 로 나가는 내용과 실패 처리를 확인한다. Gmail 대신 로컬 가짜 서버를 세운다 —
 * 계정 없이 돌아야 하고, 테스트가 실제 메일을 보내면 안 된다.
 */
class SmtpMailSenderTest {

	private static final String USERNAME = "billage.sender@gmail.com";

	private ServerSocket server;
	private boolean rejectLogin;
	private final List<String> received = new CopyOnWriteArrayList<>();

	@BeforeEach
	void startServer() throws IOException {
		server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
		Thread thread = new Thread(this::serve);
		thread.setDaemon(true);
		thread.start();
	}

	@AfterEach
	void stopServer() throws IOException {
		server.close();
	}

	@Test
	void 계정으로_로그인해_계정_주소를_발신자로_보낸다() {
		sender("abcd efgh ijkl mnop").send("user@example.com", "[빌리지] 인증 코드", "code-123456");

		// 앱 비밀번호는 공백을 뺀 값으로 로그인한다.
		assertThat(loginCredentials()).endsWith(USERNAME + "\0abcdefghijklmnop");
		assertThat(received).contains("MAIL FROM:<" + USERNAME + ">", "RCPT TO:<user@example.com>");
		assertThat(received).anyMatch(line -> line.startsWith("Subject: =?UTF-8?"));
		assertThat(received).contains("code-123456");
	}

	@Test
	void 로그인이_거부되면_공통_오류로_바꾼다() {
		rejectLogin = true;

		assertThatThrownBy(() -> sender("wrong").send("user@example.com", "제목", "본문"))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.MAIL_SEND_FAILED);
	}

	@Test
	void 계정이나_앱_비밀번호가_비면_시작을_실패시킨다() {
		assertThatThrownBy(() -> sender(" "))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("GMAIL_APP_PASSWORD");
		assertThatThrownBy(() -> new SmtpMailSender(properties(null, "pw")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("GMAIL_USERNAME");
	}

	private SmtpMailSender sender(String password) {
		return new SmtpMailSender(properties(USERNAME, password));
	}

	private MailProperties properties(String username, String password) {
		return new MailProperties(MailProperties.SenderType.SMTP, null, new MailProperties.Smtp(
				"127.0.0.1", server.getLocalPort(), username, password, false, Duration.ofSeconds(3)));
	}

	/** AUTH PLAIN 으로 넘어온 계정·비밀번호. 항목 사이는 NUL 로 구분된다. */
	private String loginCredentials() {
		String encoded = received.stream()
				.filter(line -> line.startsWith("AUTH PLAIN "))
				.findFirst().orElseThrow()
				.substring("AUTH PLAIN ".length());
		return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
	}

	/** 한 번의 접속만 받는 최소한의 SMTP 서버. 받은 줄을 그대로 모은다. */
	private void serve() {
		try (Socket socket = server.accept();
				BufferedReader in = new BufferedReader(
						new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
				PrintWriter out = new PrintWriter(socket.getOutputStream(), false, StandardCharsets.UTF_8)) {
			reply(out, "220 localhost ESMTP");
			boolean data = false;
			String line;
			while ((line = in.readLine()) != null) {
				received.add(line);
				if (data) {
					if (line.equals(".")) {
						data = false;
						reply(out, "250 OK");
					}
				} else if (line.startsWith("EHLO")) {
					reply(out, "250-localhost\r\n250 AUTH PLAIN");
				} else if (line.startsWith("AUTH")) {
					reply(out, rejectLogin ? "535 5.7.8 Username and Password not accepted" : "235 OK");
				} else if (line.equals("DATA")) {
					data = true;
					reply(out, "354 End data with <CR><LF>.<CR><LF>");
				} else if (line.equals("QUIT")) {
					reply(out, "221 Bye");
					return;
				} else {
					reply(out, "250 OK");
				}
			}
		} catch (IOException ignored) {
			// 테스트가 끝나 소켓이 닫힌 경우다.
		}
	}

	private static void reply(PrintWriter out, String message) {
		out.print(message + "\r\n");
		out.flush();
	}
}
