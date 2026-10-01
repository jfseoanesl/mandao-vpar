# 002 · Solicitud y cotización de mandados

## Historias
- **H1** Como cliente quiero elegir qué necesito hacer hoy (6 categorías + "Otro mandado").
- **H2** Como cliente quiero describir el mandado con mis palabras, adjuntar fotos/documentos y marcar origen, paradas y destino en un mapa.
- **H3** Como cliente quiero conocer el precio **antes** de confirmar, con desglose claro.
- **H4** Como cliente quiero repetir un mandado anterior con un toque.
- **H5** Como cliente quiero programar un mandado para más tarde.

## Motor de precios
```
subtotal = base + km × tarifaKm + max(0, esperaEstimada − esperaIncluida) × tarifaMinuto
         + max(0, paradas − 2) × tarifaParadaExtra + recargoComplejidad
total    = redondeo_arriba_100(subtotal)
plataforma = redondeo_100(total × comision)      mandadero = total − plataforma
```
Tarifa inicial (versión 1): base 5.000 · km 900 · espera incluida 10 min · minuto 250 · parada extra 2.000 ·
complejidad SENCILLA 0 / MEDIA 2.500 / COMPLEJA 5.000 · comisión 22,5 %.
Distancia: suma de tramos por OSRM; si OSRM no responde en 3 s, haversine × 1,3.

## Criterios de aceptación
- **AC-002-01** Mandado de 6 km, 2 paradas, complejidad MEDIA, sin espera extra ⇒ total **$12.900** (ejemplo de `Mandao.md`).
- **AC-002-02** Cada parada adicional sobre 2 suma $2.000; cada minuto de espera sobre 10 suma $250.
- **AC-002-03** La cotización responde el desglose: `base`, `distance`, `waitCharge`, `extraStops`, `complexity`, `total`, `platformFee`, `courierPayout`, `distanceKm`, `tariffVersion`.
- **AC-002-04** Se exige al menos 1 parada; máximo 6. Coordenadas fuera del área de servicio (radio 30 km del centro de Valledupar) ⇒ 400.
- **AC-002-05** Al crear un mandado FIXED el servidor **recalcula** el precio (nunca confía en el precio enviado por el cliente) y el mandado queda en `SEARCHING`.
- **AC-002-06** Categorías `COMPRA` y `BUSCAR_COMPRAR` admiten `purchaseBudget` (bolsa); otras categorías lo rechazan.
- **AC-002-07** El mandado se crea con códigos de inicio y fin de 4 dígitos visibles solo para el cliente dueño.
- **AC-002-08** `repeatOf` apunta a un mandado propio; si no, 404. Se registra el evento `repeat_order`.
- **AC-002-09** Consultar una cotización registra el evento `quote_viewed`; crear un mandado registra `errand_requested`.
- **AC-002-10** Un mandado con `scheduledFor` futuro no se ofrece a mandaderos hasta 15 minutos antes.
- **AC-002-11** El cliente puede cancelar mientras el estado sea `SEARCHING`, `NEGOTIATING`, `ASSIGNED` o `GOING_TO_ORIGIN`.

## Estados
```
SEARCHING ─┬─> ASSIGNED ─> GOING_TO_ORIGIN ─> IN_PROGRESS ─> DELIVERED ─> COMPLETED ─> RATED
NEGOTIATING┘      (OTP inicio: GOING_TO_ORIGIN→IN_PROGRESS · OTP fin: IN_PROGRESS→DELIVERED)
Cualquier estado previo a IN_PROGRESS ─> CANCELLED ;  ASSIGNED..IN_PROGRESS ─> FAILED
```
