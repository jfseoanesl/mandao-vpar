import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { COMPLEXITY, PAYMENT, categoryInfo, dateTime, money } from '../../core/labels';
import { CourierView, ErrandView } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { EvidenceListComponent } from '../../shared/evidence-list.component';
import { MapComponent, MapMarker } from '../../shared/map.component';
import { StatusChipComponent, TimelineComponent } from '../../shared/ui';

const ASSIGNABLE = ['SEARCHING', 'NEGOTIATING'];
const ADMIN_CANCELLABLE = ['SEARCHING', 'NEGOTIATING', 'ASSIGNED', 'GOING_TO_ORIGIN', 'IN_PROGRESS'];

/** Detalle de operación: asignación manual (fallback de 5 min), cancelar, ajustar precio, liquidar. */
@Component({
  selector: 'app-admin-errand-detail',
  standalone: true,
  imports: [FormsModule, RouterLink, MapComponent, StatusChipComponent, TimelineComponent, EvidenceListComponent],
  template: `
    <main class="page wide">
      <a routerLink="/admin/mandados" class="btn link">‹ Mandados</a>
      @if (e(); as e) {
        <div class="row between mt">
          <h1>{{ cat().emoji }} {{ e.code }}</h1>
          <app-status [status]="e.status" />
        </div>
        @if (e.needsAttention) {
          <div class="alert warn row between">⚠ {{ e.attentionReason }}
            <button class="btn sm" [disabled]="busy()" (click)="act(api.resolve(e.id), 'Marcado como resuelto')">Marcar resuelto</button></div>
        }
        <p>{{ e.description }}</p>

        <div class="grid two">
          <div class="stack">
            <app-map [markers]="markers()" [path]="route()" />
            <div class="card">
              @for (s of e.stops; track s.seq) {
                <div class="kv"><span>{{ s.seq + 1 }}. {{ s.address }}</span><span>{{ s.contactPhone ?? '' }}</span></div>
              }
            </div>
            <div class="card"><h3>Evidencias</h3><app-evidence-list [items]="e.evidences" /></div>
          </div>

          <div class="stack">
            <div class="card">
              <div class="kv"><span>Cliente</span><span>{{ e.client?.fullName }} · {{ e.client?.phone }}</span></div>
              <div class="kv"><span>Mandadero</span><span>{{ e.courier?.fullName ?? '—' }} {{ e.courier?.phone ?? '' }}</span></div>
              <div class="kv"><span>Complejidad</span><span>{{ complexity(e) }}</span></div>
              <div class="kv"><span>Distancia</span><span>{{ e.distanceKm }} km</span></div>
              <div class="kv"><span>Cotizado</span><span>{{ money(e.quotedPrice) }}</span></div>
              <div class="kv"><span>Total servicio</span><span>{{ money(e.totalPrice) }}</span></div>
              <div class="kv"><span>Plataforma</span><span>{{ money(e.platformFee) }}</span></div>
              <div class="kv"><span>Pago mandadero</span><span>{{ money(e.courierPayout) }} {{ e.courierPaidAt ? '✅' : '' }}</span></div>
              @if (e.purchaseBudget > 0) {
                <div class="kv"><span>Bolsa / gastado / devolver</span>
                  <span>{{ money(e.purchaseBudget) }} / {{ money(e.purchasesTotal) }} / {{ money(e.refundTotal) }}</span></div>
              }
              <div class="kv"><span>Pago cliente</span><span>{{ payment(e) }} · {{ e.paymentStatus ?? 'PENDING' }}</span></div>
              @if (e.otpFailedAttempts) { <div class="kv"><span>Intentos OTP fallidos</span><span>{{ e.otpFailedAttempts }}</span></div> }
              <div class="kv"><span>Creado</span><span>{{ dt(e.createdAt) }}</span></div>
              @if (e.assignedAt) { <div class="kv"><span>Asignado</span><span>{{ dt(e.assignedAt) }}</span></div> }
            </div>

            @if (canAssign()) {
              <div class="card stack">
                <h3>Asignación manual</h3>
                <select name="courier" [(ngModel)]="courierId">
                  <option value="">Elige un mandadero…</option>
                  @for (c of couriers(); track c.userId) {
                    <option [value]="c.userId">{{ c.available ? '🟢' : '⚪' }} {{ c.fullName }} · {{ c.phone }}</option>
                  }
                </select>
                <button class="btn" [disabled]="!courierId || busy()" (click)="act(api.assign(e.id, courierId), 'Mandadero asignado')">Asignar</button>
              </div>
            }

            @if (!done()) {
              <div class="card stack">
                <h3>Ajustar precio</h3>
                <div class="row">
                  <input class="grow" type="number" min="1000" step="100" name="price" [(ngModel)]="price" />
                  <button class="btn ghost sm" [disabled]="!price || busy()" (click)="act(api.adjustPrice(e.id, +price!), 'Precio ajustado')">Guardar</button>
                </div>
              </div>
            }

            <div class="row wrap">
              @if (canCancel()) {
                <button class="btn danger" [disabled]="busy()" (click)="cancel()">Cancelar mandado</button>
              }
              @if (done() && !e.courierPaidAt) {
                <button class="btn ok" [disabled]="busy()" (click)="act(api.courierPaid(e.id), 'Pago al mandadero registrado')">Registrar pago al mandadero</button>
              }
              <a class="btn ghost" [routerLink]="['/comprobante', e.id]">🧾 Comprobante</a>
            </div>

            <div class="card"><h3>Auditoría</h3><app-timeline [items]="e.history" /></div>
          </div>
        </div>
      } @else { <div class="spinner mt"></div> }
    </main>`,
})
export class AdminErrandDetailComponent implements OnInit {
  api = inject(ApiService);
  private toast = inject(ToastService);
  private id = inject(ActivatedRoute).snapshot.paramMap.get('id')!;
  e = signal<ErrandView | null>(null);
  couriers = signal<CourierView[]>([]);
  busy = signal(false);
  courierId = '';
  price: number | null = null;
  money = money;
  dt = dateTime;

