package ru.maltsev.primemarketbackend.treasury.api.dto;

public record TreasuryTransferResponse(
    TreasuryTransactionResponse outgoing,
    TreasuryTransactionResponse incoming
) {
}
