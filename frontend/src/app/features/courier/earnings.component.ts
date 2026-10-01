import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { dateTime, money } from '../../core/labels';
import { CourierView, Earnings, Reputation } from '../../core/models';
import { ReputationComponent, StatusChipComponent } from '../../shared/ui';

/** Ganancias y reputación del mandadero. */
@Component({
  selector: 'app-earnings',
  standalone: true,
  imports: [RouterLink, StatusChipComponent, ReputationComponent],
  template: `
    <main class="page">
      <h1>Mis ganancias</h1>
      @if (data(); as d) {
        <div class="grid two">
          <div class="card"><small class="muted">Hoy</small><div class="big-number">{{ money(d.today) }}</div></div>
          <div class="card"><small class="muted">Esta semana</small><div class="big-number">{{ money(d.week) }}</div></div>
          <div class="card"><small class="muted">Por pagarte</small><div class="big-number">{{ money(d.pendingPayout) }}</div></div>
          <div class="card"><small class="muted">Pagado total</small><div class="big-number">{{ money(d.paidTotal) }}</div></div>
        </div>

        <h2 class="mt">Mi reputación</h2>
        <div class="card"><app-reputation [rep]="rep()" /></div>

        <h2 class="mt">Mandados ({{ d.completedCount }} completados)</h2>
        <div class="card list">
          @for (i of d.items; track i.errandId) {
            <a class="list-item" style="color:inherit" [routerLink]="['/mandadero/mandados', i.errandId]">
              <div class="grow">
                <strong>{{ i.code }}</strong> <app-status [status]="i.status" />
                <div><small class="muted">{{ i.completedAt ? dt(i.completedAt) : '' }}</small></div>
              </div>
              <div style="text-align:right">
                <strong>{{ money(i.payout) }}</strong>
                <div><small class="{{ i.paidAt ? '' : 'muted' }}">{{ i.paidAt ? '✅ Pagado' : 'Pendiente' }}</small></div>
              </div>
            </a>
          } @empty { <p class="muted">Aún no tienes mandados.</p> }
        </div>
      } @else { <div class="spinner"></div> }
    </main>`,
})
export class EarningsComponent implements OnInit {
  private api = inject(ApiService);
  data = signal<Earnings | null>(null);
  rep = signal<Reputation | undefined>(undefined);
  money = money;
  dt = dateTime;

  ngOnInit() {
    this.api.earnings().subscribe(d => this.data.set(d));
    this.api.courierMe().subscribe((me: CourierView) =>
      this.api.reputation(me.userId).subscribe({ next: r => this.rep.set(r), error: () => {} }));
  }
}
