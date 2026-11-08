package com.billage.helpcenter;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TermsRepository extends JpaRepository<Terms, TermType> {
}
