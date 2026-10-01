import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, debounceTime, firstValueFrom, of, switchMap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { GeoService } from '../../core/geo.service';
import { compressImage } from '../../core/images';
import { COMPLEXITY, PAYMENT, categoryInfo, errorMessage, money } from '../../core/labels';
import { AddressView, Complexity, ErrandCategory, PaymentMethod, PricingMode, Quote } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { MapComponent, MapMarker } from '../../shared/map.component';
import { StopDraft, StopEditorComponent, emptyStop } from './stop-editor.component';

/** Categorías que normalmente implican comprar algo con la bolsa (Mandao.md §11). */
const BUYS: ErrandCategory[] = ['COMPRA', 'BUSCAR_COMPRAR'];
/** Categorías con un único punto por defecto (el mandadero va y vuelve al mismo lugar o no hay destino). */
const SINGLE: ErrandCategory[] = ['OTRO'];
const MAX_STOPS = 6;
const MAX_FILES = 3;

/** Solicitud de mandado: descripción, paradas en mapa, cotización en vivo, bolsa y modo negociable. */
@Component({
  selector: 'app-new-errand',
  standalone: true,
  imports: [FormsModule, RouterLink, MapComponent, StopEditorComponent],
  template: `
    <main class="page">
      <a routerLink="/app" class="btn link">‹ Volver</a>
      <h1 class="mt">{{ cat.emoji }} {{ cat.title }}</h1>

      <form class="stack" (ngSubmit)="submit()">
        <label class="field">Cuéntanos qué necesitas
          <span class="hint">Entre más claro, mejor. Ej.: {{ cat.example }}</span>
          <textarea name="description" maxlength="2000" [(ngModel)]="description" required></textarea>
        </label>

        <h2 class="mt">¿Dónde?</h2>
        <app-map [markers]="markers()" [path]="path()" [picking]="true" (pick)="picked($event)" />
        <small class="muted">Toca el mapa para marcar el punto seleccionado ({{ activeTitle() }}).</small>
        @for (s of stops(); track $index; let i = $index) {
          <app-stop-editor [stop]="s" [index]="i" [title]="stopTitle(i)" [active]="i === active()"
                           [removable]="stops().length > 1" [saved]="addresses()"
                           (focus)="active.set(i)" (changed)="setStop(i, $event)" (remove)="removeStop(i)" />
        }
        @if (stops().length < maxStops) {
          <button type="button" class="btn ghost" (click)="addStop()">+ Agregar parada</button>
        }

        <h2 class="mt">Detalles</h2>
        <label class="field">¿Qué tan complejo es?
          <select name="complexity" [(ngModel)]="complexityValue" (ngModelChange)="complexity.set($event)">
            @for (k of complexityKeys; track k) { <option [value]="k">{{ complexityLabel[k] }}</option> }
          </select>
        </label>
        <label class="field">Tiempo estimado de espera o fila (minutos)
          <span class="hint">Los primeros 10 minutos están incluidos.</span>
          <input name="wait" type="number" min="0" max="240" [(ngModel)]="waitValue" (ngModelChange)="wait.set(+$event || 0)" />
        </label>
        <label class="field">Presupuesto para compras (bolsa)
          <span class="hint">Dinero que el mandadero usará para comprar. Pagas solo lo gastado con factura; el resto se te devuelve.</span>
          <input name="budget" type="number" min="0" max="2000000" step="1000" [(ngModel)]="budget" [placeholder]="buys ? 'Ej.: 80000' : 'Opcional'" />
        </label>
        <label class="field">Programar (opcional)
          <span class="hint">Déjalo vacío para que lo hagamos ya.</span>
          <input name="scheduled" type="datetime-local" [min]="minSchedule" [(ngModel)]="scheduled" />
        </label>
        <label class="field">Fotos o documentos de apoyo (opcional, máx. {{ maxFiles }})
          <input type="file" accept="image/*,application/pdf" multiple (change)="filesChosen($event)" />
        </label>
        @if (files().length) { <small class="muted">{{ files().length }} archivo(s) listos para adjuntar</small> }

        <h2 class="mt">Precio</h2>
        <div class="row" style="gap:8px">
          <button type="button" class="chip select" [class.on]="mode() === 'FIXED'" (click)="mode.set('FIXED')">Precio Mandao</button>
          <button type="button" class="chip select" [class.on]="mode() === 'NEGOTIABLE'" (click)="mode.set('NEGOTIABLE')">Recibir ofertas</button>
        </div>
        <div class="card">
          @if (quoting()) { <div class="spinner"></div> }
          @else if (quote()) {
            @let q = quote()!;
            <div class="kv"><span>Distancia</span><span>{{ q.distanceKm }} km</span></div>
            <div class="kv"><span>Servicio</span><span>{{ money(q.total - q.platformFee) }}</span></div>
            <div class="kv"><span>Tarifa de plataforma</span><span>{{ money(q.platformFee) }}</span></div>
            <div class="kv total"><span>{{ mode() === 'FIXED' ? 'Total' : 'Precio sugerido' }}</span><span>{{ money(q.total) }}</span></div>
            @if (budgetValue() > 0) {
              <small class="muted">+ hasta {{ money(budgetValue()) }} de compras con factura.</small>
            }
            @if (mode() === 'NEGOTIABLE') {
              <div class="alert info mt">Los mandaderos cercanos te enviarán ofertas; tú eliges la que prefieras.</div>
            }
          } @else {
            <small class="muted">Marca los puntos en el mapa para ver el precio al instante.</small>
          }
        </div>

        <label class="field">¿Cómo vas a pagar?
          <select name="payment" [(ngModel)]="payment">
            @for (k of paymentKeys; track k) { <option [value]="k">{{ paymentLabel[k] }}</option> }
          </select>
        </label>

        @if (error()) { <div class="alert danger">{{ error() }}</div> }
        <button class="btn block" [disabled]="busy() || !ready()">
          {{ busy() ? 'Enviando…' : mode() === 'FIXED' ? 'Pedir mandado' : 'Publicar y recibir ofertas' }}
        </button>
      </form>
    </main>`,
})
export class NewErrandComponent implements OnInit {
  private api = inject(ApiService);
  private geo = inject(GeoService);
  private router = inject(Router);
  private toast = inject(ToastService);
  private route = inject(ActivatedRoute);

