import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { VEHICLES, money } from '../../core/labels';
import { ErrandView, OfferView } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { ReputationComponent } from '../../shared/ui';

/** Negociación (spec 003): el cliente acepta, rechaza o contraoferta. */
@Component({
  selector: 'app-offers-panel',
  standalone: true,
  imports: [FormsModule, ReputationComponent],
  template: `
    <div class="card stack">
      <div class="row between">
        <h2>Ofertas recibidas</h2>
        <button type="button" class="btn ghost sm" (click)="load()">Actualizar</button>
      </div>
      <small class="muted">Precio sugerido por Mandao: <strong>{{ money(errand().quotedPrice) }}</strong></small>
      @if (!offers().length) {
        <div class="row"><div class="spinner"></div><span class="muted">Esperando ofertas de mandaderos cercanos…</span></div>
      }
      @for (o of offers(); track o.id) {
        <div class="card flat stack">
          <div class="row between">
            <strong>{{ o.courier?.fullName ?? 'Mandadero' }}</strong>
            <span class="big-number" style="font-size:22px">{{ money(o.amount) }}</span>
          </div>
          @if (o.courier?.vehicleType) { <small class="muted">{{ vehicle(o) }}</small> }
          <app-reputation [rep]="o.courier?.reputation" />
          @if (o.message) { <div>“{{ o.message }}”</div> }
          @if (o.status === 'COUNTERED') {
            <div class="alert info">Le propusiste {{ money(o.counterAmount) }}. Esperando su respuesta.</div>
          } @else if (o.status === 'REJECTED') {
            <small class="muted">Rechazada</small>
          } @else if (o.status === 'PENDING') {
            <div class="row wrap">
              <button type="button" class="btn ok sm" [disabled]="busy()" (click)="accept(o)">Aceptar</button>
              <button type="button" class="btn ghost sm" [disabled]="busy()" (click)="reject(o)">Rechazar</button>
              <input class="grow" type="number" min="3000" step="500" placeholder="Tu precio" style="min-width:110px"
                     [(ngModel)]="counter[o.id]" [name]="'c' + o.id" />
              <button type="button" class="btn dark sm" [disabled]="busy() || !counter[o.id]" (click)="sendCounter(o)">Contraofertar</button>
            </div>
          }
        </div>
      }
    </div>`,
})
export class OffersPanelComponent implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  errand = input.required<ErrandView>();
  updated = output<ErrandView>();
  offers = signal<OfferView[]>([]);
  busy = signal(false);
  counter: Record<string, number | null> = {};
  money = money;
  vehicle = (o: OfferView) => VEHICLES[o.courier!.vehicleType!] + (o.courier?.plate ? ` · ${o.courier.plate}` : '');

  ngOnInit() { this.load(); }

  load() {
    this.api.offers(this.errand().id).subscribe({ next: o => this.offers.set(o), error: e => this.toast.error(e) });
  }

  accept(o: OfferView) {
    this.busy.set(true);
    this.api.acceptOffer(this.errand().id, o.id).subscribe({
      next: e => { this.busy.set(false); this.toast.ok('¡Oferta aceptada!'); this.updated.emit(e); },
      error: e => { this.busy.set(false); this.toast.error(e); this.load(); },
    });
  }

  reject(o: OfferView) {
    this.busy.set(true);
    this.api.rejectOffer(this.errand().id, o.id).subscribe({
      next: () => { this.busy.set(false); this.load(); },
      error: e => { this.busy.set(false); this.toast.error(e); },
    });
  }

  sendCounter(o: OfferView) {
    const amount = Number(this.counter[o.id]);
    this.busy.set(true);
    this.api.counterOffer(this.errand().id, o.id, amount).subscribe({
      next: () => { this.busy.set(false); this.counter[o.id] = null; this.toast.ok('Contraoferta enviada'); this.load(); },
      error: e => { this.busy.set(false); this.toast.error(e); },
    });
  }
}
