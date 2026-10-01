import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { LocationTrackerService } from './location-tracker.service';

@Component({
  selector: 'app-courier-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="topbar">
      <div class="inner">
        <a routerLink="/mandadero" class="logo">Man<b>dao</b> <small class="muted" style="margin-left:6px;font-weight:600">mandadero</small></a>
        <span class="grow"></span>
        @if (gps.running()) { <span class="chip ok" title="GPS activo">📡<span class="hide-sm"> GPS activo</span></span> }
        <a class="btn ghost sm" routerLink="/cambiar-clave" title="Cambiar contraseña" aria-label="Cambiar contraseña">🔑</a>
        <button class="btn ghost sm" (click)="logout()">Salir</button>
      </div>
    </header>
    <router-outlet />
    <nav class="bottomnav">
      <a routerLink="/mandadero" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }"><span class="ico">🛵</span>Mandados</a>
      <a routerLink="/mandadero/ganancias" routerLinkActive="active"><span class="ico">💰</span>Ganancias</a>
      <a routerLink="/mandadero/perfil" routerLinkActive="active"><span class="ico">👤</span>Perfil</a>
    </nav>`,
})
export class CourierShellComponent {
  auth = inject(AuthService);
  gps = inject(LocationTrackerService);

  logout() {
    this.gps.stop();
    void this.auth.logout();
  }
}
