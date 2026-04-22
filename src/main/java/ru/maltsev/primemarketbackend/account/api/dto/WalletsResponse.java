package ru.maltsev.primemarketbackend.account.api.dto;

import java.util.List;

public record WalletsResponse(List<WalletItemResponse> items) {
}
