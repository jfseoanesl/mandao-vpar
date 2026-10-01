import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { COMPLEXITY, PAYMENT, VEHICLES, categoryInfo, dateTime, money } from '../../core/labels';
import { ErrandStatus, ErrandView, LivePoint } from '../../core/models';
import { RealtimeService } from '../../core/realtime.service';
import { ToastService } from '../../core/toast.service';
import { EvidenceListComponent } from '../../shared/evidence-list.component';
import { MapComponent, MapMarker } from '../../shared/map.component';
import { PushPromptComponent } from '../../shared/push-prompt.component';
import { ReputationComponent, StatusChipComponent, TimelineComponent } from '../../shared/ui';
import { OffersPanelComponent } from './offers-panel.component';
import { RatingPanelComponent } from './rating-panel.component';

const CANCELLABLE: ErrandStatus[] = ['SEARCHING', 'NEGOTIATING', 'ASSIGNED', 'GOING_TO_ORIGIN'];
const TRACKABLE: ErrandStatus[] = ['ASSIGNED', 'GOING_TO_ORIGIN', 'IN_PROGRESS', 'DELIVERED'];

/** Seguimiento del mandado: mapa en vivo, OTP, evidencias, bolsa, confirmación y calificación. */
@Component({
  selector: 'app-errand-detail',
  standalone: true,
  imports: [RouterLink, MapComponent, StatusChipComponent, TimelineComponent, ReputationComponent,
    EvidenceListComponent, OffersPanelComponent, RatingPanelComponent, PushPromptComponent],
  template: `
    <main class="page">
      <a routerLink="/app/mandados" class="btn link">‹ Mis mandados</a>
      @if (!e()) { <div class="spinner mt"></div> }
      @if (e(); as e) {
        <div class="row between mt">
          <h1>{{ cat().emoji }} {{ e.code }}</h1>
          <app-status [status]="e.status" />
        </div>
        <p>{{ e.description }}</p>
        @if (!done() && e.status !== 'CANCELLED' && e.status !== 'FAILED') { <app-push-prompt /> }

        @if (e.status === 'SEARCHING') {
          <div class="alert info row"><div class="spinner"></div> Buscando el mandadero más cercano…</div>
        }
        @if (e.status === 'NEGOTIATING') {
          <app-offers-panel [errand]="e" (updated)="e$.set($event)" />
        }

        @if (e.startOtp || e.endOtp) {
          <div class="grid two mt">
            @if (e.startOtp) {
              <div class="otp-box"><small>Código de inicio</small><div class="code">{{ e.startOtp }}</div>
                <small>Dáselo al mandadero al empezar</small></div>
            }
            @if (e.endOtp) {
              <div class="otp-box"><small>Código de entrega</small><div class="code">{{ e.endOtp }}</div>
                <small>Solo al recibir conforme</small></div>
            }
          </div>
        }

        @if (live()) {
          <h2 class="mt">En vivo</h2>
          <app-map [markers]="markers()" [path]="trail()" [keepView]="true" />
        }

        @if (e.courier; as c) {
          <div class="card mt stack">
            <div class="row between">
              <div>
                <strong>{{ c.fullName }}</strong>
                @if (c.vehicleType) { <div><small class="muted">{{ vehicle(c.vehicleType) }} {{ c.plate ?? '' }}</small></div> }
              </div>
              @if (c.phone && live()) {
                <div class="row" style="gap:8px">
                  <a class="btn ghost sm" [href]="'tel:' + c.phone">📞</a>
                  <a class="btn ok sm" [href]="whatsapp(c.phone, e.code)" target="_blank" rel="noopener">WhatsApp</a>
                </div>
              }
            </div>
            <app-reputation [rep]="c.reputation" />
          </div>
        }

        @if (e.status === 'DELIVERED') {
          <div class="card mt stack">
            <strong>¿Recibiste todo bien?</strong>
            <small class="muted">Revisa las evidencias y confirma. Si no confirmas, se confirmará automáticamente.</small>
            <button class="btn ok block" [disabled]="busy()" (click)="confirm()">Sí, confirmar mandado</button>
          </div>
        }
        @if (e.status === 'COMPLETED' && !e.rating) {
          <div class="mt"><app-rating-panel [errand]="e" (updated)="e$.set($event)" /></div>
        }
        @if (e.rating; as r) {
          <div class="card mt"><strong>Tu calificación:</strong> {{ '★'.repeat(r.stars) }} @if (r.comment) { — {{ r.comment }} }</div>
        }

        <h2 class="mt">Evidencias</h2>
        <div class="card"><app-evidence-list [items]="e.evidences" /></div>

        <h2 class="mt">Resumen</h2>
        <div class="card">
          @for (s of e.stops; track s.seq) {
            <div class="kv"><span>{{ s.seq + 1 }}.</span><span style="text-align:right">{{ s.address }}</span></div>
          }
          <div class="divider"></div>
          <div class="kv"><span>Complejidad</span><span>{{ complexity(e) }}</span></div>
          <div class="kv"><span>Distancia</span><span>{{ e.distanceKm }} km</span></div>
          <div class="kv"><span>Servicio</span><span>{{ money(e.totalPrice - e.platformFee) }}</span></div>
          <div class="kv"><span>Tarifa de plataforma</span><span>{{ money(e.platformFee) }}</span></div>
          @if (e.purchaseBudget > 0) {
            <div class="kv"><span>Bolsa de compras</span><span>{{ money(e.purchaseBudget) }}</span></div>
            <div class="kv"><span>Gastado (con factura)</span><span>{{ money(e.purchasesTotal) }}</span></div>
            <div class="kv"><span>A devolverte</span><span>{{ money(e.refundTotal) }}</span></div>
          }
          <div class="kv total"><span>Total a pagar</span><span>{{ money(e.totalPrice + e.purchasesTotal) }}</span></div>
          <small class="muted">Pago: {{ payment(e) }} · {{ e.paymentStatus === 'CONFIRMED' ? 'recibido' : 'pendiente' }}</small>
          @if (e.scheduledFor) { <div><small class="muted">Programado: {{ dt(e.scheduledFor) }}</small></div> }
        </div>

        <h2 class="mt">Historial</h2>
        <div class="card"><app-timeline [items]="e.history" /></div>

        <div class="row wrap mt">
          @if (canCancel()) {
            <button class="btn ghost" [disabled]="busy()" (click)="cancel()">Cancelar mandado</button>
          }
          @if (done()) {
            <a class="btn ghost" [routerLink]="['/comprobante', e.id]">🧾 Comprobante</a>
            <a class="btn" [routerLink]="['/app/nuevo', e.category]" [queryParams]="{ repetir: e.id }">Repetir mandado</a>
          }
        </div>
        @if (e.cancelReason) { <div class="alert warn mt">Motivo: {{ e.cancelReason }}</div> }
      }
    </main>`,
})
export class ErrandDetailComponent implements OnInit {
  private api = inject(ApiService);
  private rt = inject(RealtimeService);
  private toast = inject(ToastService);
  private destroy = inject(DestroyRef);
  private id = inject(ActivatedRoute).snapshot.paramMap.get('id')!;