  readonly category = (this.route.snapshot.paramMap.get('categoria') ?? 'OTRO') as ErrandCategory;
  readonly cat = categoryInfo(this.category) ?? categoryInfo('OTRO');
  readonly buys = BUYS.includes(this.category);
  readonly maxStops = MAX_STOPS;
  readonly maxFiles = MAX_FILES;
  readonly complexityKeys = Object.keys(COMPLEXITY) as Complexity[];
  readonly complexityLabel = COMPLEXITY;
  readonly paymentKeys = Object.keys(PAYMENT) as PaymentMethod[];
  readonly paymentLabel = PAYMENT;
  readonly minSchedule = localInput(new Date(Date.now() + 30 * 60_000));
  money = money;

  description = '';
  complexityValue: Complexity = this.category === 'DILIGENCIA' ? 'MEDIA' : 'SENCILLA';
  waitValue = 0;
  budget: number | null = null;
  scheduled = '';
  payment: PaymentMethod = 'EFECTIVO';
  private repeatOf?: string;

  stops = signal<StopDraft[]>(SINGLE.includes(this.category) ? [emptyStop()] : [emptyStop(), emptyStop()]);
  active = signal(0);
  complexity = signal<Complexity>(this.complexityValue);
  wait = signal(0);
  mode = signal<PricingMode>('FIXED');
  addresses = signal<AddressView[]>([]);
  files = signal<File[]>([]);
  quote = signal<Quote | null>(null);
  quoting = signal(false);
  busy = signal(false);
  error = signal('');

  located = computed(() => this.stops().filter(s => s.lat != null && s.lng != null));
  ready = computed(() => this.located().length === this.stops().length && this.stops().length > 0);
  budgetValue = () => Math.max(0, Number(this.budget) || 0);
  activeTitle = computed(() => this.stopTitle(this.active()));
  markers = computed<MapMarker[]>(() => this.stops().flatMap((s, i) =>
    s.lat != null && s.lng != null ? [{ lat: s.lat, lng: s.lng!, label: String(i + 1), title: s.address }] : []));
  path = computed(() => this.located().map(s => ({ lat: s.lat!, lng: s.lng! })));

  constructor() {
    // Cotización en vivo: se recalcula al cambiar puntos, complejidad o espera.
    const input = computed(() => ({ points: this.path(), complexity: this.complexity(), wait: this.wait(), ready: this.ready() }));
    toObservable(input).pipe(
      debounceTime(400),
      switchMap(i => {
        if (!i.ready) return of(null);
        this.quoting.set(true);
        return this.api.quote(i.complexity, i.points, i.wait).pipe(catchError(() => of(null)));
      }),
      takeUntilDestroyed(inject(DestroyRef)),
    ).subscribe(q => { this.quote.set(q); this.quoting.set(false); });
  }

