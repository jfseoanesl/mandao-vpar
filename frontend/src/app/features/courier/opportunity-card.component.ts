import { Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { categoryInfo, dateTime, money } from '../../core/labels';
import { ErrandView, OpportunityView } from '../../core/models';
import { ToastService } from '../../core/toast.service';

/** Mandado disponible cerca: aceptar (primero gana) u ofertar en negociables (specs 003/004). */
@Component({
  selector: 'app-opportunity-card',
  standalone: true,
  imports: [FormsModule],
  template: `
    @let o = op();
    <div class="card stack">
      <div class="row between">
        <strong>{{ cat().emoji }} {{ cat().title }}</strong>
        <span class="big-number" style="font-size:22px">{{ money(o.pricingMode === 'FIXED' ? o.courierPayout : o.suggestedPrice) }}</span>
      </div>
      <div>{{ o.description }}</div>
      <small class="muted">
        📍 {{ o.originAddress }}@if (o.destinationAddress) { → {{ o.destinationAddress }} }
      </small>
      <div class="row wrap" style="gap:6px">
        @if (o.distanceToOriginKm != null) { <span class="chip neutral">A {{ o.distanceToOriginKm }} km de ti</span> }
        <span class="chip neutral">Recorrido {{ o.routeKm }} km</span>
        @if (o.stopsCount > 2) { <span class="chip neutral">{{ o.stopsCount }} paradas</span> }
        @if (o.purchaseBudget > 0) { <span class="chip warn">🛒 Compra hasta {{ money(o.purchaseBudget) }}</span> }
        @if (o.scheduledFor) { <span class="chip info">🕑 {{ dt(o.scheduledFor) }}</span> }
      </div>

      @if (o.pricingMode === 'FIXED') {
        <small class="muted">Tu ganancia neta. El primero en aceptar se lo lleva.</small>
        <button class="btn ok block" [disabled]="busy()" (click)="accept()">Aceptar mandado</button>
      } @else if (o.myOffer?.status === 'COUNTERED') {
        <div class="alert info">El cliente propone {{ money(o.myOffer!.counterAmount) }}.</div>
        <button class="btn ok block" [disabled]="busy()" (click)="acceptCounter()">Aceptar {{ money(o.myOffer!.counterAmount) }}</button>
      } @else if (o.myOffer?.status === 'PENDING') {
        <div class="alert info">Ofertaste {{ money(o.myOffer!.amount) }}. Esperando al cliente.</div>
      } @else if (o.myOffer?.status === 'REJECTED') {
        <small class="muted">El cliente rechazó tu oferta.</small>
      } @else {
        <small class="muted">Precio sugerido para el cliente. Propón el tuyo:</small>
        <div class="row">
          <input class="grow" type="number" min="3000" max="500000" step="500" name="amount" [(ngModel)]="amount" />
          <button class="btn sm" [disabled]="busy() || !amount" (click)="bid()">Ofertar</button>
        </div>
        <input name="msg" maxlength="300" placeholder="Mensaje (opcional): llego en 10 min" [(ngModel)]="message" />
      }
    </div>`,
})
export class OpportunityCardComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  op = input.required<OpportunityView>();
  taken = output<ErrandView>();
  changed = output<void>();
  busy = signal(false);
  amount: number | null = null;
  message = '';
  money = money;
  dt = dateTime;
  cat = () => categoryInfo(this.op().category);

  accept() {
    this.run(this.api.accept(this.op().id), e => this.taken.emit(e as ErrandView), '¡Es tuyo! Ve por él');
  }

  acceptCounter() {
    this.run(this.api.acceptCounter(this.op().id), e => this.taken.emit(e as ErrandView), '¡Trato hecho!');
  }

  bid() {
    this.run(this.api.bid(this.op().id, Number(this.amount), this.message.trim() || undefined),
      () => this.changed.emit(), 'Oferta enviada');
  }

  private run(req: Observable<unknown>, done: (r: unknown) => void, ok: string) {
    this.busy.set(true);
    req.subscribe({
      next: r => { this.busy.set(false); this.toast.ok(ok); done(r); },
      // 409: otro mandadero lo tomó primero o el mandado cambió.
      error: e => { this.busy.set(false); this.toast.error(e); this.changed.emit(); },
    });
  }
}
