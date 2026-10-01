package com.mandao.identity;

public enum Role {
    CLIENT, COURIER, ADMIN, COMPANY_ADMIN;

    /** Roles que pueden solicitar mandados. */
    public boolean canRequestErrands() {
        return this == CLIENT || this == COMPANY_ADMIN;
    }
}
