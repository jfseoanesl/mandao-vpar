import { Location } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { PAYMENT, STATUS, categoryInfo, dateTime, money } from '../../core/labels';
import { ErrandView } from '../../core/models';
import { EvidenceListComponent } from '../../shared/evidence-list.component';
import { TimelineComponent } from '../../shared/ui';

/** Comprobante imprimible (spec 011): "Imprimir / Guardar PDF" usa el diálogo del navegador. */
@Component({
  selector: 'app-receipt',
  standalone: true,
  imports: [EvidenceListComponent, TimelineComponent],
  template: `
    <main class="page">
      <div class="row between no-print">
        <button class="btn link" (click)="back()">‹ Volver</button>
        <button class="btn sm" (click)="print()">Imprimir / Guardar PDF</button>
      </div>
      @if (e(); as e) {
        <div class="card mt stack">
          <div class="row between">
            <span class="logo">Man<b>dao</b></span>
            <div style="text-align:right"><strong>Comprobante {{ e.code }}</strong><br><small class="muted">{{ dt(e.completedAt ?? e.createdAt) }}</small></div>
          </div>
          <small class="muted">Tú lo pides. Nosotros lo hacemos. · Valledupar</small>
          <div class="divider"></div>
          <div class="kv"><span>Servicio</span><span>{{ cat(e) }}</span></div>
          <div class="kv"><span>Estado</span><span>{{ status(e) }}</span></div>
          <div class="kv"><span>Cliente</span><span>{{ e.client?.fullName ?? '—' }}</span></div>
          <div class="kv"><span>Mandadero</span><span>{{ e.courier?.fullName ?? '—' }}</span></div>
          <p class="mt">{{ e.description }}</p>
          @for (s of e.stops; track s.seq) {
            <div class="kv"><span>{{ s.seq + 1 }}.</span><span style="text-align:right">{{ s.address }}</span></div>
          }
          <div class="divider"></div>
          <div class="kv"><span>Servicio</span><span>{{ money(e.totalPrice - e.platformFee) }}</span></div>
          <div class="kv"><span>Tarifa de plataforma</span><span>{{ money(e.platformFee) }}</span></div>
          @if (e.purchaseBudget > 0) {
            <div class="kv"><span>Compras con factura</span><span>{{ money(e.purchasesTotal) }}</span></div>
            <div class="kv"><span>Devuelto de la bolsa</span><span>{{ money(e.refundTotal) }}</span></div>
          }
          <div class="kv total"><span>Total</span><span>{{ money(e.totalPrice + e.purchasesTotal) }}</span></div>
          <small class="muted">Medio de pago: {{ payment(e) }}</small>
        </div>
        <div class="card mt"><h2>Evidencias</h2><app-evidence-list [items]="e.evidences" /></div>
        <div class="card mt"><h2>Trazabilidad</h2><app-timeline [items]="e.history" /></div>
      } @else if (error()) {
        <div class="alert danger mt">{{ error() }}</div>
      } @else { <div class="spinner mt"></div> }
    </main>`,
})
export class ReceiptComponent implements OnInit {
  private api = inject(ApiService);
  private location = inject(Location);
  private id = inject(ActivatedRoute).snapshot.paramMap.get('id')!;
  e = signal<ErrandView | null>(null);
  error = signal('');
  money = money;
  dt = dateTime;
  cat = (e: ErrandView) => `${categoryInfo(e.category).emoji} ${categoryInfo(e.category).title}`;
  status = (e: ErrandView) => STATUS[e.status].label;
  payment = (e: ErrandView) => PAYMENT[e.paymentMethod];

  ngOnInit() {
    this.api.errand(this.id).subscribe({ next: e => this.e.set(e), error: () => this.error.set('No tienes acceso a este comprobante.') });
  }

  print() { window.print(); }
  back() { this.location.back(); }
}
