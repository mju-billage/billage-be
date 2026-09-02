package com.billage.membership;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.billage.group.GroupService;
import com.billage.group.dto.GroupCreateRequest;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

/**
 * 초대 코드 발급은 멱등해야 한다 — 화면에 발급 버튼이 없어 진입할 때마다 호출되므로
 * 두 요청이 실제로 겹칠 수 있다.
 *
 * <p>모임 행 잠금만으로는 부족하다. MySQL 기본 격리 수준에서 잠금 뒤의 일반 읽기는 트랜잭션
 * 스냅샷을 보기 때문에, 먼저 커밋한 코드를 못 보고 하나를 더 만든다.
 */
class InvitationIssueConcurrencyTest extends IntegrationTest {

	@Autowired
	GroupService groupService;
	@Autowired
	GroupMembershipService groupMembershipService;
	@Autowired
	GroupInvitationRepository groupInvitationRepository;
	@Autowired
	UserRepository userRepository;

	@Test
	void 동시에_발급을_요청해도_코드는_하나만_만들어진다() throws Exception {
		Long ownerId = userRepository.save(User.create("owner@example.com", "encoded", "총무")).getId();
		Long groupId = groupService.create(ownerId, new GroupCreateRequest("주리랑", null, null)).groupId();

		CountDownLatch start = new CountDownLatch(1);
		Set<String> codes = ConcurrentHashMap.newKeySet();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			pool.submit(() -> issue(start, codes, groupId, ownerId));
			pool.submit(() -> issue(start, codes, groupId, ownerId));
			start.countDown();
			pool.shutdown();
			assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
		} finally {
			pool.shutdownNow();
		}

		assertThat(groupInvitationRepository.count()).isEqualTo(1);
		assertThat(codes).hasSize(1);
	}

	private void issue(CountDownLatch start, Set<String> codes, Long groupId, Long ownerId) {
		try {
			start.await();
			codes.add(groupMembershipService.createInvitation(groupId, ownerId).invitationCode());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
