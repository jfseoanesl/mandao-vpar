import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { RealtimeService } from '../../core/realtime.service';
import { ToastService } from '../../core/toast.service';
import { PushPromptComponent } from '../../shared/push-prompt.component';

/** Panel de operación del piloto. Escucha /topic/admin para alertas de asignación. */
@Component({
  selector: 'app-admin-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, PushPromptComponent],
  template: `
    <header class="topbar">
      <div class="inner">
        <a routerLink="/admin" class="logo">Man<b>dao</b> <small class="muted" style="margin-left:6px;font-weight:600">admin</small></a>
        <nav class="row wrap grow admin-nav">
          <a routerLink="/admin" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">KPIs</a>
          <a routerLink="/admin/en-vivo" routerLinkActive="active">En vivo</a>
          <a routerLink="/admin/mandados" routerLinkActive="active">
            Mandados @if (alerts()) { <span class="chip danger">⚠ {{ alerts() }}</span> }
          </a>
          <a routerLink="/admin/mandaderos" routerLinkActive="active">Mandaderos</a>
          <a routerLink="/admin/pagos" routerLinkActive="active">Pagos</a>
          <a routerLink="/admin/tarifas" routerLinkActive="active">Tarifas</a>
          <a routerLink="/admin/empresas" routerLinkActive="active">Empresas</a>
          <a routerLink="/admin/usuarios" routerLinkActive="active">Usuarios</a>
        </nav>
        <span class="spacer"></span>
        <a class="btn ghost sm" routerLink="/cambiar-clave" title="Cambiar contraseña" aria-label="Cambiar contraseña">🔑</a>
        <button class="btn ghost sm" (click)="auth.logout()">Salir</button>
      </div>
    </header>
    <div class="page wide" style="padding-bottom:0">
      <app-push-prompt text="Activa las notificaciones para enterarte de los mandados que requieren asignación manual." />
    </div>
    <router-outlet />`,
  styles: [`
    .admin-nav { gap: 4px 14px; font-size: 14px; }
    /* En celular el menú va en su propia fila y se desliza horizontalmente. */
    @media (max-width: 760px) {
      .topbar .inner { flex-wrap: wrap; }
      .admin-nav { order: 3; flex: 1 0 100%; flex-wrap: nowrap; overflow-x: auto; white-space: nowrap; padding-bottom: 4px; }
      .spacer { flex: 1; }
    }
    .admin-nav a { color: var(--ink-2); padding: 4px 0; }
    .admin-nav a.active { color: var(--brand-600); font-weight: 700; }
  `],
})
export class AdminShellComponent implements OnInit {
  auth = inject(AuthService);
  private rt = inject(RealtimeService);
  private toast = inject(ToastService);
  private destroy = inject(DestroyRef);
  alerts = signal(0);

  ngOnInit() {
    this.rt.watch('/topic/admin').pipe(takeUntilDestroyed(this.destroy)).subscribe(ev => {
      if (ev.type === 'NEEDS_ATTENTION') {
        this.alerts.update(n => n + 1);
        this.toast.show(`⚠ ${ev.data?.['code'] ?? 'Mandado'} necesita asignación manual`, 'error', 8000);
      } else if (ev.type === 'ERRAND_CREATED') {
        this.toast.show(`Nuevo mandado ${ev.data?.['code'] ?? ''}`);
      }
    });
  }
}
