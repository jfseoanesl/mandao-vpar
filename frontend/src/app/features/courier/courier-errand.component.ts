import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { categoryInfo, money } from '../../core/labels';
import { ErrandView, StopView } from '../../core/models';
import { RealtimeService } from '../../core/realtime.service';
import { ToastService } from '../../core/toast.service';
import { EvidenceListComponent } from '../../shared/evidence-list.component';
import { MapComponent, MapMarker } from '../../shared/map.component';
import { StatusChipComponent } from '../../shared/ui';
import { EvidenceCaptureComponent } from './evidence-capture.component';
import { LocationTrackerService } from './location-tracker.service';

/** Ejecución guiada (spec 005): ir → OTP de inicio → evidencias → OTP de entrega. */
@Component({
  selector: 'app-courier-errand',
  standalone: true,
  imports: [FormsModule, RouterLink, MapComponent, StatusChipComponent, EvidenceListComponent, EvidenceCaptureComponent],
  template: `
    <main class="page">
      <a routerLink="/mandadero" class="btn link">‹ Inicio</a>
      @if (e(); as e) {
        <div class="row between mt">
          <h1>{{ cat().emoji }} {{ e.code }}</h1>
          <app-status [status]="e.status" />
        </div>
        <p>{{ e.description }}</p>
        <div class="row wrap" style="gap:6px">
          <span class="chip ok">Ganas {{ money(e.courierPayout) }}</span>
          @if (e.purchaseBudget > 0) { <span class="chip warn">🛒 Bolsa {{ money(e.purchaseBudget) }}</span> }
          @if (e.estimatedWaitMinutes) { <span class="chip neutral">⏱ ~{{ e.estimatedWaitMinutes }} min de espera</span> }
        </div>

        <app-map class="mt" [markers]="markers()" [path]="route()" />

        <div class="stack mt">
          @for (s of e.stops; track s.seq) {
            <div class="card flat stack">
              <div class="row between">
                <strong>{{ s.seq + 1 }}. {{ s.address }}</strong>
                <div class="row" style="gap:6px">
                  <a class="btn ghost sm" [href]="gmaps(s)" target="_blank" rel="noopener">Maps</a>
                  <a class="btn ghost sm" [href]="waze(s)" target="_blank" rel="noopener">Waze</a>
                </div>
              </div>
              @if (s.instructions) { <small>{{ s.instructions }}</small> }
              @if (s.contactPhone) {
                <small>Contacto: {{ s.contactName ?? '' }} <a [href]="'tel:' + s.contactPhone">{{ s.contactPhone }}</a></small>
              }
            </div>
          }
        </div>

        @if (e.client?.phone && active()) {
          <div class="row mt">
            <span class="grow">Cliente: <strong>{{ e.client!.fullName }}</strong></span>
            <a class="btn ghost sm" [href]="'tel:' + e.client!.phone">📞</a>
            <a class="btn ok sm" [href]="whatsapp(e.client!.phone!, e.code)" target="_blank" rel="noopener">WhatsApp</a>
          </div>
        }

        <div class="card mt stack">
          @switch (e.status) {
            @case ('ASSIGNED') {
              <strong>Paso 1 · Sal hacia el punto de partida</strong>
              <button class="btn block" [disabled]="busy()" (click)="act(api.onTheWay(e.id))">Voy en camino</button>
            }
            @case ('GOING_TO_ORIGIN') {
              <strong>Paso 2 · Al llegar, pide el código de inicio al cliente</strong>
              <input class="otp-input" name="startOtp" inputmode="numeric" maxlength="4" autocomplete="one-time-code" [(ngModel)]="otp" />
              <button class="btn block" [disabled]="busy() || otp.length !== 4" (click)="act(api.start(e.id, otp))">Iniciar mandado</button>
            }
            @case ('IN_PROGRESS') {
              <strong>Paso 3 · Haz el mandado y registra evidencias</strong>
              <small class="muted">Toma fotos, guarda facturas y la firma de quien recibe. Es tu respaldo.</small>
            }
            @case ('DELIVERED') {
              <div class="alert ok">✅ Entregado. Esperando confirmación del cliente.</div>
            }
            @default {
              <div class="alert info">Este mandado está {{ e.status === 'CANCELLED' ? 'cancelado' : 'cerrado' }}.</div>
            }
          }
        </div>

        @if (e.status === 'IN_PROGRESS') {
          <div class="mt"><app-evidence-capture [errandId]="e.id" [budget]="e.purchaseBudget" (added)="load()" /></div>
          <div class="card mt stack">
            <strong>Paso 4 · Entrega: pide el código de entrega</strong>
            <input class="otp-input" name="endOtp" inputmode="numeric" maxlength="4" autocomplete="one-time-code" [(ngModel)]="otp" />
            <button class="btn ok block" [disabled]="busy() || otp.length !== 4" (click)="act(api.deliver(e.id, otp))">Confirmar entrega</button>
          </div>
        }

        <h2 class="mt">Evidencias</h2>
        <div class="card"><app-evidence-list [items]="e.evidences" /></div>

        @if (active()) {
          <button class="btn ghost block mt" [disabled]="busy()" (click)="fail()">No pude completar el mandado</button>
        }
      } @else { <div class="spinner mt"></div> }
    </main>`,
})
export class CourierErrandComponent implements OnInit {
  api = inject(ApiService);
  private rt = inject(RealtimeService);
  private toast = inject(ToastService);
  private gps = inject(LocationTrackerService);
  private destroy = inject(DestroyRef);
  private id = inject(ActivatedRoute).snapshot.paramMap.get('id')!;

