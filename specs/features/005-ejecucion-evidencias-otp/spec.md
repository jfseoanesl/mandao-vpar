# 005 · Ejecución, evidencias y códigos OTP

## Historias
- **H1** Como mandadero quiero un paso a paso claro: voy en camino → llegué e inicio (OTP) → evidencias → entrego (OTP).
- **H2** Como cliente quiero ver en tiempo real fotos, facturas y firma como prueba.
- **H3** Como Mandao quiero que el servicio solo se pague si el cliente entregó el código de fin.

## Evidencias
Tipos: `FOTO`, `FACTURA` (con `amount` gastado), `FIRMA` (PNG desde canvas), `NOTA` (texto), `ADJUNTO` (subido por el cliente al crear).
Formatos permitidos: JPEG, PNG, PDF (validados por **firma binaria**, no por extensión). Máx. 8 MB.
Las imágenes se re-codifican en el servidor (elimina metadatos EXIF). Se guarda GPS y hora del servidor.
Los archivos se sirven solo a través de la API con verificación de permisos (dueño, mandadero asignado o admin).

## Criterios de aceptación
- **AC-005-01** El mandadero asignado pasa `ASSIGNED → GOING_TO_ORIGIN`.
- **AC-005-02** Con el OTP de inicio correcto pasa `GOING_TO_ORIGIN → IN_PROGRESS`; con uno incorrecto responde 422.
- **AC-005-03** Tras 5 intentos de OTP incorrectos el mandado queda bloqueado para OTP (423) y se marca `needsAttention`.
- **AC-005-04** Con el OTP de fin correcto pasa `IN_PROGRESS → DELIVERED`.
- **AC-005-05** Solo el mandadero asignado puede subir evidencias y solo en `GOING_TO_ORIGIN`, `IN_PROGRESS` o `DELIVERED`.
- **AC-005-06** Un archivo con extensión `.jpg` pero contenido que no es imagen se rechaza con 400.
- **AC-005-07** Un usuario ajeno al mandado no puede descargar sus evidencias (404).
- **AC-005-08** El cliente confirma `DELIVERED → COMPLETED`; si no lo hace en 2 horas se confirma automáticamente.
- **AC-005-09** Los códigos OTP se almacenan cifrados (AES-GCM) y nunca aparecen en respuestas al mandadero ni al admin.
