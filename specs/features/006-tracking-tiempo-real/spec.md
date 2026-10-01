# 006 · Tracking en tiempo real

## Historias
- **H1** Como cliente quiero ver en el mapa dónde va mi mandadero.
- **H2** Como operador quiero un mapa en vivo con todos los mandaderos disponibles y los mandados activos.

## Diseño
- WebSocket STOMP en `/ws`. El JWT se envía en el frame `CONNECT` (`Authorization: Bearer ...`).
- Tópicos: `/topic/errands/{id}` (estados, evidencias, ubicación), `/topic/admin` (ubicaciones y alertas), `/user/queue/offers` (mandadero).
- La PWA del mandadero usa `watchPosition` y envía `POST /api/courier/location` como máximo cada 10 s.

## Criterios de aceptación
- **AC-006-01** Un ping de ubicación de un mandadero en servicio se guarda en `location_pings` y se publica en `/topic/errands/{id}`.
- **AC-006-02** Un mandadero **no disponible y sin servicio** no puede enviar ubicación (409) — privacidad (P5).
- **AC-006-03** Solo el cliente dueño, el mandadero asignado o un admin pueden suscribirse a `/topic/errands/{id}`; `/topic/admin` solo admins.
- **AC-006-04** Los pings con más de 30 días se eliminan diariamente.
- **AC-006-05** Todo cambio de estado publica un evento en `/topic/errands/{id}`.
