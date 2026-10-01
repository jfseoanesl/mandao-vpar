# Mandao — MVP de validación

> **"Tú lo pides. Nosotros lo hacemos."** Mandados y diligencias en Valledupar.

MVP para operar el **piloto de 30 días** (5 mandaderos, 30–50 clientes, 5 empresas) y medir si el negocio funciona
antes de invertir en la plataforma definitiva. El modelo de negocio está en [`Proyecto/Mandao.md`](Proyecto/Mandao.md).

| Interfaz | Ruta | Para quién |
|---|---|---|
| PWA cliente | `/app` | Pide mandados, cotiza al instante o recibe ofertas, sigue al mandadero en vivo, confirma y califica |
| PWA mandadero | `/mandadero` | Registro y documentos, disponibilidad con GPS, acepta u oferta, ejecución guiada con OTP y evidencias, ganancias |
| Panel admin | `/admin` | KPIs del piloto, mapa en vivo, asignación manual, verificación, pagos, tarifas, empresas |
| Cuenta empresa | `/app/empresa` | Usuarios, direcciones frecuentes, historial y reporte mensual CSV |

## Arquitectura

```
Navegador (PWA Angular 18)
        │ HTTPS
   ┌────▼─────┐   /api, /ws    ┌──────────────────────┐     ┌──────────────────────┐
   │  Caddy   ├───────────────►│ Spring Boot 3 (J21)  ├────►│ PostgreSQL 16+PostGIS│
   │ (web)    │                │ monolito modular     │     └──────────────────────┘
   └──────────┘                │ REST + STOMP/WS      ├────► archivos (volumen o S3)
                               └──────────────────────┘
```

- **Backend** (`backend/`): módulos `identity, orders, pricing, matching, tracking, evidence, payments, ratings,
  couriers, companies, admin, analytics, shared`. Flyway, JWT corto + refresh rotativo en cookie HttpOnly,
  rate limiting, OTP cifrados, autorización por pertenencia (anti-IDOR).
- **Frontend** (`frontend/`): una sola PWA con rutas perezosas por rol, Leaflet + OpenStreetMap, Nominatim, STOMP.
- **Especificaciones** (`specs/`): metodología SDD. `api/openapi.yaml` es el contrato y una prueba verifica que el
  backend lo cumple; cada feature (`features/001…011`) tiene criterios Given/When/Then con ID que las pruebas citan.
- **Infra** (`infra/`): Docker Compose con `db` (PostGIS), `backend` y `web` (Caddy: sirve la PWA, hace proxy y
  obtiene HTTPS automáticamente).

## Arranque rápido (tu PC)

Requisitos: Docker Desktop.

```bash
cd infra
cp .env.example .env          # cambia los secretos; para probar pon SEED_DEMO=true
docker compose up -d --build
```

Abre **http://localhost:8088**. Con `SEED_DEMO=true` existen estos usuarios (contraseña `Mandao2026`):

| Rol | Celular |
|---|---|
| Admin | 3000000000 |
| Mandaderos aprobados | 3001000001 … 3001000005 |
| Clientes | 3002000001, 3002000002 |
| Admin de empresa | 3003000001 |

> Los datos demo son solo para pruebas. En el piloto real usa `SEED_DEMO=false` y crea el admin con `ADMIN_PHONE` /
> `ADMIN_PASSWORD`.

### Despliegue local con un comando (Windows)

```powershell
powershell -ExecutionPolicy Bypass -File infra\deploy-local.ps1          # -NoCache, -NoDemo, -NoLan, -Reset
```

Completa `.env` (secretos y llaves de notificaciones si faltan), reconstruye, espera a que todo esté sano y publica:

- **En el PC:** http://localhost:8088
- **En el celular** (misma red): `https://<IP-del-PC>:8443`, con HTTPS de una CA local de Caddy. Así funcionan el GPS,
  la cámara y las notificaciones, que el celular bloquea sin HTTPS. Primero instala el certificado raíz desde
  `http://<IP-del-PC>:8088/mandao-local-ca.crt` (Android: Ajustes → Seguridad → Cifrado y credenciales → Instalar
  certificado → Certificado de CA). Si no conecta, permite el puerto 8443 en el firewall de Windows.

### Desarrollo

```bash
# Base de datos
docker compose -f infra/docker-compose.yml up -d db   # o un PostGIS local en :5432
# Backend (http://localhost:8080, Swagger en /swagger-ui.html)
cd backend && COOKIE_SECURE=false SEED_DEMO=true mvn spring-boot:run
# Frontend (http://localhost:4200, proxy a :8080)
cd frontend && npm install && npm start
```

## Pruebas

| Qué | Comando |
|---|---|
| Backend: unitarias + integración (Testcontainers/PostGIS) + contrato OpenAPI | `cd backend && mvn verify` |
| Frontend: build de producción | `cd frontend && npx ng build` |
| E2E: flujo completo cliente → mandadero → admin (stack arriba con `SEED_DEMO=true`) | `cd frontend && npx playwright test` |

La CI (`.github/workflows/ci.yml`) corre las tres en cada push, más OWASP Dependency-Check; Dependabot mantiene las
dependencias al día.

## Despliegue del piloto (VPS)

Un VPS de 2 vCPU / 4 GB (AWS Lightsail, DigitalOcean, Hetzner) basta para el piloto.

1. Apunta un dominio (p. ej. `app.mandao.co`) a la IP del servidor e instala Docker.
2. Copia el repositorio, crea `infra/.env` desde `.env.example` con **secretos nuevos** (`openssl rand -base64 48`) y:
   ```
   SITE_ADDRESS=app.mandao.co
   PUBLIC_URL=https://app.mandao.co
   HTTP_PORT=80
   HTTPS_PORT=443
   COOKIE_SECURE=true
   SEED_DEMO=false
   ```
