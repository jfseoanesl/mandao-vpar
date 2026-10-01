import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { STATUS, categoryInfo, dateTime, money } from '../../core/labels';
import { ErrandStatus, ErrandSummary } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { StatusChipComponent } from '../../shared/ui';

/** Gestión de mandados con filtro por estado; los que requieren atención van resaltados. */
@Component({
  selector: 'app-admin-errands',
  standalone: true,
  imports: [FormsModule, StatusChipComponent],
  template: `
    <main class="page wide">
      <div class="row between wrap">
        <h1>Mandados</h1>
        <div class="row">
          <select name="status" [(ngModel)]="status" (ngModelChange)="load()">
            <option value="">Todos</option>
            @for (s of statuses; track s) { <option [value]="s">{{ labels[s].label }}</option> }
          </select>
          <button class="btn ghost sm" (click)="load()">Actualizar</button>
        </div>
      </div>
      <div class="card table-wrap mt">
        <table class="table">
          <thead><tr><th>Código</th><th>Categoría</th><th>Cliente</th><th>Mandadero</th><th>Estado</th><th>Total</th><th>Creado</th></tr></thead>
          <tbody>
            @for (e of items(); track e.id) {
              <tr class="clickable" [class.attention]="e.needsAttention" (click)="open(e)">
                <td><strong>{{ e.code }}</strong>@if (e.needsAttention) { <div><small class="error-text">⚠ {{ e.attentionReason }}</small></div> }</td>
                <td>{{ cat(e) }}<div><small class="muted">{{ e.description }}</small></div></td>
                <td>{{ e.clientName }}</td>
                <td>{{ e.courierName ?? '—' }}</td>
                <td><app-status [status]="e.status" /></td>
                <td class="mono">{{ money(e.totalPrice) }}</td>
                <td><small>{{ dt(e.createdAt) }}</small></td>
              </tr>
            } @empty { <tr><td colspan="7" class="muted">Sin mandados</td></tr> }
          </tbody>
        </table>
      </div>
    </main>`,
})
export class AdminErrandsComponent implements OnInit {
  private api = inject(ApiService);
  private router = inject(Router);
  private toast = inject(ToastService);
  items = signal<ErrandSummary[]>([]);
  status: ErrandStatus | '' = '';
  statuses = Object.keys(STATUS) as ErrandStatus[];
  labels = STATUS;
  money = money;
  dt = dateTime;
  cat = (e: ErrandSummary) => `${categoryInfo(e.category).emoji} ${categoryInfo(e.category).title}`;

  ngOnInit() { this.load(); }

  load() {
    this.api.adminErrands(this.status).subscribe({
      // Los que requieren atención primero.
      next: l => this.items.set([...l].sort((a, b) => Number(b.needsAttention) - Number(a.needsAttention))),
      error: e => this.toast.error(e),
    });
  }

  open(e: ErrandSummary) { this.router.navigate(['/admin/mandados', e.id]); }
}
