import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { categoryInfo, dateTime, money } from '../../core/labels';
import { ErrandSummary } from '../../core/models';
import { StatusChipComponent } from '../../shared/ui';

@Component({
  selector: 'app-my-errands',
  standalone: true,
  imports: [RouterLink, StatusChipComponent],
  template: `
    <main class="page">
      <h1>Mis mandados</h1>
      @if (loading()) { <div class="spinner"></div> }
      @if (!loading() && !items().length) {
        <div class="card center">
          <p>Aún no has pedido mandados.</p>
          <a class="btn" routerLink="/app">Pide el primero</a>
        </div>
      }
      <div class="stack">
        @for (e of items(); track e.id) {
          <a class="card click" [routerLink]="['/app/mandados', e.id]">
            <div class="row between">
              <strong>{{ cat(e).emoji }} {{ e.code }}</strong>
              <app-status [status]="e.status" />
            </div>
            <div class="mt">{{ e.description }}</div>
            <div class="row between mt">
              <small class="muted">{{ dt(e.createdAt) }}</small>
              <strong>{{ money(e.totalPrice) }}</strong>
            </div>
          </a>
        }
      </div>
    </main>`,
})
export class MyErrandsComponent implements OnInit {
  private api = inject(ApiService);
  items = signal<ErrandSummary[]>([]);
  loading = signal(true);
  money = money;
  dt = dateTime;
  cat = (e: ErrandSummary) => categoryInfo(e.category);

  ngOnInit() {
    this.api.myErrands().subscribe({ next: l => { this.items.set(l); this.loading.set(false); }, error: () => this.loading.set(false) });
  }
}
