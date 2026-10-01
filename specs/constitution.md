# Constitución de Mandao (MVP)

Principios no negociables. Toda especificación, plan y línea de código debe respetarlos.
Si una feature entra en conflicto con un principio, gana el principio y se ajusta la feature.

## P1. Validar antes de escalar
El MVP existe para responder **una pregunta**: *¿la gente de Valledupar paga, y vuelve a pagar, para que le hagan sus mandados?*
- Toda feature debe alimentar al menos un KPI del piloto (ver `features/010-panel-admin-kpis`).
- Si una feature no ayuda a operar o medir el piloto, se pospone.
- Preferimos operación manual asistida (admin) a automatización compleja.

## P2. La especificación manda (SDD)
- El contrato HTTP vive en `specs/api/openapi.yaml`. El backend no expone endpoints fuera del contrato
  (lo verifica `ApiContractTest`).
- Cada criterio de aceptación tiene un ID (`AC-002-03`) y al menos una prueba que lo referencia.
- Cambiar comportamiento = cambiar primero la spec.

## P3. Confianza por evidencia
El diferenciador es la confianza: cada mandado deja rastro verificable
(estados con hora, fotos con GPS y hora, facturas, firma, códigos OTP de inicio y fin).
El mandadero **no cobra** hasta que el servicio se completa con OTP de fin.

## P4. Seguridad por defecto (OWASP ASVS L1–L2)
- Autenticación con contraseñas BCrypt, JWT de acceso de 15 min, refresh rotativo en cookie `HttpOnly`.
- Autorización por **rol y por pertenencia** al recurso en cada operación (sin IDOR).
- Validación de entrada en el borde; archivos validados por firma binaria, tamaño y tipo.
- Rate limiting en login, registro, OTP y cotización.
- Secretos solo por variables de entorno. Logs sin datos personales.

## P5. Privacidad (Ley 1581 de 2012 — Habeas Data)
- Consentimiento explícito y registrado en el registro.
- La ubicación del mandadero solo se captura mientras está **disponible** o **en servicio**.
- Los pings de ubicación se purgan a los 30 días.
- Documentos de identidad visibles solo para administradores.

## P6. Simplicidad deliberada
- Monolito modular (un paquete por dominio). Sin microservicios, sin colas externas, sin Redis en el MVP.
- Una sola PWA con rutas por rol (cliente, mandadero, admin).
- Pagos manuales detrás de la interfaz `PaymentGateway`; la pasarela (Wompi) se conecta después sin tocar el dominio.
- Mapas libres (OpenStreetMap, Nominatim, OSRM) con degradación elegante si no responden.

## P7. Lenguaje del negocio
El código y la UI usan el lenguaje de Mandao: *mandado* (`Errand`), *mandadero* (`Courier`),
*evidencia*, *bolsa de compras*, *diligencia*. Ver `domain-glossary.md`.
