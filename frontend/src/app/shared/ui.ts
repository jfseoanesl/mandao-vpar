import { Component, inject, input } from '@angular/core';
import { STATUS, dateTime } from '../core/labels';
import { ErrandStatus, HistoryView, Reputation } from '../core/models';
import { ToastService } from '../core/toast.service';

@Component({
  selector: 'app-status',
  standalone: true,
  template: `<span class="chip {{ info().tone }}">{{ info().label }}</span>`,
})
export class StatusChipComponent {
  status = input.required<ErrandStatus>();
  info = () => STATUS[this.status()];
}

@Component({
  selector: 'app-toasts',
  standalone: true,
  template: `
    <div class="toasts" role="status" aria-live="polite">
      @for (t of toasts.toasts(); track t.id) {
        <div class="toast {{ t.kind }}">{{ t.text }}</div>
      }
    </div>`,
})
export class ToastHostComponent {
  toasts = inject(ToastService);
}

@Component({
  selector: 'app-timeline',
  standalone: true,
  template: `
    <ul class="timeline">
      @for (h of items(); track $index) {
        <li><strong>{{ label(h.to) }}</strong> <small class="muted">· {{ dt(h.at) }}</small>
          @if (h.note) { <div><small>{{ h.note }}</small></div> }
        </li>
      }
    </ul>`,
})
export class TimelineComponent {
  items = input<HistoryView[]>([]);
  label = (s: ErrandStatus) => STATUS[s].label;
  dt = dateTime;
}

/** Reputación más allá de las estrellas (Mandao.md §15). */
@Component({
  selector: 'app-reputation',
  standalone: true,
  template: `
    @if (rep(); as r) {
      <div class="row wrap" style="gap:8px">
        <span class="chip">{{ r.averageStars != null ? r.averageStars + ' ⭐' : 'Nuevo' }}</span>
        @if (r.completionRate != null) { <span class="chip neutral">{{ r.completionRate }}% completados</span> }
        @if (r.punctualityRate != null) { <span class="chip neutral">⏱ {{ r.punctualityRate }}% puntual</span> }
        <span class="chip neutral">✅ {{ r.completedCount }} mandados</span>
        @if (r.memberSince) { <span class="chip neutral">📆 desde {{ year(r.memberSince) }}</span> }
      </div>
    }`,
})
export class ReputationComponent {
  rep = input<Reputation | undefined>();
  year = (iso: string) => new Date(iso).getFullYear();
}
