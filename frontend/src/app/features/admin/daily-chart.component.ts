import { Component, computed, input, signal } from '@angular/core';
import { money } from '../../core/labels';

export interface DailyPoint { date: string; errands: number; completed: number; gmv: number; }

const H = 160;
const PAD_TOP = 8;

/**
 * Mandados por día: una sola serie (magnitud) en el color de marca, barras finas con extremo
 * redondeado anclado a la base, grilla tenue y tooltip al pasar el cursor (o tocar).
 */
@Component({
  selector: 'app-daily-chart',
  standalone: true,
  template: `
    <div class="chart" (mouseleave)="hover.set(null)">
      <svg [attr.viewBox]="'0 0 ' + width() + ' ' + (h + 20)" role="img"
           [attr.aria-label]="'Mandados por día, máximo ' + max()">
        @for (g of grid(); track g.v) {
          <line x1="0" [attr.x2]="width()" [attr.y1]="g.y" [attr.y2]="g.y" class="grid" />
          <text x="2" [attr.y]="g.y - 3" class="axis">{{ g.v }}</text>
        }
        @for (b of bars(); track b.p.date) {
          <rect [attr.x]="b.slotX" y="0" [attr.width]="slot()" [attr.height]="h" fill="transparent"
                (mouseenter)="hover.set(b)" (click)="hover.set(b)" />
          <path [attr.d]="b.d" class="bar" [class.dim]="hover() && hover() !== b" />
        }
        @for (l of labels(); track l.x) {
          <text [attr.x]="l.x" [attr.y]="h + 15" text-anchor="middle" class="axis">{{ l.text }}</text>
        }
      </svg>
      @if (hover(); as b) {
        <div class="tip" [style.left.%]="(b.cx / width()) * 100">
          <strong>{{ b.p.date }}</strong><br>
          {{ b.p.errands }} mandados · {{ b.p.completed }} completados<br>
          GMV {{ money(b.p.gmv) }}
        </div>
      }
    </div>`,
  styles: [`
    .chart { position: relative; }
    svg { width: 100%; height: auto; max-height: 260px; display: block; overflow: visible; }
    .grid { stroke: var(--line); stroke-width: 1; vector-effect: non-scaling-stroke; }
    .axis { fill: var(--muted); font-size: 10px; }
    .bar { fill: var(--brand); transition: opacity .12s; pointer-events: none; }
    .bar.dim { opacity: .45; }
    .tip { position: absolute; top: 0; transform: translateX(-50%); background: var(--surface); color: var(--ink);
           border: 1px solid var(--line); box-shadow: var(--shadow); border-radius: 10px; padding: 8px 10px;
           font-size: 12px; pointer-events: none; white-space: nowrap; }
  `],
})
export class DailyChartComponent {
  points = input<DailyPoint[]>([]);
  hover = signal<Bar | null>(null);
  readonly h = H;
  money = money;

  width = computed(() => Math.max(300, this.points().length * 24));
  slot = computed(() => this.width() / Math.max(1, this.points().length));
  max = computed(() => niceMax(Math.max(1, ...this.points().map(p => p.errands))));
  grid = computed(() => [0.5, 1].map(f => ({ v: Math.round(this.max() * f), y: this.y(this.max() * f) })));

  bars = computed<Bar[]>(() => this.points().map((p, i) => {
    const slot = this.slot();
    const w = Math.max(2, Math.min(18, slot - 2)); // ≥ 2px de separación entre barras
    const x = i * slot + (slot - w) / 2;
    const top = this.y(p.errands);
    const r = Math.min(4, w / 2, H - top);
    // Extremo superior redondeado (4px), base recta anclada al eje.
    const d = p.errands === 0 ? '' :
      `M${x},${H} V${top + r} Q${x},${top} ${x + r},${top} H${x + w - r} Q${x + w},${top} ${x + w},${top + r} V${H} Z`;
    return { p, d, slotX: i * slot, cx: x + w / 2 };
  }));

  /** Etiquetas del eje X espaciadas para no chocar (~6 visibles). */
  labels = computed(() => {
    const step = Math.max(1, Math.ceil(this.points().length / 6));
    return this.bars().filter((_, i) => i % step === 0).map(b => ({ x: b.cx, text: b.p.date.slice(5) }));
  });

  private y(v: number) {
    return PAD_TOP + (H - PAD_TOP) * (1 - v / this.max());
  }
}

interface Bar { p: DailyPoint; d: string; slotX: number; cx: number; }

function niceMax(v: number): number {
  const mag = 10 ** Math.floor(Math.log10(v));
  return [1, 2, 5, 10].map(m => m * mag).find(m => m >= v)!;
}
