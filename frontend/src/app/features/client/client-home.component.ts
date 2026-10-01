import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { CATEGORIES, money } from '../../core/labels';
import { ErrandSummary } from '../../core/models';
import { StatusChipComponent } from '../../shared/ui';

const ACTIVE = ['NEGOTIATING', 'SEARCHING', 'ASSIGNED', 'GOING_TO_ORIGIN', 'IN_PROGRESS', 'DELIVERED'];

/** Pantalla inicial: "¿Qué necesitas hacer hoy?" (Mandao.md §4). */
@Component({
  selector: 'app-client-home',
  standalone: true,
  imports: [RouterLink, StatusChipComponent],
  template: `
    <main class="page">
      @for (e of active(); track e.id) {
        <a class="card click row" [routerLink]="['/app/mandados', e.id]" style="margin-bottom:12px">
          <div class="pulse"></div>
          <div class="grow">
            <strong>{{ e.code }}</strong> · <app-status [status]="e.status" />
            <div><small class="muted">{{ e.description }}</small></div>
          </div>
          <span>›</span>
        </a>
      }
      <h1>¿Qué necesitas hacer hoy?</h1>
      <p class="muted">Elige una opción. Tú lo pides, nosotros lo hacemos.</p>
      <div class="cat-grid">
        @for (c of categories; track c.key) {
          <a class="cat" [class.featured]="c.key === 'OTRO'" [routerLink]="['/app/nuevo', c.key]">
            <span class="emoji">{{ c.emoji }}</span>
            <strong>{{ c.title }}</strong>
            <small class="muted">{{ c.hint }}</small>
          </a>
        }
      </div>
      @if (recent().length) {
        <h2 class="mt">Repite un mandado</h2>
        <div class="card list">
          @for (e of recent(); track e.id) {
            <div class="list-item">
              <div class="grow"><strong>{{ e.description }}</strong><br><small class="muted">{{ money(e.totalPrice) }}</small></div>
              <a class="btn ghost sm" [routerLink]="['/app/nuevo', e.category]" [queryParams]="{ repetir: e.id }">Repetir</a>
            </div>
          }
        </div>
      }
    </main>`,
})
export class ClientHomeComponent implements OnInit {
  private api = inject(ApiService);
  categories = CATEGORIES;
  active = signal<ErrandSummary[]>([]);
  recent = signal<ErrandSummary[]>([]);
  money = money;

  ngOnInit() {
    this.api.myErrands().subscribe(list => {
      this.active.set(list.filter(e => ACTIVE.includes(e.status)));
      this.recent.set(list.filter(e => e.status === 'COMPLETED' || e.status === 'RATED').slice(0, 3));
    });
  }
}
