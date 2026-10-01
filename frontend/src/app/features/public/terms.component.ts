import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';

/** Versión vigente; debe coincidir con AuthDtos.TERMS_VERSION en el backend (AC-001-12). */
export const TERMS_VERSION = '1.0';
const TERMS_DATE = '1 de octubre de 2026';

/**
 * Datos del responsable. Complétalos cuando Mandao esté constituida; los campos vacíos no se muestran.
 * El texto es una base para el piloto y DEBE revisarlo un abogado antes del lanzamiento comercial.
 */
const LEGAL = {
  brand: 'Mandao',
  holder: '',        // Razón social o nombre del titular, p. ej. "Mandao S.A.S."
  taxId: '',         // NIT o cédula del titular
  address: 'Valledupar, Cesar, Colombia',
  email: '',         // Correo de soporte y PQR
};

const SECTIONS = [
  ['objeto', '1. Qué es Mandao'],
  ['cuenta', '2. Tu cuenta'],
  ['solicitud', '3. Cómo funciona un mandado'],
  ['precios', '4. Precios y pagos'],
  ['bolsa', '5. Bolsa de compras'],
  ['codigos', '6. Códigos de inicio y entrega, y evidencias'],
  ['cancelaciones', '7. Cancelaciones y mandados no completados'],
  ['prohibidos', '8. Mandados no permitidos'],
  ['responsabilidad', '9. Responsabilidad'],
  ['mandaderos', '10. Condiciones para mandaderos'],
  ['empresas', '11. Cuentas empresa'],
  ['calificaciones', '12. Calificaciones y conducta'],
  ['pqr', '13. Peticiones, quejas y reclamos'],
  ['datos', '14. Datos personales'],
  ['cambios', '15. Cambios a estos términos y ley aplicable'],
];

