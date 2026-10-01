import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { DOCUMENTS, VEHICLES, dateTime } from '../../core/labels';
import { CourierView } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { SecureImgComponent } from '../../shared/secure-img.component';

/** Verificación de mandaderos (spec 009): revisar documentos y aprobar/rechazar. */
@Component({
  selector: 'app-couriers-admin',
  standalone: true,
  imports: [FormsModule, SecureImgComponent],
  template: `
    <main class="page wide">
      <div class="row between wrap">
        <h1>Mandaderos</h1>
        <select name="status" [(ngModel)]="status" (ngModelChange)="load()">
          <option value="PENDING">En revisión</option>
          <option value="APPROVED">Aprobados</option>
          <option value="REJECTED">Rechazados</option>
          <option value="">Todos</option>
        </select>
      </div>
      <div class="stack mt">
        @for (c of items(); track c.userId) {
          <div class="card stack">
            <div class="row between wrap">
              <div>
                <strong>{{ c.fullName }}</strong> <small class="muted">· {{ c.phone }}</small>
                <div><small class="muted">{{ vehicle(c) }} · Pago: {{ c.payoutMethod ?? '—' }} {{ c.payoutAccount ?? '' }}</small></div>
                @if (c.rejectionReason) { <div><small class="error-text">Rechazo: {{ c.rejectionReason }}</small></div> }
              </div>
              <span class="chip {{ c.status === 'APPROVED' ? 'ok' : c.status === 'REJECTED' ? 'danger' : 'info' }}">{{ c.status }}</span>
            </div>
            @if (c.missingDocuments.length) {
              <div class="alert warn">Faltan: {{ missing(c) }}</div>
            }
            @if (open() === c.userId) {
              <div class="thumbs">
                @for (d of c.documents; track d.id) {
                  <div><app-secure-img [fileId]="d.id" source="document" [alt]="docs[d.type]" /><small>{{ docs[d.type] }}</small></div>
                }
              </div>
            }
            <div class="row wrap">
              <button class="btn ghost sm" (click)="open.set(open() === c.userId ? null : c.userId)">
                {{ open() === c.userId ? 'Ocultar' : 'Ver' }} documentos ({{ c.documents.length }})</button>
              @if (c.status !== 'APPROVED') {
                <button class="btn ok sm" [disabled]="busy() || c.missingDocuments.length > 0" (click)="approve(c)">Aprobar</button>
              }
              @if (c.status !== 'REJECTED') {
                <button class="btn danger sm" [disabled]="busy()" (click)="reject(c)">Rechazar</button>
              }
            </div>
            @if (c.approvedAt) { <small class="muted">Aprobado {{ dt(c.approvedAt) }}</small> }
          </div>
        } @empty { <p class="muted">No hay mandaderos en este estado.</p> }
      </div>
    </main>`,
})
export class CouriersAdminComponent implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  items = signal<CourierView[]>([]);
  open = signal<string | null>(null);
  busy = signal(false);
  status = 'PENDING';
  docs = DOCUMENTS;
  dt = dateTime;
  vehicle = (c: CourierView) => (c.vehicleType ? VEHICLES[c.vehicleType] : 'Sin vehículo') + (c.plate ? ` · ${c.plate}` : '');
  missing = (c: CourierView) => c.missingDocuments.map(d => DOCUMENTS[d]).join(', ');

  ngOnInit() { this.load(); }

  load() {
    this.api.couriers(this.status || undefined).subscribe({ next: l => this.items.set(l), error: e => this.toast.error(e) });
  }

  approve(c: CourierView) {
    this.busy.set(true);
    this.api.approveCourier(c.userId).subscribe({
      next: () => { this.busy.set(false); this.toast.ok(`${c.fullName} aprobado`); this.load(); },
      error: e => { this.busy.set(false); this.toast.error(e); },
    });
  }

  reject(c: CourierView) {
    const reason = prompt('Motivo del rechazo (lo verá el mandadero)')?.trim();
    if (!reason) return;
    this.busy.set(true);
    this.api.rejectCourier(c.userId, reason).subscribe({
      next: () => { this.busy.set(false); this.load(); },
      error: e => { this.busy.set(false); this.toast.error(e); },
    });
  }
}
