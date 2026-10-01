import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { PAYMENT, dateTime, money } from '../../core/labels';
import { PaymentView } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { StatusChipComponent } from '../../shared/ui';

/** Pagos manuales (spec 007): confirmar recaudo y registrar liquidación al mandadero. */
@Component({
  selector: 'app-payments',
  standalone: true,
  imports: [FormsModule, RouterLink, StatusChipComponent],
  template: `
    <main class="page wide">
      <div class="row between wrap">
        <h1>Pagos</h1>
        <select name="status" [(ngModel)]="status" (ngModelChange)="load()">
          <option value="PENDING">Por confirmar</option>
          <option value="CONFIRMED">Confirmados</option>
          <option value="VOIDED">Anulados</option>
          <option value="">Todos</option>
        </select>
      </div>
      <div class="card table-wrap mt">
        <table class="table">
          <thead><tr><th>Mandado</th><th>Cliente</th><th>Método</th><th>Servicio</th><th>Compras</th><th>Devolver</th>
            <th>Mandadero</th><th>Estado</th><th></th></tr></thead>
          <tbody>
            @for (p of items(); track p.id) {
              <tr>
                <td><a [routerLink]="['/admin/mandados', p.errandId]">{{ p.errandCode }}</a><div><app-status [status]="p.errandStatus" /></div></td>
                <td>{{ p.clientName }}</td>
                <td>{{ method(p) }}@if (p.reference) { <div><small class="muted">Ref. {{ p.reference }}</small></div> }</td>
                <td class="mono">{{ money(p.amount) }}</td>
                <td class="mono">{{ money(p.purchases) }}</td>
                <td class="mono">{{ money(p.refund) }}</td>
                <td>{{ p.courierName ?? '—' }}<div><small class="mono">{{ money(p.courierPayout) }} {{ p.courierPaidAt ? '✅' : '' }}</small></div></td>
                <td><span class="chip {{ p.status === 'CONFIRMED' ? 'ok' : p.status === 'VOIDED' ? 'neutral' : 'warn' }}">{{ p.status }}</span>
                  @if (p.confirmedAt) { <div><small class="muted">{{ dt(p.confirmedAt) }}</small></div> }</td>
                <td>
                  @if (p.status === 'PENDING') {
                    <button class="btn ok sm" [disabled]="busy()" (click)="confirm(p)">Confirmar</button>
                  }
                </td>
              </tr>
            } @empty { <tr><td colspan="9" class="muted">Sin pagos</td></tr> }
          </tbody>
        </table>
      </div>
    </main>`,
})
export class PaymentsComponent implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  items = signal<PaymentView[]>([]);
  busy = signal(false);
  status = 'PENDING';
  money = money;
  dt = dateTime;
  method = (p: PaymentView) => PAYMENT[p.method];

  ngOnInit() { this.load(); }

  load() {
    this.api.payments(this.status || undefined).subscribe({ next: l => this.items.set(l), error: e => this.toast.error(e) });
  }

  confirm(p: PaymentView) {
    const reference = prompt(`Referencia del recaudo (${this.method(p)}), p. ej. número de Nequi o "efectivo"`)?.trim();
    if (!reference) return;
    this.busy.set(true);
    this.api.confirmPayment(p.id, reference).subscribe({
      next: () => { this.busy.set(false); this.toast.ok('Pago confirmado'); this.load(); },
      error: e => { this.busy.set(false); this.toast.error(e); },
    });
  }
}
