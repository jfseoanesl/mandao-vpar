# 007 · Bolsa de compras y pagos

## Historias
- **H1** Como cliente quiero autorizar un monto máximo para compras y que me devuelvan lo que no se gaste.
- **H2** Como mandadero no quiero financiar las compras del cliente.
- **H3** Como operador quiero registrar pagos manuales (efectivo, Nequi, Daviplata, transferencia) y liquidar al mandadero.

## Diseño
`PaymentGateway` (interfaz) → `ManualPaymentGateway` (MVP). Wompi se conectará como otra implementación.
Libro contable por mandado (`ledger_entries`): `HOLD`, `PURCHASE`, `REFUND`, `COURIER_PAYOUT`, `PLATFORM_FEE`.

## Criterios de aceptación
- **AC-007-01** Al crear un mandado se crea un pago `PENDING` por `totalPrice + purchaseBudget` con el método elegido, y un asiento `HOLD` si hay bolsa.
- **AC-007-02** Cada evidencia `FACTURA` registra un asiento `PURCHASE`; la suma de compras no puede superar la bolsa (422).
- **AC-007-03** Al completar: `REFUND = bolsa − compras` (si > 0), `COURIER_PAYOUT` y `PLATFORM_FEE` según el reparto. Ejemplo: bolsa $80.000, factura $63.700 ⇒ devolución **$16.300**.
- **AC-007-04** El admin confirma el pago (`PENDING → CONFIRMED`), registrando quién y cuándo.
- **AC-007-05** El admin marca el pago al mandadero como realizado; el mandadero ve sus ganancias (pagadas y pendientes).
- **AC-007-06** Un mandado cancelado antes de `IN_PROGRESS` deja el pago en `VOIDED` y sin asientos de liquidación.
