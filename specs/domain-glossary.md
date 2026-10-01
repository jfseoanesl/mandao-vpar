# Glosario de dominio

| Término (UI) | Código | Definición |
|---|---|---|
| Mandado | `Errand` | Tarea urbana solicitada por un cliente. Puede o no incluir transporte. |
| Mandadero | `Courier` | Persona verificada que ejecuta mandados a través de la plataforma. |
| Cliente | `CLIENT` | Persona o miembro de empresa que solicita mandados. |
| Empresa | `Company` | Cuenta B2B con varios usuarios, direcciones frecuentes y reportes. |
| Categoría | `ErrandCategory` | `ENVIO`, `COMPRA`, `DILIGENCIA`, `BUSCAR_COMPRAR`, `RECOGER_ENTREGAR`, `EMPRESARIAL`, `OTRO`. |
| Complejidad | `Complexity` | `SENCILLA` (+$0), `MEDIA` (+$2.500), `COMPLEJA` (+$5.000). |
| Parada | `ErrandStop` | Punto geográfico del mandado (origen, intermedias, destino). |
| Cotización | `Quote` | Precio calculado por el motor de precios con su desglose. |
| Mandado negociable | `pricingMode = NEGOTIABLE` | El precio lo proponen los mandaderos; el cliente acepta, rechaza o contraoferta. |
| Oferta | `ErrandOffer` | Propuesta de precio de un mandadero (o contraoferta del cliente) en un mandado negociable. |
| Evidencia | `Evidence` | Prueba de ejecución: `FOTO`, `FACTURA`, `FIRMA`, `NOTA`, con GPS y hora. |
| Código de inicio / fin | `startOtp` / `endOtp` | Códigos de 4 dígitos que solo conoce el cliente. El mandadero los ingresa para iniciar y cerrar. |
| Bolsa de compras | `purchaseBudget` | Monto máximo autorizado por el cliente para comprar. Se retiene, se liquida contra la factura y se devuelve la diferencia. |
| Tarifa de plataforma | `platformFee` | Parte del precio que queda para Mandao. |
| Pago al mandadero | `courierPayout` | Parte del precio que recibe el mandadero. |
| Liquidación | `Settlement` | Cierre económico del mandado (asientos en `ledger_entries`). |
| Recompra | `repeat` | Cliente que solicita un segundo mandado dentro del periodo. |