  e$ = signal<ErrandView | null>(null);
  e = this.e$.asReadonly();
  trail = signal<LivePoint[]>([]);
  busy = signal(false);

  cat = computed(() => categoryInfo(this.e()!.category));
  live = computed(() => TRACKABLE.includes(this.e()?.status as ErrandStatus));
  canCancel = computed(() => CANCELLABLE.includes(this.e()?.status as ErrandStatus));
  done = computed(() => ['COMPLETED', 'RATED'].includes(this.e()?.status ?? ''));
  markers = computed<MapMarker[]>(() => {
    const e = this.e();
    if (!e) return [];
    const stops: MapMarker[] = e.stops.map(s => ({ lat: s.lat, lng: s.lng, label: String(s.seq + 1), title: s.address }));
    const last = this.trail().at(-1) ?? e.courierLocation;
    return last ? [...stops, { lat: last.lat, lng: last.lng, label: '🛵', kind: 'courier', title: e.courier?.fullName }] : stops;
  });

  money = money;
  dt = dateTime;
  vehicle = (v: keyof typeof VEHICLES) => VEHICLES[v];
  complexity = (e: ErrandView) => COMPLEXITY[e.complexity];
  payment = (e: ErrandView) => PAYMENT[e.paymentMethod];

  ngOnInit() {
    this.load();
    this.api.track(this.id).subscribe({ next: t => this.trail.set(t), error: () => {} });
    this.rt.watch(`/topic/errands/${this.id}`).pipe(takeUntilDestroyed(this.destroy)).subscribe(ev => {
      if (ev.type === 'LOCATION' && ev.data) {
        const p = { lat: Number(ev.data['lat']), lng: Number(ev.data['lng']), at: ev.at };
        this.trail.update(t => [...t.slice(-300), p]);
      } else {
        // Cambio de estado, evidencia u oferta nueva: se recarga la vista autoritativa.
        this.load();
        if (ev.type === 'STATUS_CHANGED' && ev.status) this.toast.show(statusToast(ev.status));
      }
    });
  }

  load() {
    this.api.errand(this.id).subscribe({ next: e => this.e$.set(e), error: err => this.toast.error(err) });
  }

  confirm() {
    this.busy.set(true);
    this.api.confirm(this.id).subscribe({
      next: e => { this.busy.set(false); this.e$.set(e); this.toast.ok('¡Mandado completado!'); },
      error: err => { this.busy.set(false); this.toast.error(err); },
    });
  }

  cancel() {
    if (!confirm('¿Seguro que quieres cancelar el mandado?')) return;
    const reason = prompt('¿Por qué cancelas? (opcional)') ?? undefined;
    this.busy.set(true);
    this.api.cancel(this.id, reason?.trim() || undefined).subscribe({
      next: e => { this.busy.set(false); this.e$.set(e); this.toast.show('Mandado cancelado'); },
      error: err => { this.busy.set(false); this.toast.error(err); },
    });
  }

  whatsapp(phone: string, code: string) {
    const number = phone.startsWith('57') ? phone : `57${phone}`;
    return `https://wa.me/${number}?text=${encodeURIComponent(`Hola, te escribo por el mandado ${code} de Mandao.`)}`;
  }
}

function statusToast(s: string): string {
  switch (s) {
    case 'ASSIGNED': return '¡Ya tienes mandadero!';
    case 'GOING_TO_ORIGIN': return 'Tu mandadero va en camino';
    case 'IN_PROGRESS': return 'Empezamos tu mandado';
    case 'DELIVERED': return 'Mandado entregado. Confírmalo';
    default: return 'Tu mandado se actualizó';
  }
}
