import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, saveBlob } from '../../core/api.service';
import { CATEGORIES, money } from '../../core/labels';
import { Breakdown, Kpi, KpiReport } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { DailyChartComponent } from './daily-chart.component';

const SEGMENTS: Record<string, string> = { PERSONA: 'Personas', EMPRESA: 'Empresas' };

/** Tablero del piloto (spec 010): KPIs de validación contra meta de Mandao.md. */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [FormsModule, DailyChartComponent],
  template: `
    <main class="page wide">
      <div class="row between wrap">
        <h1>Validación del piloto</h1>
        <form class="row wrap" (ngSubmit)="load()">
          <input type="date" name="from" [(ngModel)]="from" />
          <input type="date" name="to" [(ngModel)]="to" />
          <button class="btn sm">Ver</button>
          <button type="button" class="btn ghost sm" (click)="export()">Exportar CSV</button>
        </form>
      </div>

      @if (r(); as r) {
        <p class="muted">{{ r.days }} días · {{ day(r.from) }} a {{ day(r.to, true) }}</p>
        <div class="grid three">
          @for (k of r.kpis; track k.key) {
            <div class="card">
              <small class="muted">{{ k.label }}</small>
              <div class="big-number">{{ fmt(k, k.value) }}</div>
              <div class="row between">
                <small class="muted">Meta {{ k.higherIsBetter ? '≥' : '≤' }} {{ fmt(k, k.target) }}</small>
                @if (k.met === true) { <span class="chip ok">✓ Cumple</span> }
                @else if (k.met === false) { <span class="chip warn">⚠ Por debajo</span> }
                @else { <span class="chip neutral">Sin datos</span> }
              </div>
            </div>
          }
        </div>

        <div class="grid three mt">
          <div class="card"><small class="muted">Mandados</small><div class="big-number">{{ r.totals.errands }}</div>
            <small class="muted">{{ r.totals.completed }} completados · {{ r.totals.cancelled }} cancelados · {{ r.totals.failed }} fallidos</small></div>
          <div class="card"><small class="muted">GMV</small><div class="big-number">{{ money(r.totals.gmv) }}</div>
            <small class="muted">Ingreso plataforma {{ money(r.totals.platformRevenue) }}</small></div>
          <div class="card"><small class="muted">Usuarios</small><div class="big-number">{{ r.totals.activeClients }}</div>
            <small class="muted">clientes activos · {{ r.totals.signups }} registros · {{ r.totals.activeCouriers }} mandaderos</small></div>
        </div>

        <div class="card mt">
          <h2>Mandados por día</h2>
          <app-daily-chart [points]="r.daily" />
        </div>

        <div class="card mt">
          <h2>Embudo</h2>
          @for (f of funnel(r); track f.label) {
            <div class="kv"><span>{{ f.label }}</span><span>{{ f.value }} <small class="muted">{{ f.pct }}</small></span></div>
          }
        </div>

        <div class="grid three mt">
          @for (t of tables(r); track t.title) {
            <div class="card table-wrap">
              <h3>{{ t.title }}</h3>
              <table class="table">
                <thead><tr><th>{{ t.col }}</th><th>Mandados</th><th>Completados</th></tr></thead>
                <tbody>
                  @for (b of t.rows; track b.key) {
                    <tr><td>{{ t.name(b.key) }}</td><td class="mono">{{ b.errands }}</td><td class="mono">{{ b.completed }}</td></tr>
                  } @empty { <tr><td colspan="3" class="muted">Sin datos</td></tr> }
                </tbody>
              </table>
            </div>
          }
        </div>
      } @else { <div class="spinner"></div> }
    </main>`,
})
export class DashboardComponent implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  r = signal<KpiReport | null>(null);
  from = isoDay(-29);
  to = isoDay(0);
  money = money;

  ngOnInit() { this.load(); }

  /** El rango llega como instantes; {@code to} es exclusivo (medianoche del día siguiente). */
  day(iso: string, exclusiveEnd = false) {
    const t = new Date(iso).getTime() - (exclusiveEnd ? 1 : 0);
    return new Date(t).toLocaleDateString('es-CO', { timeZone: 'America/Bogota', day: 'numeric', month: 'short', year: 'numeric' });
  }

  load() {
    this.api.kpis(this.from, this.to).subscribe({ next: r => this.r.set(r), error: e => this.toast.error(e) });
  }

  export() {
    this.api.exportErrands(this.from, this.to).subscribe({
      next: b => saveBlob(b, `mandados-${this.from}-${this.to}.csv`),
      error: e => this.toast.error(e),
    });
  }

  fmt(k: Kpi, v?: number): string {
    if (v == null) return '—';
    switch (k.unit) {
      case 'COP': return money(v);
      case '%': return `${v}%`;
      case 'min': return `${v} min`;
      case 'estrellas': return `${v} ★`;
      default: return `${v}${k.unit ? ' ' + k.unit : ''}`;
    }
  }

  funnel(r: KpiReport) {
    const pct = (a: number, b: number) => (b ? `(${Math.round((a / b) * 100)}%)` : '');
    return [
      { label: 'Cotizaron', value: r.funnel.quotes, pct: '' },
      { label: 'Solicitaron', value: r.funnel.requested, pct: pct(r.funnel.requested, r.funnel.quotes) },
      { label: 'Completados', value: r.funnel.completed, pct: pct(r.funnel.completed, r.funnel.requested) },
    ];
  }

  tables(r: KpiReport): { title: string; col: string; rows: Breakdown[]; name: (k: string) => string }[] {
    return [
      { title: 'Por categoría', col: 'Categoría', rows: r.byCategory, name: k => CATEGORIES.find(c => c.key === k)?.title ?? k },
      { title: 'Por segmento', col: 'Segmento', rows: r.bySegment, name: k => SEGMENTS[k] ?? k },
      { title: 'Por canal', col: 'Canal', rows: r.byChannel, name: k => k || 'Directo' },
    ];
  }
}

function isoDay(offset: number): string {
  const d = new Date(Date.now() + offset * 86_400_000);
  return d.toLocaleDateString('en-CA', { timeZone: 'America/Bogota' });
}