  cat = computed(() => categoryInfo(this.e()!.category));
  canAssign = computed(() => ASSIGNABLE.includes(this.e()?.status ?? ''));
  canCancel = computed(() => ADMIN_CANCELLABLE.includes(this.e()?.status ?? ''));
  done = computed(() => ['COMPLETED', 'RATED'].includes(this.e()?.status ?? ''));
  route = computed(() => (this.e()?.stops ?? []).map(s => ({ lat: s.lat, lng: s.lng })));
  markers = computed<MapMarker[]>(() => {
    const e = this.e();
    if (!e) return [];
    const stops: MapMarker[] = e.stops.map(s => ({ lat: s.lat, lng: s.lng, label: String(s.seq + 1), title: s.address }));
    const c = e.courierLocation;
    return c ? [...stops, { lat: c.lat, lng: c.lng, label: '🛵', kind: 'courier', title: e.courier?.fullName }] : stops;
  });
  complexity = (e: ErrandView) => COMPLEXITY[e.complexity];
  payment = (e: ErrandView) => PAYMENT[e.paymentMethod];

  ngOnInit() {
    this.load();
    this.api.couriers('APPROVED').subscribe({
      next: l => this.couriers.set([...l].sort((a, b) => Number(b.available) - Number(a.available))),
      error: () => {},
    });
  }

  load() {
    this.api.errand(this.id).subscribe({
      next: e => { this.e.set(e); this.price = e.totalPrice; },
      error: err => this.toast.error(err),
    });
  }

  act(req: Observable<unknown>, ok: string) {
    this.busy.set(true);
    req.subscribe({
      next: () => { this.busy.set(false); this.toast.ok(ok); this.load(); },
      error: err => { this.busy.set(false); this.toast.error(err); },
    });
  }

  cancel() {
    const reason = prompt('Motivo de la cancelación (obligatorio)')?.trim();
    if (reason) this.act(this.api.adminCancel(this.id, reason), 'Mandado cancelado');
  }
}
