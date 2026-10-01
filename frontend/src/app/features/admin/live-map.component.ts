import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { debounceTime } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { STATUS, time } from '../../core/labels';
import { LiveMap } from '../../core/models';
import { RealtimeService } from '../../core/realtime.service';
import { MapComponent, MapMarker } from '../../shared/map.component';

/** Mapa en vivo de mandaderos y mandados activos (spec 010). */
@Component({
  selector: 'app-live-map',
  standalone: true,
  imports: [RouterLink, MapComponent],
  template: `
    <main class="page wide">
      <div class="row between">
        <h1>En vivo</h1>
        <small class="muted">🛵 disponible · 🟠 en servicio · 📦 mandado · ⚠ requiere atención</small>
      </div>
      <app-map [markers]="markers()" [tall]="true" [keepView]="true" (markerClick)="open($event)" />
      @if (data(); as d) {
        <div class="grid two mt">
          <div class="card">
            <h3>Mandaderos ({{ d.couriers.length }})</h3>
            @for (c of d.couriers; track c.id) {
              <div class="kv"><span>{{ c.available ? '🟢' : '⚪' }} {{ c.fullName }}</span>
                <span>@if (c.activeErrandId) { <a [routerLink]="['/admin/mandados', c.activeErrandId]">{{ c.activeErrandCode }}</a> · }{{ t(c.lastPingAt) }}</span></div>
            }
          </div>
          <div class="card">
            <h3>Mandados activos ({{ d.errands.length }})</h3>
            @for (e of d.errands; track e.id) {
              <div class="kv"><a [routerLink]="['/admin/mandados', e.id]">{{ e.needsAttention ? '⚠ ' : '' }}{{ e.code }}</a><span>{{ label(e.status) }}</span></div>
            }
          </div>
        </div>
      }
    </main>`,
})
export class LiveMapComponent implements OnInit {
  private api = inject(ApiService);
  private rt = inject(RealtimeService);
  private router = inject(Router);
  private destroy = inject(DestroyRef);
  data = signal<LiveMap | null>(null);
  t = time;
  label = (s: keyof typeof STATUS) => STATUS[s].label;

  markers = computed<MapMarker[]>(() => {
    const d = this.data();
    if (!d) return [];
    return [
      ...d.errands.map(e => ({
        lat: e.lat, lng: e.lng, label: e.needsAttention ? '⚠' : '📦', id: 'e:' + e.id,
        color: e.needsAttention ? '#c53030' : '#ff6a13', title: `${e.code} · ${STATUS[e.status].label}`,
      })),
      ...d.couriers.map(c => ({
        lat: c.lat, lng: c.lng, label: '🛵', kind: 'courier' as const, id: c.activeErrandId ? 'e:' + c.activeErrandId : undefined,
        color: c.activeErrandId ? '#e85a05' : c.available ? '#138a5b' : '#7c8098',
        title: `${c.fullName}${c.activeErrandCode ? ' · ' + c.activeErrandCode : ''}`,
      })),
    ];
  });

  ngOnInit() {
    this.load();
    // Los eventos llegan muy seguido (pings GPS): se agrupan en una recarga cada 3 s como máximo.
    this.rt.watch('/topic/admin').pipe(debounceTime(3000), takeUntilDestroyed(this.destroy)).subscribe(() => this.load());
  }

  load() {
    this.api.live().subscribe({ next: d => this.data.set(d), error: () => {} });
  }

  open(id: string) {
    if (id.startsWith('e:')) this.router.navigate(['/admin/mandados', id.slice(2)]);
  }
}
