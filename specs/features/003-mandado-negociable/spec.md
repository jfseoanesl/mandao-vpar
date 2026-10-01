# 003 · Mandado negociable

Para tareas difíciles de calcular ("encuentra una bomba de gasolina para Duster 2018, si cuesta menos de $350.000 cómprala").

## Historias
- **H1** Como cliente quiero publicar un mandado sin precio fijo y recibir ofertas de mandaderos.
- **H2** Como mandadero quiero ofertar lo que cobraría por hacer el trabajo.
- **H3** Como cliente quiero aceptar, rechazar o contraofertar.

## Criterios de aceptación
- **AC-003-01** Un mandado con `pricingMode = NEGOTIABLE` se crea en estado `NEGOTIATING` con la cotización automática como **precio sugerido**.
- **AC-003-02** Un mandadero aprobado puede tener como máximo una oferta `PENDING` por mandado; monto entre $3.000 y $500.000.
- **AC-003-03** El cliente dueño ve las ofertas con nombre del mandadero, calificación y monto.
- **AC-003-04** Aceptar una oferta asigna el mandado a ese mandadero (`ASSIGNED`), fija `totalPrice = monto` y rechaza las demás ofertas.
- **AC-003-05** Contraofertar cambia la oferta a `COUNTERED` con `counterAmount`; el mandadero puede aceptar la contraoferta y el mandado queda `ASSIGNED` con ese precio.
- **AC-003-06** El reparto plataforma/mandadero de un precio negociado usa la misma comisión vigente.