/** Términos y condiciones de uso (versión del piloto). */
@Component({
  selector: 'app-terms',
  standalone: true,
  imports: [RouterLink],
  template: `
    <main class="page terms">
      <a routerLink="/">← Volver</a>
      <h1 class="mt">Términos y condiciones</h1>
      <p class="muted">Versión {{ version }} · vigente desde el {{ date }}</p>

      <div class="card stack">
        <p>Estos términos regulan el uso de la aplicación <strong>{{ legal.brand }}</strong> y de los servicios que
          se solicitan a través de ella. Al crear tu cuenta declaras que los leíste y los aceptas. Si no estás de
          acuerdo, no uses la aplicación.</p>
        <p><strong>Responsable:</strong> {{ legal.holder || legal.brand }}@if (legal.taxId) {, identificado con
          {{ legal.taxId }}}, con domicilio en {{ legal.address }}.
          @if (legal.email) { Correo: <a [href]="'mailto:' + legal.email">{{ legal.email }}</a>. }
          @if (whatsapp()) { WhatsApp de soporte: <a [href]="'https://wa.me/' + whatsapp()" target="_blank" rel="noopener">+{{ whatsapp() }}</a>. }
        </p>
        <nav aria-label="Contenido">
          <strong>Contenido</strong>
          <ol class="toc">
            @for (s of sections; track s[0]) { <li><a routerLink="/terminos" [fragment]="s[0]">{{ s[1] }}</a></li> }
          </ol>
        </nav>
      </div>

      <section id="objeto" class="card mt">
        <h2>1. Qué es Mandao</h2>
        <p>Mandao es una plataforma tecnológica que conecta a personas y empresas que necesitan hacer una tarea
          (enviar algo, comprar, hacer una diligencia, recoger, hacer una fila, etc.) con <strong>mandaderos</strong>:
          personas verificadas que ejecutan esa tarea. Mandao calcula el precio, coordina la asignación, registra
          evidencias y gestiona el pago.</p>
        <p>Actualmente opera en etapa <strong>piloto</strong> en Valledupar y sus alrededores (hasta 30 km del centro).
          Algunas funciones pueden cambiar o no estar disponibles mientras validamos el servicio.</p>
      </section>

      <section id="cuenta" class="card mt">
        <h2>2. Tu cuenta</h2>
        <ul>
          <li>Debes ser mayor de edad y dar información real. Tu celular es tu identificador: una cuenta por persona.</li>
          <li>Eres responsable de tu contraseña y de lo que se haga con tu cuenta. Si crees que alguien la usa sin
            permiso, cámbiala y escríbenos.</li>
          <li>Si olvidas tu contraseña, soporte verificará tu identidad y te enviará una contraseña temporal que
            deberás cambiar al entrar.</li>
          <li>Podemos suspender o cerrar cuentas que incumplan estos términos, que presenten indicios de fraude o
            que pongan en riesgo a otros usuarios.</li>
        </ul>
      </section>

      <section id="solicitud" class="card mt">
        <h2>3. Cómo funciona un mandado</h2>
        <ol>
          <li>Describes la tarea, marcas los puntos en el mapa y ves el precio <strong>antes</strong> de confirmar.</li>
          <li>Eliges <strong>Precio Mandao</strong> (precio fijo calculado por la app) o <strong>Recibir ofertas</strong>
            (los mandaderos proponen un precio y tú eliges, rechazas o contraofertas).</li>
          <li>Un mandadero cercano acepta el mandado o, si nadie lo hace a tiempo, nuestro equipo lo asigna.</li>
          <li>Sigues al mandadero en el mapa, recibes las evidencias y confirmas cuando todo está bien.</li>
        </ol>
        <p>Describe la tarea con claridad: el mandadero hará lo que está escrito en la solicitud. Si cambias lo
          pedido (otra dirección, más paradas, más espera), el precio puede ajustarse, siempre informándote antes.</p>
      </section>

      <section id="precios" class="card mt">
        <h2>4. Precios y pagos</h2>
        <ul>
          <li>El precio se calcula con una tarifa base, la distancia, el tiempo de espera que supere el incluido,
            las paradas adicionales y la complejidad de la diligencia. Siempre ves el desglose:
            <strong>servicio + tarifa de plataforma</strong>.</li>
          <li>El precio que confirmas es el que pagas por el servicio, salvo que cambies lo pedido. Las compras
            hechas con la bolsa se pagan aparte, según la factura.</li>
          <li>Durante el piloto los pagos se hacen en <strong>efectivo, Nequi, Daviplata o transferencia</strong>,
            y nuestro equipo los confirma manualmente. Paga solo por los canales que muestra la app o que te indique
            soporte; Mandao no te pedirá claves ni códigos bancarios.</li>
          <li>El servicio se paga cuando se completa. Puedes descargar el comprobante con las evidencias desde
            el detalle del mandado.</li>
        </ul>
      </section>

      <section id="bolsa" class="card mt">
        <h2>5. Bolsa de compras</h2>
        <p>Si el mandado implica comprar algo, autorizas un <strong>monto máximo</strong> (la bolsa). El mandadero
          solo puede gastar hasta ese monto y debe registrar la <strong>factura</strong> de cada compra. Pagas
          únicamente lo gastado con factura y la diferencia se te devuelve por el mismo medio de pago.</p>
        <p>Ejemplo: autorizas $80.000, la factura es de $63.700 ⇒ se te devuelven $16.300.</p>
        <p>El mandadero no está obligado a financiar compras ni a pagar un valor mayor al autorizado. Si el producto
          cuesta más, te consultará antes.</p>
      </section>

      <section id="codigos" class="card mt">
        <h2>6. Códigos de inicio y entrega, y evidencias</h2>
        <ul>
          <li>Cada mandado tiene un <strong>código de inicio</strong> y un <strong>código de entrega</strong> que solo
            ves tú. Da el de inicio cuando el mandadero llegue a empezar, y el de entrega <strong>solo</strong> cuando
            recibas conforme. Entregar el código de entrega equivale a aceptar la entrega.</li>
          <li>El mandadero registra evidencias (fotos, facturas, firma de quien recibe y notas) con fecha, hora y
            ubicación. Son el respaldo de lo que se hizo, para ti y para él.</li>
          <li>Tras la entrega puedes confirmar el mandado; si no lo confirmas ni reportas un problema en
            <strong>2 horas</strong>, se confirma automáticamente.</li>
        </ul>
      </section>

      <section id="cancelaciones" class="card mt">
        <h2>7. Cancelaciones y mandados no completados</h2>
        <ul>
          <li>Puedes cancelar <strong>sin costo</strong> desde la app mientras el mandado no haya empezado
            (antes de dar el código de inicio).</li>
          <li>Una vez iniciado, la cancelación se gestiona con soporte: pagarás las compras ya realizadas con factura
            y, según lo avanzado, una parte proporcional del servicio, que siempre te informaremos antes.</li>
          <li>Si el mandado no se puede completar por causas ajenas a ti (por ejemplo, la oficina estaba cerrada o el
            producto no existía), no se cobra el servicio no prestado y se te devuelve el dinero no usado de la bolsa.</li>
          <li>El derecho de retracto no aplica a servicios cuya ejecución ya comenzó con tu aceptación
            (Ley 1480 de 2011, art. 47).</li>
        </ul>
      </section>

      <section id="prohibidos" class="card mt">
        <h2>8. Mandados no permitidos</h2>
        <p>No se pueden solicitar mandados que impliquen:</p>
        <ul>
          <li>Bienes o actividades ilegales: drogas, armas, municiones, explosivos, mercancía de contrabando o robada.</li>
          <li>Sustancias peligrosas, inflamables o tóxicas, y animales vivos.</li>
          <li>Bebidas alcohólicas o cigarrillos para menores de edad.</li>
          <li>Medicamentos de control sin la fórmula médica correspondiente.</li>
          <li>Transportar dinero en efectivo o títulos valores, salvo los pagos propios del mandado.</li>
          <li>Trámites que la ley exige hacer personalmente o que requieren un poder que no se haya entregado.</li>
          <li>Suplantar identidades, acosar, vigilar o perjudicar a otras personas.</li>
        </ul>
        <p>El mandadero puede rechazar o suspender un mandado que considere riesgoso o contrario a estos términos.
          Si el envío o la compra tiene un valor alto, indícalo en la descripción: podremos pedir condiciones
          adicionales o no aceptarlo.</p>
      </section>

      <section id="responsabilidad" class="card mt">
        <h2>9. Responsabilidad</h2>
        <ul>
          <li>Mandao verifica la identidad de los mandaderos, registra evidencias de cada servicio y atiende las
            reclamaciones. Si un objeto se pierde o daña durante el mandado por causa del mandadero, gestionaremos
            la reclamación con base en las evidencias y responderemos conforme a la ley colombiana.</li>
          <li>No respondemos por el contenido de paquetes cerrados que no se declararon, por demoras de terceros
            (filas, cierres, tráfico, entidades) ni por la calidad de productos de terceros comprados según tus
            instrucciones; sí te ayudaremos a gestionar el cambio o la reclamación cuando sea posible.</li>
          <li>Los tiempos que muestra la app son estimados.</li>
        </ul>
      </section>

      <section id="mandaderos" class="card mt">
        <h2>10. Condiciones para mandaderos</h2>
        <ul>
          <li>Para recibir mandados debes completar el registro (cédula, selfie, cuenta de pago y, si usas vehículo
            motorizado, licencia, SOAT y tarjeta de propiedad vigentes) y ser aprobado por Mandao.</li>
          <li>Decides cuándo te conectas y qué mandados aceptas. Mientras estés conectado o en un servicio, la app
            usa tu ubicación para asignarte mandados cercanos y mostrar el seguimiento al cliente.</li>
          <li>Debes ejecutar el mandado tal como fue pedido, registrar evidencias reales, usar la bolsa solo para lo
            autorizado y con factura, y tratar con respeto a clientes y terceros. Cumple las normas de tránsito.</li>
          <li>Tu ganancia es el valor del servicio menos la tarifa de plataforma, y se ve antes de aceptar. Los pagos se
            liquidan a la cuenta que registraste, en los plazos que informe Mandao.</li>
          <li>La relación entre el mandadero y Mandao se rige por la legislación colombiana aplicable a la prestación
            de servicios a través de plataformas digitales.</li>
          <li>El fraude (evidencias falsas, cobros indebidos, uso indebido de la bolsa) causa la desactivación de la
            cuenta y puede ser denunciado ante las autoridades.</li>
        </ul>
      </section>

      <section id="empresas" class="card mt">
        <h2>11. Cuentas empresa</h2>
        <p>Las empresas pueden tener varios usuarios, direcciones frecuentes, historial y reportes. El plan mensual y
          sus condiciones se acuerdan por escrito con cada empresa; el administrador de la cuenta empresa es
          responsable de los usuarios que agrega y de los mandados que estos solicitan.</p>
      </section>

      <section id="calificaciones" class="card mt">
        <h2>12. Calificaciones y conducta</h2>
        <p>Al terminar puedes calificar el servicio. Las calificaciones deben ser honestas y respetuosas; podemos
          retirar las que sean ofensivas o falsas. La reputación del mandadero se calcula con sus calificaciones,
          su tasa de cumplimiento y su puntualidad.</p>
      </section>

      <section id="pqr" class="card mt">
        <h2>13. Peticiones, quejas y reclamos</h2>
        <p>Escríbenos por el WhatsApp de soporte@if (legal.email) { o a {{ legal.email }}}, indicando el código del
          mandado. Responderemos dentro de los <strong>15 días hábiles</strong> siguientes. Si no quedas satisfecho,
          puedes acudir a la Superintendencia de Industria y Comercio.</p>
      </section>

      <section id="datos" class="card mt">
        <h2>14. Datos personales</h2>
        <p>Tratamos tus datos según nuestra <a routerLink="/privacidad">Política de tratamiento de datos personales</a>
          (Ley 1581 de 2012). La ubicación del mandadero solo se registra mientras está disponible o en servicio, y
          los documentos de identidad solo los ve el equipo de verificación.</p>
      </section>

      <section id="cambios" class="card mt">
        <h2>15. Cambios a estos términos y ley aplicable</h2>
        <p>Podemos actualizar estos términos; publicaremos la nueva versión con su fecha y te avisaremos en la app
          antes de que empiece a regir. Si sigues usando Mandao después de esa fecha, aceptas la nueva versión.</p>
        <p>Estos términos se rigen por las leyes de la República de Colombia, en especial la Ley 1480 de 2011
          (Estatuto del Consumidor), la Ley 527 de 1999 sobre comercio electrónico y la Ley 1581 de 2012. La aceptación
          electrónica al registrarte tiene plena validez.</p>
      </section>
    </main>`,
  styles: [`
    .terms section { scroll-margin-top: 16px; }
    .terms ul, .terms ol { padding-left: 20px; margin: 0 0 .5rem; }
    .terms li { margin-bottom: 6px; }
    .terms h2 { font-size: 1.1rem; }
    .terms .toc { columns: 2; column-gap: 24px; font-size: 14px; list-style: none; padding-left: 0; }
    @media (max-width: 560px) { .terms .toc { columns: 1; } }
  `],
})
export class TermsComponent implements OnInit {
  private api = inject(ApiService);
  readonly version = TERMS_VERSION;
  readonly date = TERMS_DATE;
  readonly legal = LEGAL;
  readonly sections = SECTIONS;
  whatsapp = signal('');

  ngOnInit() {
    this.api.publicConfig().subscribe({ next: c => this.whatsapp.set(c.whatsapp), error: () => {} });
  }
}
