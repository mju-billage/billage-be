package com.billage.auth;

import com.billage.common.exception.BusinessException;
import com.billage.common.exception.ErrorCode;

/**
 * 회전된 Refresh Token의 재사용이 감지됐을 때 던진다.
 * <p>
 * 재사용 감지 시 같은 familyId의 활성 토큰을 모두 폐기해야 하는데,
 * 이 폐기는 예외로 트랜잭션이 롤백되면 사라지므로 {@code noRollbackFor}로 커밋을 보장한다.
 * (재발급 트랜잭션이 이미 비관적 락을 쥐고 있어 REQUIRES_NEW는 자기 자신과 교착되므로 사용 불가.)
 */
public class RefreshTokenReuseException extends BusinessException {

	public RefreshTokenReuseException() {
		super(ErrorCode.REFRESH_TOKEN_REUSED);
	}
}
