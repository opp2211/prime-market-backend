package ru.maltsev.primemarketbackend.currency.api.dto;

import ru.maltsev.primemarketbackend.currency.domain.Currency;

public record CurrencyResponse(String code, String title) {
    public static CurrencyResponse from(Currency currency) {
        return new CurrencyResponse(currency.getCode(), currency.getTitle());
    }
}