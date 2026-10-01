# 011 · Cuentas empresa (Mandao Empresas)

Objetivo del piloto: validar si 5 empresas usan Mandao de forma **recurrente** en horario laboral.

## Historias
- **H1** Como operador quiero crear una empresa con su administrador y plan ($99.000/mes, cobro manual).
- **H2** Como administrador de empresa quiero agregar empleados que pidan mandados a nombre de la empresa.
- **H3** Como empresa quiero direcciones frecuentes, historial, comprobantes con evidencias y un reporte mensual.

## Criterios de aceptación
- **AC-011-01** El admin crea una empresa (nombre, NIT, plan) y su usuario `COMPANY_ADMIN`.
- **AC-011-02** El `COMPANY_ADMIN` crea miembros (rol `CLIENT` asociados a la empresa).
- **AC-011-03** Los mandados creados por miembros quedan asociados a la empresa (segmento empresa en KPIs).
- **AC-011-04** El `COMPANY_ADMIN` ve todos los mandados de su empresa y ninguno de otra.
- **AC-011-05** Direcciones frecuentes: crear, listar y eliminar; visibles para todos los miembros al crear mandados.
- **AC-011-06** Reporte mensual CSV con fecha, solicitante, categoría, estado, precio y mandadero.
- **AC-011-07** Cada mandado tiene un comprobante imprimible con estados, horas y evidencias.
