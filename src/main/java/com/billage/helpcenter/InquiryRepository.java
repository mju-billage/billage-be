package com.billage.helpcenter;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

	long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime after);
}
