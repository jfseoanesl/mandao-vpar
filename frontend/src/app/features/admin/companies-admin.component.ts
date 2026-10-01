import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { dateTime, money } from '../../core/labels';
import { CompanyView } from '../../core/models';
import { ToastService } from '../../core/toast.service';

/** Empresas del piloto B2B (spec 011): plan registrado manualmente, sin cobro automático. */
@Component({
  selector: 'app-companies-admin',
  standalone: true,
  imports: [FormsModule],
  template: `
    <main class="page wide">
      <h1>Empresas</h1>
      <div class="grid two">
        <div class="card table-wrap">
          <table class="table">
            <thead><tr><th>Empresa</th><th>Plan</th><th>Usuarios</th><th>Desde</th></tr></thead>
            <tbody>
              @for (c of items(); track c.id) {
                <tr>
                  <td><strong>{{ c.name }}</strong>@if (c.nit) { <div><small class="muted">NIT {{ c.nit }}</small></div> }</td>
                  <td>{{ c.plan }}<div><small class="mono">{{ money(c.monthlyFee) }}/mes</small></div></td>
                  <td class="mono">{{ c.members }}</td>
                  <td><small>{{ dt(c.createdAt) }}</small></td>
                </tr>
              } @empty { <tr><td colspan="4" class="muted">Aún no hay empresas</td></tr> }
            </tbody>
          </table>
        </div>
        <form class="card stack" (ngSubmit)="create()">
          <h2>Nueva empresa</h2>
          <input name="name" placeholder="Razón social" [(ngModel)]="f.name" required />
          <input name="nit" placeholder="NIT (opcional)" [(ngModel)]="f.nit" />
          <select name="plan" [(ngModel)]="f.plan">
            <option value="BASICO">Básico (sin costo)</option>
            <option value="PYME">Pyme ($79.000/mes)</option>
            <option value="EMPRESAS">Empresas ($99.000/mes)</option>
            <option value="PRO">Pro ($199.000/mes)</option>
          </select>
          <strong class="mt">Administrador de la cuenta</strong>
          <input name="adminFullName" placeholder="Nombre completo" [(ngModel)]="f.adminFullName" required />
          <input name="adminPhone" placeholder="Celular" inputmode="numeric" maxlength="10" [(ngModel)]="f.adminPhone" required />
          <input name="adminPassword" type="password" autocomplete="new-password" placeholder="Contraseña inicial" [(ngModel)]="f.adminPassword" required />
          <button class="btn" [disabled]="busy()">Crear empresa</button>
        </form>
      </div>
    </main>`,
})
export class CompaniesAdminComponent implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  items = signal<CompanyView[]>([]);
  busy = signal(false);
  f = blank();
  money = money;
  dt = dateTime;

  ngOnInit() { this.load(); }

  load() {
    this.api.companies().subscribe({ next: l => this.items.set(l), error: e => this.toast.error(e) });
  }

  create() {
    this.busy.set(true);
    this.api.createCompany({ ...this.f, nit: this.f.nit.trim() || undefined }).subscribe({
      next: c => { this.busy.set(false); this.toast.ok(`${c.name} creada`); this.f = blank(); this.load(); },
      error: e => { this.busy.set(false); this.toast.error(e); },
    });
  }
}

function blank() {
  return { name: '', nit: '', plan: 'EMPRESAS', adminFullName: '', adminPhone: '', adminPassword: '' };
}
