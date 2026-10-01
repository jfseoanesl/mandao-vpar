import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { dateTime } from '../../core/labels';
import { Tariff } from '../../core/models';
import { ToastService } from '../../core/toast.service';

type NumericKey = Exclude<keyof Tariff, 'version' | 'createdAt'>;

const FIELDS: { key: NumericKey; label: string; hint: string }[] = [
  { key: 'baseFare', label: 'Tarifa base (COP)', hint: 'Cobro fijo por mandado' },
  { key: 'perKm', label: 'Por kilómetro (COP)', hint: 'Distancia del recorrido' },
  { key: 'includedWaitMin', label: 'Minutos de espera incluidos', hint: 'Sin costo adicional' },
  { key: 'perExtraMin', label: 'Por minuto extra (COP)', hint: 'Filas y esperas' },
  { key: 'perExtraStop', label: 'Por parada extra (COP)', hint: 'Desde la tercera parada' },
  { key: 'complexityMedium', label: 'Recargo diligencia sencilla (COP)', hint: '' },
  { key: 'complexityComplex', label: 'Recargo diligencia compleja (COP)', hint: '' },
  { key: 'commissionPct', label: 'Comisión de plataforma (%)', hint: 'Del valor del servicio' },
];

/** Tarifas versionadas (spec 002): cada guardado crea una versión nueva; los mandados conservan la suya. */
@Component({
  selector: 'app-tariff',
  standalone: true,
  imports: [FormsModule],
  template: `
    <main class="page">
      <h1>Tarifas</h1>
      @if (t(); as t) {
        <p class="muted">Versión vigente {{ t.version }} · {{ dt(t.createdAt) }}</p>
        <form class="card stack" (ngSubmit)="save()">
          @for (f of fields; track f.key) {
            <label class="field">{{ f.label }}
              @if (f.hint) { <span class="hint">{{ f.hint }}</span> }
              <input type="number" min="0" [step]="f.key === 'commissionPct' ? 0.5 : 100" [name]="f.key" [(ngModel)]="draft[f.key]" />
            </label>
          }
          <div class="alert info">Los cambios aplican solo a cotizaciones nuevas.</div>
          <button class="btn" [disabled]="busy()">Guardar nueva versión</button>
        </form>
      } @else { <div class="spinner"></div> }
    </main>`,
})
export class TariffComponent implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  t = signal<Tariff | null>(null);
  busy = signal(false);
  fields = FIELDS;
  draft = {} as Record<NumericKey, number>;
  dt = dateTime;

  ngOnInit() {
    this.api.tariff().subscribe({ next: t => this.apply(t), error: e => this.toast.error(e) });
  }

  private apply(t: Tariff) {
    this.t.set(t);
    for (const f of FIELDS) this.draft[f.key] = t[f.key];
  }

  save() {
    if (!confirm('¿Publicar la nueva tarifa?')) return;
    this.busy.set(true);
    const body = { ...this.draft } as Tariff;
    for (const f of FIELDS) body[f.key] = Number(body[f.key]) || 0;
    this.api.updateTariff(body).subscribe({
      next: t => { this.busy.set(false); this.apply(t); this.toast.ok(`Tarifa v${t.version} publicada`); },
      error: e => { this.busy.set(false); this.toast.error(e); },
    });
  }
}
