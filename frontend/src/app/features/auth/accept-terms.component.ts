import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/labels';
import { TERMS_VERSION } from '../public/terms.component';

/**
 * AC-001-13/14: usuarios creados por un admin o una empresa, o cuando se publica una versión nueva,
 * deben aceptar los términos vigentes antes de usar la app.
 */
@Component({
  selector: 'app-accept-terms',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <main class="page" style="max-width:520px">
      <span class="logo">Man<b>dao</b></span>
      <h1 class="mt">Términos y condiciones</h1>
      <p class="muted">Hola, {{ firstName() }}. Para usar Mandao necesitas aceptar los términos y condiciones
        (versión {{ version }}).</p>

      <div class="card stack">
        <strong>Lo más importante</strong>
        <ul class="summary">
          <li>Ves el precio antes de confirmar; las compras se pagan solo con factura y te devolvemos lo que sobre de la bolsa.</li>
          <li>Puedes cancelar sin costo mientras el mandado no haya empezado.</li>
          <li>Da el código de entrega solo cuando recibas conforme: equivale a aceptar la entrega.</li>
          <li>No se permiten mandados ilegales, peligrosos ni de transporte de dinero.</li>
          <li>Tus datos se tratan según la Ley 1581 de 2012.</li>
        </ul>
        <a routerLink="/terminos" target="_blank" rel="noopener">Leer los términos completos ↗</a>

        <label class="check">
          <input type="checkbox" name="accept" [(ngModel)]="accepted" />
          <span>Leí y acepto los <a routerLink="/terminos" target="_blank" rel="noopener">términos y condiciones</a> y
            la <a routerLink="/privacidad" target="_blank" rel="noopener">política de tratamiento de datos</a>.</span>
        </label>
        @if (error()) { <div class="alert danger">{{ error() }}</div> }
        <button class="btn block" [disabled]="!accepted || busy()" (click)="accept()">
          {{ busy() ? 'Guardando…' : 'Aceptar y continuar' }}
        </button>
      </div>
      <p class="center mt"><button class="btn link" (click)="auth.logout()">Ahora no, salir</button></p>
    </main>`,
  styles: [`.summary { padding-left: 20px; margin: 0; } .summary li { margin-bottom: 6px; font-size: 14px; }`],
})
export class AcceptTermsComponent {
  auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  readonly version = TERMS_VERSION;
  accepted = false;
  busy = signal(false);
  error = signal('');
  firstName = () => this.auth.user()?.fullName.split(' ')[0] ?? '';

  async accept() {
    this.busy.set(true);
    this.error.set('');
    try {
      await this.auth.acceptTerms(this.version);
      const back = this.route.snapshot.queryParamMap.get('volver');
      this.router.navigateByUrl(back && back.startsWith('/') && !back.startsWith('//') ? back : this.auth.homeFor());
    } catch (e: any) {
      // 409: el texto cambió mientras estaba abierto; recargar trae la versión vigente.
      if (e?.status === 409) { location.reload(); return; }
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}
