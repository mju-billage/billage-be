package com.billage.file;

import java.nio.file.Path;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * 파일 업로드 정책. 허용 형식·용량은 기획 확정 전 제안값이며 설정으로 바꿀 수 있다.
 */
@ConfigurationProperties(prefix = "billage.file")
public record FileProperties(
		Path storagePath,
		@DefaultValue("10MB") DataSize maxSize,
		@DefaultValue({ "image/jpeg", "image/png", "image/webp" }) Set<String> allowedContentTypes
) {
}
