import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-client-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="topbar">
      <div class="inner">
        <a routerLink="/app" class="logo">Man<b>dao</b></a>
        <span class="grow"></span>
        <small class="muted">Hola, {{ firstName() }}</small>
        <a class="btn ghost sm" routerLink="/cambiar-clave" title="Cambiar contraseña" aria-label="Cambiar contraseña">🔑</a>
        <button class="btn ghost sm" (click)="auth.logout()">Salir</button>
      </div>
    </header>
    <router-outlet />
    <nav class="bottomnav">
      <a routerLink="/app" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }"><span class="ico">🏠</span>Inicio</a>
      <a routerLink="/app/mandados" routerLinkActive="active"><span class="ico">📋</span>Mis mandados</a>
      @if (auth.user()?.role === 'COMPANY_ADMIN') {
        <a routerLink="/app/empresa" routerLinkActive="active"><span class="ico">🏢</span>Empresa</a>
      }
    </nav>`,
})
export class ClientShellComponent {
  auth = inject(AuthService);
  firstName = () => this.auth.user()?.fullName.split(' ')[0] ?? '';
}
