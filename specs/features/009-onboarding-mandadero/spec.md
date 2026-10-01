# 009 · Onboarding y verificación de mandaderos

## Historias
- **H1** Como mandadero quiero subir mis documentos desde el celular.
- **H2** Como operador quiero revisar y aprobar/rechazar mandaderos antes de que tomen servicios.

## Documentos
`CEDULA_FRENTE`, `CEDULA_REVERSO`, `SELFIE` (obligatorios) · `SOAT`, `LICENCIA`, `TARJETA_PROPIEDAD` (obligatorios si el vehículo es `MOTO` o `CARRO`).
Datos: tipo de vehículo (`A_PIE`, `BICICLETA`, `MOTO`, `CARRO`), placa, cuenta de pago (Nequi/Daviplata/banco).

## Criterios de aceptación
- **AC-009-01** Un mandadero recién registrado queda `PENDING` y no puede ponerse disponible (409).
- **AC-009-02** El mandadero puede subir/reemplazar documentos mientras esté `PENDING` o `REJECTED`.
- **AC-009-03** El admin aprueba solo si están todos los documentos obligatorios (422 si faltan).
- **AC-009-04** El admin puede rechazar con motivo; el mandadero ve el motivo y puede corregir.
- **AC-009-05** Los documentos solo son descargables por un admin (P5).
- **AC-009-06** Toda aprobación/rechazo queda en `audit_log`.
