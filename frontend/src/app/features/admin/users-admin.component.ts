import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { dateTime } from '../../core/labels';
import { AdminUserView } from '../../core/models';
import { ToastService } from '../../core/toast.service';

const ROLES: Record<string, string> = { CLIENT: 'Cliente', COURIER: 'Mandadero', ADMIN: 'Admin', COMPANY_ADMIN: 'Empresa' };

/** Usuarios: búsqueda, canal de adquisición y activación/bloqueo. */
@Component({
  selector: 'app-users-admin',
  standalone: true,
  imports: [FormsModule],
  template: `
    <main class="page wide">
      <div class="row between wrap">
        <h1>Usuarios ({{ filtered().length }})</h1>
        <input name="q" placeholder="Buscar por nombre o celular" [ngModel]="q()" (ngModelChange)="q.set($event)" />
      </div>
      @if (temp(); as t) {
        <div class="card mt stack" style="border-color:var(--brand)">
          <strong>Contraseña temporal de {{ t.name }}</strong>
          <div class="otp-box"><div class="code" style="letter-spacing:3px">{{ t.password }}</div></div>
          <small class="muted">Se muestra solo esta vez. Envíala únicamente al celular registrado ({{ t.phone }}); al entrar deberá cambiarla.</small>
          <div class="row wrap">
            <a class="btn ok sm" [href]="whatsapp(t)" target="_blank" rel="noopener">Enviar por WhatsApp</a>
            <button class="btn ghost sm" (click)="copy(t.password)">Copiar</button>
            <button class="btn ghost sm" (click)="temp.set(null)">Cerrar</button>
          </div>
        </div>
      }
      <div class="card table-wrap mt">
        <table class="table">
          <thead><tr><th>Nombre</th><th>Celular</th><th>Rol</th><th>Canal</th><th>Registro</th><th>Estado</th></tr></thead>
          <tbody>
            @for (u of filtered(); track u.user.id) {
              <tr>
                <td>{{ u.user.fullName }}</td>
                <td class="mono">{{ u.user.phone }}</td>
                <td>{{ role(u) }}</td>
                <td>{{ u.acquisitionSource ?? 'Directo' }}</td>
                <td><small>{{ dt(u.createdAt) }}</small></td>
                <td class="row" style="gap:6px">
                  @if (u.user.role !== 'ADMIN') {
                    <button class="btn sm {{ u.active ? 'ghost' : 'ok' }}" (click)="toggle(u)">{{ u.active ? 'Bloquear' : 'Activar' }}</button>
                    <button class="btn ghost sm" (click)="reset(u)">Restablecer clave</button>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </main>`,
})
export class UsersAdminComponent implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  items = signal<AdminUserView[]>([]);
  q = signal('');
  temp = signal<{ name: string; phone: string; password: string } | null>(null);
  dt = dateTime;
  role = (u: AdminUserView) => ROLES[u.user.role] ?? u.user.role;
  filtered = computed(() => {
    const q = this.q().trim().toLowerCase();
    return q ? this.items().filter(u => u.user.fullName.toLowerCase().includes(q) || u.user.phone.includes(q)) : this.items();
  });

  ngOnInit() {
    this.api.users().subscribe({ next: l => this.items.set(l), error: e => this.toast.error(e) });
  }

  /** AC-001-11: verifica la identidad por WhatsApp antes de restablecer. */
  reset(u: AdminUserView) {
    if (!confirm(`¿Restablecer la contraseña de ${u.user.fullName}? Se cerrarán sus sesiones.`)) return;
    this.api.resetPassword(u.user.id).subscribe({
      next: r => this.temp.set({ name: u.user.fullName, phone: u.user.phone, password: r.temporaryPassword }),
      error: e => this.toast.error(e),
    });
  }

  whatsapp(t: { name: string; phone: string; password: string }) {
    const text = `Hola ${t.name.split(' ')[0]}, tu contraseña temporal de Mandao es: ${t.password}
Al entrar te pediremos crear una nueva.`;
    return `https://wa.me/57${t.phone}?text=${encodeURIComponent(text)}`;
  }

  copy(text: string) {
    navigator.clipboard?.writeText(text).then(() => this.toast.ok('Copiada'), () => this.toast.error('No se pudo copiar'));
  }

  toggle(u: AdminUserView) {
    if (u.active && !confirm(`¿Bloquear a ${u.user.fullName}? No podrá entrar a la app.`)) return;
    this.api.setUserActive(u.user.id, !u.active).subscribe({
      next: r => this.items.update(l => l.map(x => (x.user.id === r.user.id ? r : x))),
      error: e => this.toast.error(e),
    });
  }
}
