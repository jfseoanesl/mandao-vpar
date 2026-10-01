# 010 · Panel de operación y KPIs del piloto

## KPIs (objetivos de `Mandao.md`, piloto de 30 días)
| KPI | Definición | Objetivo |
|---|---|---|
| Mandados/día | mandados creados en el rango ÷ días del rango | 15 |
| Ticket medio | promedio de `totalPrice` de mandados completados | $12.000 |
| Tiempo de asignación | promedio de `assignedAt − createdAt` (min) | < 5 |
| Servicios completados | completados ÷ (completados + fallidos + cancelados tras asignar) | > 95 % |
| Calificación | promedio de estrellas | > 4,6 |
| Recompra | clientes con ≥ 2 mandados ÷ clientes con ≥ 1 | > 30 % |

Además: GMV, ingreso de plataforma, embudo (`quote_viewed` → `errand_requested` → `errand_completed`), serie diaria, desglose por categoría y por segmento (persona / empresa), canal de adquisición (`utm_source`).

## Criterios de aceptación
- **AC-010-01** `GET /api/admin/kpis?from&to` devuelve cada KPI con su valor y su objetivo.
- **AC-010-02** El admin lista mandados filtrando por estado y ve los que requieren atención primero.
- **AC-010-03** El admin puede cancelar un mandado o ajustar su precio (queda en auditoría).
- **AC-010-04** El admin exporta los mandados a CSV (sin datos de documentos de identidad).
- **AC-010-05** El admin edita la tarifa vigente; se crea una nueva versión y las anteriores se conservan.
- **AC-010-06** Mapa en vivo con mandaderos disponibles y mandados activos.
