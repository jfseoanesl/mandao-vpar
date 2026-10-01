import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/labels';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <main class="page" style="max-width:440px">
      <a routerLink="/" class="logo">Man<b>dao</b></a>
      <h1 class="mt">Entrar</h1>
      <form class="card stack" (ngSubmit)="submit()">
        <label class="field">Celular
          <input name="phone" inputmode="numeric" autocomplete="tel" maxlength="10" placeholder="3001234567"
                 [(ngModel)]="phone" required />
        </label>
        <label class="field">Contraseña
          <input name="password" type="password" autocomplete="current-password" [(ngModel)]="password" required />
        </label>
        @if (error()) { <div class="alert danger">{{ error() }}</div> }
        <button class="btn block" [disabled]="busy()">{{ busy() ? 'Entrando…' : 'Entrar' }}</button>
      </form>
      <p class="center mt">
        <a [href]="forgotLink()" target="_blank" rel="noopener">¿Olvidaste tu contraseña?</a>
      </p>
      <p class="center">¿No tienes cuenta? <a routerLink="/registro">Regístrate</a></p>
    </main>`,
})
export class LoginComponent implements OnInit {
  private auth = inject(AuthService);
  private api = inject(ApiService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  phone = '';
  password = '';
  busy = signal(false);
  error = signal('');
  private whatsapp = signal('');

  ngOnInit() {
    this.api.publicConfig().subscribe({ next: c => this.whatsapp.set(c.whatsapp), error: () => {} });
  }

  /** Sin SMS en el piloto: soporte verifica la identidad por WhatsApp y envía una clave temporal. */
  forgotLink() {
    const text = `Hola Mandao, olvidé mi contraseña. Mi celular registrado es ${this.phone.trim() || '(escríbelo aquí)'}.`;
    return `https://wa.me/${this.whatsapp()}?text=${encodeURIComponent(text)}`;
  }

  async submit() {
    this.busy.set(true);
    this.error.set('');
    try {
      await this.auth.login(this.phone.trim(), this.password);
      const back = this.route.snapshot.queryParamMap.get('volver');
      this.router.navigateByUrl(back && back.startsWith('/') && !back.startsWith('//') ? back : this.auth.homeFor());
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}
