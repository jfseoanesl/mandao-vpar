# 012 · Notificaciones push

## Historias
- **H1** Como mandadero quiero enterarme de un mandado cercano aunque tenga el celular bloqueado, para tomarlo a tiempo
  (KPI: asignación < 5 min).
- **H2** Como cliente quiero saber cuándo me asignan mandadero, cuándo empieza y cuándo entregan, sin tener la app abierta.
- **H3** Como operador quiero enterarme de los mandados que requieren asignación manual.

## Reglas
- Estándar **Web Push** (RFC 8030) con cifrado **aes128gcm** (RFC 8291) y autenticación **VAPID** (RFC 8292).
  Funciona en Android (Chrome) y en iPhone con iOS ≥ 16.4 **solo si la PWA está instalada** en la pantalla de inicio.
- Las suscripciones se guardan por usuario y dispositivo (`endpoint` único). Al cerrar sesión se elimina la del dispositivo.
- El contenido es mínimo (sin direcciones ni datos personales): título, texto corto y la ruta a abrir en la app.
- Se envía **después del commit** de la transacción y de forma asíncrona: una falla de push nunca revierte ni demora
  la operación del negocio.
- Si el servicio de push responde 404 o 410, la suscripción venció y se borra.

| Evento | Destinatario | Mensaje | Abre |
|---|---|---|---|
| Mandado ofrecido por el despachador | Mandadero candidato | "Nuevo mandado cerca · Ganas $X" | `/mandadero` |
| Contraoferta del cliente | Mandadero | "Te hicieron una contraoferta" | `/mandadero` |
| Oferta recibida (negociable) | Cliente | "Recibiste una oferta" | `/app/mandados/{id}` |
| `ASSIGNED`, `GOING_TO_ORIGIN`, `IN_PROGRESS`, `DELIVERED`, `FAILED` | Cliente | Estado en lenguaje claro | `/app/mandados/{id}` |
| `CANCELLED` con mandadero asignado | Mandadero | "Mandado cancelado" | `/mandadero` |
| `needsAttention` | Admins | "Mandado sin asignar" | `/admin/mandados/{id}` |

## Criterios de aceptación
- **AC-012-01** `GET /api/push/key` devuelve la llave pública VAPID (base64url, 65 bytes sin comprimir) a usuarios autenticados.
- **AC-012-02** `POST /api/push/subscriptions` registra (o actualiza) la suscripción del dispositivo para el usuario
  actual; `DELETE /api/push/subscriptions` con el `endpoint` la elimina. Un usuario no puede borrar la de otro.
- **AC-012-03** El mensaje enviado se descifra con las llaves del navegador (`p256dh`, `auth`) y contiene el título, el
  texto y la URL esperados; la petición lleva `Content-Encoding: aes128gcm`, `TTL` y `Authorization: vapid t=…, k=…`
  con un JWT ES256 cuyo `aud` es el origen del endpoint.
- **AC-012-04** Cuando el despachador ofrece un mandado a un mandadero suscrito, este recibe el push.
- **AC-012-05** Una respuesta 410 del servicio de push elimina la suscripción.

## Configuración
`VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY` (base64url) y `VAPID_SUBJECT` (`mailto:`). Se generan una sola vez con
`docker compose run --rm --no-deps --entrypoint java backend -Dloader.main=com.mandao.notifications.VapidKeyGenerator -cp app.jar org.springframework.boot.loader.launch.PropertiesLauncher`.
Sin llaves configuradas, el backend genera unas temporales al arrancar (válidas solo hasta reiniciar; solo desarrollo).
