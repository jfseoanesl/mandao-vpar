import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Política de tratamiento de datos (Ley 1581 de 2012). Texto base para el piloto:
 * debe revisarlo un abogado antes del lanzamiento comercial.
 */
@Component({
  selector: 'app-privacy',
  standalone: true,
  imports: [RouterLink],
  template: `
    <main class="page">
      <a routerLink="/">← Volver</a>
      <h1 class="mt">Política de tratamiento de datos personales</h1>
      <div class="card stack">
        <p><strong>Responsable:</strong> Mandao (piloto), Valledupar, Cesar, Colombia.</p>
        <h3>Datos que recolectamos</h3>
        <p>Nombre, celular, correo (opcional), direcciones de los mandados, fotos y documentos que adjuntes,
          y calificaciones. De los mandaderos, además: cédula, selfie, documentos del vehículo, cuenta de pago
          y ubicación GPS.</p>
        <h3>Finalidades</h3>
        <p>Prestar y coordinar los mandados, calcular precios, verificar la identidad de los mandaderos,
          generar evidencias de ejecución, atender soporte, prevenir fraude y medir la calidad del servicio.</p>
        <h3>Ubicación del mandadero</h3>
        <p>Solo se captura mientras el mandadero está <strong>disponible</strong> o <strong>en servicio</strong>.
          El historial de ubicaciones se elimina a los 30 días.</p>
        <h3>Documentos de identidad</h3>
        <p>Solo los ve el equipo de operación para verificar al mandadero. Nunca se comparten con clientes.</p>
        <h3>Tus derechos</h3>
        <p>Conocer, actualizar, rectificar y suprimir tus datos, y revocar la autorización, escribiendo a nuestro
          canal de soporte. Responderemos en los plazos de la Ley 1581 de 2012 y el Decreto 1377 de 2013.</p>
        <p>Consulta también los <a routerLink="/terminos">términos y condiciones</a> del servicio.</p>
      </div>
    </main>`,
})
export class PrivacyComponent {}
