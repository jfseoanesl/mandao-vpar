# 001 · Autenticación y roles

## Historias
- **H1** Como cliente quiero registrarme con mi celular y contraseña para pedir mandados.
- **H2** Como mandadero quiero registrarme para iniciar mi proceso de verificación.
- **H3** Como usuario quiero que mi sesión se mantenga al recargar la PWA sin volver a escribir mi clave.
- **H4** Como Mandao quiero que cada usuario solo vea y opere lo que le corresponde.
- **H5** Como usuario quiero cambiar mi contraseña y, si la olvido, recuperar el acceso con ayuda de Mandao.

## Roles
`CLIENT`, `COURIER`, `ADMIN`, `COMPANY_ADMIN` (cliente con permisos sobre su empresa).
Los administradores no se registran por la API: se crean por datos semilla o variables de entorno.

## Criterios de aceptación
- **AC-001-01** Dado un celular colombiano válido (10 dígitos, inicia en 3), contraseña ≥ 8 caracteres con letras y números y `acceptPrivacy = true`, cuando me registro, entonces recibo un token de acceso y mi rol es `CLIENT` o `COURIER` según lo elegido.
- **AC-001-02** Si `acceptPrivacy` es falso, el registro se rechaza con 400. La fecha de consentimiento queda registrada.
- **AC-001-03** Un celular ya registrado produce 409 sin revelar datos del otro usuario.
- **AC-001-04** Credenciales incorrectas producen 401 con mensaje genérico ("Celular o contraseña incorrectos").
- **AC-001-05** Tras 5 intentos de login fallidos por IP en 1 minuto, las siguientes solicitudes responden 429.
- **AC-001-06** El token de acceso expira a los 15 minutos. El refresh token viaja en cookie `HttpOnly`, `SameSite=Strict`, ruta `/api/auth`, dura 30 días y **rota** en cada uso; reutilizar uno ya rotado revoca toda la familia de sesiones del usuario.
- **AC-001-07** `POST /api/auth/logout` revoca el refresh token y borra la cookie.
- **AC-001-08** Un `CLIENT` que consulta un mandado ajeno recibe 404 (no 403, para no filtrar existencia).
- **AC-001-09** Endpoints `/api/admin/**` exigen rol `ADMIN`; `/api/courier/**` exigen `COURIER`; `/api/company/**` exigen `COMPANY_ADMIN`.

- **AC-001-10** Con mi contraseña actual correcta y una nueva válida, `PUT /api/me/password` la cambia, revoca **todas** mis sesiones y me entrega una sesión nueva. Con la contraseña actual incorrecta responde 400 y no cambia nada.
- **AC-001-11** El admin puede generar una contraseña temporal (`POST /api/admin/users/{id}/reset-password`) que se muestra una sola vez; se revocan las sesiones del usuario, queda en auditoría y el usuario entra con `mustChangePassword = true`, por lo que la app le exige cambiarla antes de continuar. El admin no puede restablecer su propia contraseña por esta vía.

- **AC-001-12** Al registrarse, el usuario acepta en un mismo paso los **términos y condiciones** (`/terminos`) y la
  política de datos; se guarda la versión de los términos aceptada (`terms_version`) y la fecha (`terms_accepted_at`).

- **AC-001-13** Un usuario no `ADMIN` cuya versión aceptada difiere de la vigente (creado por un admin o por una empresa,
  usuario anterior a los términos, o tras publicar una versión nueva) tiene `termsPending = true`. Mientras tanto la API
  responde **403** con `code = TERMS_PENDING` a todo excepto: `GET /api/me`, `POST /api/me/terms`, `PUT /api/me/password`,
  `/api/auth/**`, `/api/public/**` y `DELETE /api/push/subscriptions`; el WebSocket también rechaza la conexión.
- **AC-001-14** `POST /api/me/terms` con la versión vigente registra versión y fecha, y devuelve una sesión nueva cuyo
  token ya no está pendiente. Con otra versión responde 409 (la app debe recargar el texto vigente).

## Recuperación de acceso (MVP sin SMS)
No hay envío de SMS/correo en el piloto. El usuario pulsa "¿Olvidaste tu contraseña?", escribe a soporte por WhatsApp,
el admin verifica su identidad (nombre y un mandado reciente) y le envía la contraseña temporal.

## Diseño
- Spring Security stateless + `oauth2-resource-server` (JWT HS256, clave de ≥ 32 bytes por entorno).
- `RefreshToken` guarda solo el **hash SHA-256** del token.
- `RateLimiter` en memoria por IP y ruta (ventana fija).
