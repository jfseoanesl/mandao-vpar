import { Component, DestroyRef, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Subject, catchError, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';
import { GeoService, Place } from '../../core/geo.service';
import { AddressView } from '../../core/models';
import { ToastService } from '../../core/toast.service';

export interface StopDraft {
  address: string; lat?: number; lng?: number; instructions: string; contactName: string; contactPhone: string;
}

export const emptyStop = (): StopDraft => ({ address: '', instructions: '', contactName: '', contactPhone: '' });

/** Una parada del mandado: búsqueda Nominatim, "mi ubicación", direcciones guardadas e instrucciones. */
@Component({
  selector: 'app-stop-editor',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="card flat stack" [style.border-color]="active() ? 'var(--brand)' : null" (click)="focus.emit()">
      <div class="row between">
        <strong>{{ title() }}</strong>
        @if (removable()) { <button type="button" class="btn link" (click)="remove.emit()">Quitar</button> }
      </div>
      <div class="row">
        <input class="grow" [name]="'q' + index()" placeholder="Busca la dirección o el lugar" autocomplete="off"
               [ngModel]="stop().address" (ngModelChange)="typed($event)" />
        <button type="button" class="btn ghost sm" title="Usar mi ubicación" (click)="useMine()" [disabled]="locating()">📍</button>
      </div>
      @if (results().length) {
        <div class="list card flat" style="padding:4px 12px">
          @for (p of results(); track $index) {
            <button type="button" class="list-item btn link" style="justify-content:flex-start;text-align:left;color:var(--ink)"
                    (click)="choose(p)">{{ p.label }}</button>
          }
        </div>
      }
      @if (saved().length) {
        <div class="row wrap" style="gap:6px">
          @for (a of saved(); track a.id) {
            <button type="button" class="chip select" (click)="choose({ label: a.address, lat: a.lat, lng: a.lng })">⭐ {{ a.label }}</button>
          }
        </div>
      }
      @if (stop().lat == null) {
        <small class="muted">También puedes tocar el mapa para marcar este punto.</small>
      } @else {
        <small class="muted">✅ Punto marcado en el mapa</small>
      }
      <input [name]="'ins' + index()" placeholder="Instrucciones (ej.: preguntar por Ana, oficina 203)" maxlength="500"
             [ngModel]="stop().instructions" (ngModelChange)="patch({ instructions: $event })" />
      <div class="grid two">
        <input [name]="'cn' + index()" placeholder="Contacto (opcional)" maxlength="120"
               [ngModel]="stop().contactName" (ngModelChange)="patch({ contactName: $event })" />
        <input [name]="'cp' + index()" placeholder="Celular contacto" inputmode="numeric" maxlength="10"
               [ngModel]="stop().contactPhone" (ngModelChange)="patch({ contactPhone: $event })" />
      </div>
    </div>`,
})
export class StopEditorComponent {
  private geo = inject(GeoService);
  private toast = inject(ToastService);
  stop = input.required<StopDraft>();
  index = input(0);
  title = input('Punto');
  active = input(false);
  removable = input(false);
  saved = input<AddressView[]>([]);
  changed = output<StopDraft>();
  focus = output<void>();
  remove = output<void>();

  results = signal<Place[]>([]);
  locating = signal(false);
  private query = new Subject<string>();

  constructor() {
    this.query.pipe(
      debounceTime(450),
      distinctUntilChanged(),
      switchMap(q => this.geo.search(q).pipe(catchError(() => of([])))),
      takeUntilDestroyed(inject(DestroyRef)),
    ).subscribe(r => this.results.set(r));
  }

  typed(text: string) {
    // Al editar el texto, el punto deja de ser confiable hasta elegir un resultado o tocar el mapa.
    this.patch({ address: text, lat: undefined, lng: undefined });
    this.query.next(text);
  }

  choose(p: Place) {
    this.results.set([]);
    this.patch({ address: p.label, lat: p.lat, lng: p.lng });
  }

  async useMine() {
    this.locating.set(true);
    try {
      const p = await this.geo.current();
      this.patch({ lat: p.lat, lng: p.lng, address: 'Mi ubicación' });
      this.geo.reverse(p).subscribe({ next: address => this.patch({ address }), error: () => {} });
    } catch (e: any) {
      this.toast.error(e?.message ?? 'No pudimos obtener tu ubicación');
    } finally {
      this.locating.set(false);
    }
  }

  patch(p: Partial<StopDraft>) {
    this.changed.emit({ ...this.stop(), ...p });
  }
}

