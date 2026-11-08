package com.billage.helpcenter.dto;

import com.billage.helpcenter.Faq;

public record FaqResponse(Long faqId, String question, String answer) {

	public static FaqResponse from(Faq faq) {
		return new FaqResponse(faq.getId(), faq.getQuestion(), faq.getAnswer());
	}
}
