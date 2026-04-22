package ru.maltsev.primemarketbackend.security;

public final class PermissionCodes {
    public static final String ORDERS_VIEW_ANY = "ORDERS_VIEW_ANY";
    public static final String ORDER_DISPUTES_VIEW = "ORDER_DISPUTES_VIEW";
    public static final String ORDER_DISPUTES_CREATE = "ORDER_DISPUTES_CREATE";
    public static final String ORDER_DISPUTES_TAKE = "ORDER_DISPUTES_TAKE";
    public static final String ORDER_DISPUTES_RESOLVE = "ORDER_DISPUTES_RESOLVE";
    public static final String ORDER_CHATS_VIEW_ANY = "ORDER_CHATS_VIEW_ANY";
    public static final String ORDER_CHATS_SEND_AS_SUPPORT = "ORDER_CHATS_SEND_AS_SUPPORT";

    private PermissionCodes() {
    }
}
