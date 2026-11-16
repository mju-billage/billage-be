package com.billage.helpcenter;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FaqRepository extends JpaRepository<Faq, Long> {

	List<Faq> findAllByOrderBySortOrderAscIdAsc();
}