  ngOnInit() {
    this.api.myAddresses().subscribe({ next: a => this.addresses.set(a), error: () => {} });
    const repeat = this.route.snapshot.queryParamMap.get('repetir');
    if (repeat) this.prefill(repeat);
  }

  /** "Repetir mandado": recompra con los mismos datos (KPI de recompra). */
  private prefill(id: string) {
    this.api.errand(id).subscribe({
      next: e => {
        this.repeatOf = e.id;
        this.description = e.description;
        this.complexityValue = e.complexity;
        this.complexity.set(e.complexity);
        this.waitValue = e.estimatedWaitMinutes;
        this.wait.set(e.estimatedWaitMinutes);
        this.budget = e.purchaseBudget || null;
        this.payment = e.paymentMethod;
        this.stops.set(e.stops.map(s => ({
          address: s.address, lat: s.lat, lng: s.lng, instructions: s.instructions ?? '',
          contactName: s.contactName ?? '', contactPhone: s.contactPhone ?? '',
        })));
      },
      error: () => this.toast.error('No pudimos cargar el mandado a repetir'),
    });
  }

  stopTitle(i: number): string {
    const n = this.stops().length;
    if (n === 1) return 'Lugar del mandado';
    if (i === 0) return 'Punto de partida';
    if (i === n - 1) return 'Destino';
    return `Parada ${i}`;
  }

  setStop(i: number, s: StopDraft) {
    this.stops.update(list => list.map((x, j) => (j === i ? s : x)));
  }

  addStop() {
    this.stops.update(l => [...l, emptyStop()]);
    this.active.set(this.stops().length - 1);
  }

  removeStop(i: number) {
    this.stops.update(l => l.filter((_, j) => j !== i));
    this.active.set(Math.min(this.active(), this.stops().length - 1));
  }

  picked(p: { lat: number; lng: number }) {
    const i = this.active();
    this.setStop(i, { ...this.stops()[i], lat: p.lat, lng: p.lng, address: 'Punto en el mapa' });
    this.geo.reverse(p).subscribe({ next: address => this.setStop(i, { ...this.stops()[i], address }), error: () => {} });
    // Avanza al siguiente punto sin marcar para agilizar.
    const next = this.stops().findIndex(s => s.lat == null);
    if (next >= 0) this.active.set(next);
  }

  filesChosen(ev: Event) {
    const list = Array.from((ev.target as HTMLInputElement).files ?? []);
    if (list.length > MAX_FILES) this.toast.show(`Solo se adjuntarán los primeros ${MAX_FILES} archivos`);
    this.files.set(list.slice(0, MAX_FILES));
  }

  async submit() {
    if (this.description.trim().length < 5) { this.error.set('Describe el mandado (mínimo 5 caracteres).'); return; }
    if (!this.ready()) { this.error.set('Marca todos los puntos en el mapa.'); return; }
    this.busy.set(true);
    this.error.set('');
    try {
      const errand = await firstValueFrom(this.api.createErrand({
        category: this.category,
        description: this.description.trim(),
        complexity: this.complexity(),
        pricingMode: this.mode(),
        stops: this.stops().map(s => ({
          address: s.address.trim().slice(0, 250), lat: s.lat!, lng: s.lng!,
          instructions: s.instructions.trim() || undefined,
          contactName: s.contactName.trim() || undefined,
          contactPhone: s.contactPhone.trim() || undefined,
        })),
        estimatedWaitMinutes: this.wait(),
        purchaseBudget: this.budgetValue() || undefined,
        paymentMethod: this.payment,
        scheduledFor: this.scheduled ? new Date(this.scheduled).toISOString() : undefined,
        repeatOf: this.repeatOf,
      }));
      await this.uploadAttachments(errand.id);
      this.toast.ok(this.mode() === 'FIXED' ? '¡Listo! Estamos buscando tu mandadero' : 'Publicado. Pronto llegarán ofertas');
      this.router.navigate(['/app/mandados', errand.id]);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }

  /** Los adjuntos no bloquean el mandado: si alguno falla se avisa y se continúa. */
  private async uploadAttachments(id: string) {
    for (const f of this.files()) {
      try {
        await firstValueFrom(this.api.attach(id, await compressImage(f)));
      } catch {
        this.toast.error(`No se pudo adjuntar ${f.name}`);
      }
    }
  }
}

function localInput(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
