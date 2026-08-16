package com.billage.ledger.dto;

import com.billage.ledger.Ledger;

public record LedgerUpdateResponse(
		Long ledgerId,
		Long folderId,
		String name
) {

	public static LedgerUpdateResponse from(Ledger ledger) {
		return new LedgerUpdateResponse(ledger.getId(), ledger.getFolderId(), ledger.getName());
	}
}
