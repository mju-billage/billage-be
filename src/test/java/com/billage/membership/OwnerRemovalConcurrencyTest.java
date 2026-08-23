package com.billage.membership;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.billage.group.GroupService;
import com.billage.group.dto.GroupCreateRequest;
import com.billage.membership.dto.RoleUpdateRequest;
import com.billage.support.IntegrationTest;
import com.billage.user.User;
import com.billage.user.UserRepository;

/**
 * 공동 총무 둘이 동시에 서로를 내보내도 총무가 0명이 되면 안 된다.
 * 잠금이 없으면 양쪽 다 "총무 2명"을 읽고 커밋해 복구 불가능한 모임이 남는다.
 */
class OwnerRemovalConcurrencyTest extends IntegrationTest {

	@Autowired
	GroupService groupService;
	@Autowired
	GroupMembershipService groupMembershipService;
	@Autowired
	GroupMembershipRepository groupMembershipRepository;
	@Autowired
	UserRepository userRepository;

	@Test
	void 공동_총무가_서로를_동시에_내보내도_총무는_남는다() throws Exception {
		Long firstId = userRepository.save(User.create("first@example.com", "encoded", "총무1")).getId();
		Long secondId = userRepository.save(User.create("second@example.com", "encoded", "총무2")).getId();
		Long groupId = groupService.create(firstId, new GroupCreateRequest("주리랑", null)).groupId();
		String code = groupMembershipService.createInvitation(groupId, firstId).invitationCode();
		groupMembershipService.join(secondId, code);
		Long secondMembershipId = membershipId(groupId, secondId);
		groupMembershipService.changeRole(groupId, firstId, secondMembershipId, new RoleUpdateRequest("OWNER"));
		Long firstMembershipId = membershipId(groupId, firstId);

		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger removed = new AtomicInteger();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			pool.submit(() -> attemptRemoval(start, removed, groupId, firstId, secondMembershipId));
			pool.submit(() -> attemptRemoval(start, removed, groupId, secondId, firstMembershipId));
			start.countDown();
			pool.shutdown();
			assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
		} finally {
			pool.shutdownNow();
		}

		// 한쪽만 성공해야 하고, 어떤 경우에도 총무가 사라지면 안 된다.
		assertThat(removed.get()).isEqualTo(1);
		assertThat(groupMembershipRepository.countByGroupIdAndRole(groupId, GroupRole.OWNER)).isEqualTo(1);
	}

	private void attemptRemoval(CountDownLatch start, AtomicInteger removed, Long groupId, Long actorId,
			Long targetMembershipId) {
		try {
			start.await();
			groupMembershipService.removeMembership(groupId, actorId, targetMembershipId);
			removed.incrementAndGet();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (RuntimeException e) {
			// 뒤늦게 들어온 쪽은 LAST_OWNER_REQUIRED 로 막히거나, 대상이 이미 사라져 실패한다. 둘 다 정상이다.
		}
	}

	private Long membershipId(Long groupId, Long userId) {
		return groupMembershipRepository.findByGroupIdAndUserId(groupId, userId).orElseThrow().getId();
	}
}
