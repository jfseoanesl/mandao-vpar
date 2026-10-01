package com.mandao.orders;

public enum ErrandCategory {
    ENVIO, COMPRA, DILIGENCIA, BUSCAR_COMPRAR, RECOGER_ENTREGAR, EMPRESARIAL, OTRO;

    /** AC-002-06: solo compras admiten bolsa. */
    public boolean allowsPurchaseBudget() {
        return this == COMPRA || this == BUSCAR_COMPRAR;
    }
}
