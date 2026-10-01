import { Location } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/labels';
import { ToastService } from '../../core/toast.service';

/** Cambio de contraseña (AC-001-10); obligatorio tras un restablecimiento del admin (AC-001-11). */
@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [FormsModule],
  template: `
    <main class="page" style="max-width:440px">
      <span class="logo">Man<b>dao</b></span>
      <h1 class="mt">Cambiar contraseña</h1>
      @if (forced) {
        <div class="alert info">Entraste con una contraseña temporal. Crea una nueva para continuar.</div>
      }
      <form class="card stack mt" (ngSubmit)="submit()">
        <label class="field">{{ forced ? 'Contraseña temporal' : 'Contraseña actual' }}
          <input name="current" type="password" autocomplete="current-password" [(ngModel)]="current" required />
        </label>
        <label class="field">Nueva contraseña
          <span class="hint">Mínimo 8 caracteres, con letras y números.</span>
          <input name="next" type="password" autocomplete="new-password" [(ngModel)]="next" required />
        </label>
        <label class="field">Repite la nueva contraseña
          <input name="repeat" type="password" autocomplete="new-password" [(ngModel)]="repeat" required />
        </label>
        @if (error()) { <div class="alert danger">{{ error() }}</div> }
        <button class="btn block" [disabled]="busy()">{{ busy() ? 'Guardando…' : 'Guardar contraseña' }}</button>
        <small class="muted">Por seguridad, se cerrará tu sesión en los demás dispositivos.</small>
      </form>
      @if (!forced) { <p class="center mt"><button class="btn link" (click)="back()">Cancelar</button></p> }
      @else { <p class="center mt"><button class="btn link" (click)="auth.logout()">Salir</button></p> }
    </main>`,
})
export class ChangePasswordComponent {
  auth = inject(AuthService);
  private router = inject(Router);
  private location = inject(Location);
  private toast = inject(ToastService);
  readonly forced = !!this.auth.user()?.mustChangePassword;
  current = '';
  next = '';
  repeat = '';
  busy = signal(false);
  error = signal('');

  async submit() {
    this.error.set('');
    if (this.next !== this.repeat) { this.error.set('Las contraseñas nuevas no coinciden.'); return; }
    if (!/^(?=.*[A-Za-z])(?=.*\d).{8,72}$/.test(this.next)) { this.error.set('Mínimo 8 caracteres, con letras y números.'); return; }
    this.busy.set(true);
    try {
      await this.auth.changePassword(this.current, this.next);
      this.toast.ok('Contraseña actualizada');
      this.router.navigateByUrl(this.auth.homeFor());
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }

  back() { this.location.back(); }
}
