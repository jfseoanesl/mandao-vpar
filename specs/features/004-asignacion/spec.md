# 004 · Asignación de mandaderos

## Historias
- **H1** Como cliente quiero que un mandadero cercano tome mi mandado en menos de 5 minutos.
- **H2** Como mandadero quiero ver los mandados disponibles cerca de mí y aceptarlos.
- **H3** Como operador quiero asignar manualmente cuando nadie acepta.

## Reglas
- Candidatos: mandaderos `APPROVED`, `available = true`, último ping hace < 2 min, sin mandado activo.
- Radio incremental desde la primera parada: 2 km (0–60 s), 4 km (60–120 s), 8 km (> 120 s). Consulta con PostGIS (`ST_DWithin`).
- Cada 10 s el despachador notifica por WebSocket (`/user/queue/offers`) a los 5 candidatos más cercanos.
- A los 5 min sin asignar, el mandado se marca `needsAttention` y se alerta al panel admin.

## Criterios de aceptación
- **AC-004-01** Un mandadero ve en `GET /api/courier/offers` los mandados `SEARCHING` y `NEGOTIATING` a ≤ 8 km.
- **AC-004-02** Aceptar un mandado `SEARCHING` lo pasa a `ASSIGNED`, registra `assignedAt` y el evento `errand_assigned`.
- **AC-004-03** Si dos mandaderos aceptan a la vez, solo uno gana; el otro recibe 409.
- **AC-004-04** Un mandadero con un mandado activo no puede aceptar otro (409).
- **AC-004-05** Un mandadero no aprobado recibe 403.
- **AC-004-06** El admin puede asignar a cualquier mandadero aprobado un mandado `SEARCHING`/`NEGOTIATING`.
- **AC-004-07** A los 5 minutos sin asignación el mandado queda con `needsAttention = true`.
