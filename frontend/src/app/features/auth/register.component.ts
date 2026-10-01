import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AnalyticsService } from '../../core/analytics.service';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/labels';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <main class="page" style="max-width:460px">
      <a routerLink="/" class="logo">Man<b>dao</b></a>
      <h1 class="mt">Crea tu cuenta</h1>
      <div class="row" style="gap:8px">
        <button type="button" class="chip select" [class.on]="role === 'CLIENT'" (click)="role = 'CLIENT'">Quiero pedir mandados</button>
        <button type="button" class="chip select" [class.on]="role === 'COURIER'" (click)="role = 'COURIER'">Quiero ser mandadero</button>
      </div>
      <form class="card stack mt" (ngSubmit)="submit()">
        <label class="field">Nombre completo
          <input name="fullName" autocomplete="name" [(ngModel)]="fullName" required minlength="3" />
        </label>
        <label class="field">Celular
          <span class="hint">10 dígitos. Lo usaremos para coordinar tus mandados.</span>
          <input name="phone" inputmode="numeric" autocomplete="tel" maxlength="10" placeholder="3001234567" [(ngModel)]="phone" required />
        </label>
        <label class="field">Correo (opcional)
          <input name="email" type="email" autocomplete="email" [(ngModel)]="email" />
        </label>
        <label class="field">Contraseña
          <span class="hint">Mínimo 8 caracteres, con letras y números.</span>
          <input name="password" type="password" autocomplete="new-password" [(ngModel)]="password" required />
        </label>
        <label class="check">
          <input type="checkbox" name="privacy" [(ngModel)]="acceptPrivacy" />
          <span>Acepto los <a routerLink="/terminos" target="_blank">términos y condiciones</a> y autorizo el tratamiento
            de mis datos personales según la <a routerLink="/privacidad" target="_blank">política de Mandao</a>
            (Ley 1581 de 2012).</span>
        </label>
        @if (error()) { <div class="alert danger">{{ error() }}</div> }
        <button class="btn block" [disabled]="busy() || !acceptPrivacy">{{ busy() ? 'Creando…' : 'Crear cuenta' }}</button>
      </form>
      <p class="center mt">¿Ya tienes cuenta? <a routerLink="/entrar">Entra</a></p>
    </main>`,
})
export class RegisterComponent {
  private auth = inject(AuthService);
  private router = inject(Router);
  private analytics = inject(AnalyticsService);
  role: 'CLIENT' | 'COURIER' = inject(ActivatedRoute).snapshot.queryParamMap.get('rol') === 'mandadero' ? 'COURIER' : 'CLIENT';
  fullName = '';
  phone = '';
  email = '';
  password = '';
  acceptPrivacy = false;
  busy = signal(false);
  error = signal('');

  async submit() {
    this.busy.set(true);
    this.error.set('');
    try {
      await this.auth.register({
        fullName: this.fullName.trim(), phone: this.phone.trim(), email: this.email.trim() || undefined,
        password: this.password, role: this.role, acceptPrivacy: this.acceptPrivacy, utmSource: this.analytics.source(),
      });
      this.router.navigateByUrl(this.role === 'COURIER' ? '/mandadero/perfil' : '/app');
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}
