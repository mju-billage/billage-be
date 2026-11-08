package com.billage.helpcenter.dto;

import com.billage.helpcenter.TermType;
import com.billage.helpcenter.Terms;

public record TermsResponse(TermType termType, String body) {

	public static TermsResponse from(Terms terms) {
		return new TermsResponse(terms.getTermType(), terms.getBody());
	}
}