3. Genera las llaves de notificaciones push **una sola vez** y pégalas en `.env` (`VAPID_PUBLIC_KEY`,
   `VAPID_PRIVATE_KEY`). Si las cambias después, todos tendrán que volver a activar las notificaciones:
   ```bash
   docker compose build backend
   docker compose run --rm --no-deps --entrypoint java backend \
     -Dloader.main=com.mandao.notifications.VapidKeyGenerator \
     -cp app.jar org.springframework.boot.loader.launch.PropertiesLauncher
   ```
4. `docker compose up -d --build`. Caddy obtiene el certificado HTTPS solo.
5. Entra con `ADMIN_PHONE` / `ADMIN_PASSWORD`, ajusta tarifas en **/admin/tarifas** y verifica a los mandaderos en
   **/admin/mandaderos**.

**Copias de seguridad** (diarias, por cron):
```bash
docker compose exec -T db pg_dump -U mandao mandao | gzip > backup-$(date +%F).sql.gz
docker run --rm -v mandao_files:/f -v $PWD:/b alpine tar czf /b/files-$(date +%F).tgz -C /f .
```

**Archivos en S3**: cuando crezca el volumen, define `STORAGE_TYPE=s3` y las variables `S3_*` (AWS S3 o compatible).
No hay que cambiar código.

## Operación diaria del piloto

1. **Mañana:** revisa en **/admin** los KPIs contra la meta (mandados/día ≥ 15, ticket ≥ $12.000, asignación ≤ 5 min,
   completados ≥ 95 %, calificación ≥ 4,6, recompra ≥ 30 %).
2. **Durante el día:** deja abierto **/admin/en-vivo**. Si un mandado no se asigna en 5 minutos, aparece una alerta ⚠
   y se asigna a mano desde su detalle.
3. **Pagos:** confirma en **/admin/pagos** cada recaudo (efectivo, Nequi, Daviplata o transferencia) con su referencia.
   Cuando le pagues al mandadero, regístralo en el detalle del mandado.
4. **Contraseñas olvidadas:** el usuario toca "¿Olvidaste tu contraseña?" y escribe al WhatsApp de soporte. Verifica
   su identidad (nombre y un mandado reciente) y en **/admin/usuarios → Restablecer clave** genera una contraseña
   temporal que se envía por WhatsApp; al entrar, la app le obliga a crear una nueva.
5. **Semanal:** exporta el CSV de mandados (botón en /admin) para analizar en hoja de cálculo; el canal de
   adquisición (`?utm_source=` o `?ref=` en los enlaces que compartas) aparece en los desgloses.

## Términos y condiciones

Texto en `/terminos` (`frontend/src/app/features/public/terms.component.ts`; completa allí los datos legales del
responsable). Cada usuario guarda la versión aceptada y la fecha. Quien no tenga la versión vigente (usuarios creados
por el admin o por una empresa, o todos cuando publicas una versión nueva) no puede usar la API hasta aceptarla: la app
le muestra la pantalla de aceptación al entrar. Para publicar una versión nueva, actualiza el texto y sube la versión
**en ambos lados**: `TERMS_VERSION` en ese componente y `AuthDtos.TERMS_VERSION` en el backend. Los admins no aceptan
términos de usuario.

## Notificaciones push

Web Push estándar (cifrado RFC 8291 + VAPID, implementado con la criptografía del JDK y validado con el vector de
prueba oficial del RFC). Avisa al mandadero de mandados cercanos con el celular bloqueado, al cliente de cada cambio de
su mandado y al admin de los mandados sin asignar. En **Android** basta con activarlas; en **iPhone** (iOS ≥ 16.4) solo
funcionan con la PWA **instalada** en la pantalla de inicio. Requieren HTTPS (o `localhost`).

## Mapas

Leaflet con el mapa base de OpenStreetMap, suficiente para el tráfico del piloto (con atribución, como exige su
política). Antes de escalar, contrata un proveedor de teselas (MapTiler, Stadia, Mapbox): agrégalo en
`frontend/src/app/core/geo.service.ts` (`TILE_SOURCES`) y su dominio en la CSP del `frontend/Caddyfile`, en
`img-src` **y** `connect-src` (el service worker de la PWA descarga las teselas con `fetch`).

## Ícono y marca

El ícono actual es provisional (`frontend/src/brand/icon.svg`). Para usar el logo definitivo, reemplaza ese SVG
(fondo completo, contenido dentro del 80 % central) y regenera los PNG de `frontend/public/icons/`.

## Seguridad y privacidad

- Sesión: access token de 15 min en memoria + refresh rotativo en cookie `HttpOnly; Secure; SameSite=Strict`.
- Cabeceras: CSP estricta, HSTS, `X-Frame-Options: DENY`, `nosniff`; la base de datos no se expone a internet.
- Archivos privados: se validan por firma binaria, se sirven solo a quien tiene derecho y las fotos se recomprimen en
  el teléfono (se elimina EXIF).
- **Ley 1581 de 2012:** consentimiento explícito en el registro, política en `/privacidad`, ubicación del mandadero
  solo mientras está disponible o en servicio, documentos visibles solo para el admin.

## Siguientes pasos (después de validar)

Pasarela de pagos (Wompi) detrás de `PaymentGateway`, verificación del celular por SMS, app nativa si la PWA se queda
corta, y Redis para escalar el tiempo real a varias instancias.