  e = signal<ErrandView | null>(null);
  busy = signal(false);
  otp = '';
  money = money;

  cat = computed(() => categoryInfo(this.e()!.category));
  active = computed(() => ['ASSIGNED', 'GOING_TO_ORIGIN', 'IN_PROGRESS'].includes(this.e()?.status ?? ''));
  route = computed(() => (this.e()?.stops ?? []).map(s => ({ lat: s.lat, lng: s.lng })));
  markers = computed<MapMarker[]>(() => {
    const stops: MapMarker[] = (this.e()?.stops ?? []).map(s => ({ lat: s.lat, lng: s.lng, label: String(s.seq + 1), title: s.address }));
    const me = this.gps.position();
    return me ? [...stops, { lat: me.lat, lng: me.lng, label: '🛵', kind: 'courier', title: 'Tú' }] : stops;
  });

  ngOnInit() {
    this.load();
    this.gps.start();
    this.rt.watch(`/topic/errands/${this.id}`).pipe(takeUntilDestroyed(this.destroy)).subscribe(ev => {
      if (ev.type === 'STATUS_CHANGED') {
        if (ev.status === 'CANCELLED') this.toast.error('El mandado fue cancelado');
        if (ev.status === 'COMPLETED') this.toast.ok('El cliente confirmó el mandado 🎉');
        this.load();
      }
    });
  }

  load() {
    this.api.errand(this.id).subscribe({ next: e => this.e.set(e), error: err => this.toast.error(err) });
  }

  act(req: Observable<ErrandView>) {
    this.busy.set(true);
    req.subscribe({
      next: e => { this.busy.set(false); this.otp = ''; this.e.set(e); },
      error: err => { this.busy.set(false); this.toast.error(err); },
    });
  }

  fail() {
    const reason = prompt('Cuéntanos qué pasó (obligatorio)')?.trim();
    if (!reason) return;
    this.act(this.api.fail(this.id, reason));
  }

  gmaps = (s: StopView) => `https://www.google.com/maps/dir/?api=1&destination=${s.lat},${s.lng}`;
  waze = (s: StopView) => `https://waze.com/ul?ll=${s.lat},${s.lng}&navigate=yes`;

  whatsapp(phone: string, code: string) {
    const number = phone.startsWith('57') ? phone : `57${phone}`;
    return `https://wa.me/${number}?text=${encodeURIComponent(`Hola, soy tu mandadero de Mandao (${code}).`)}`;
  }
}
