import { AfterViewInit, Component, ElementRef, OnDestroy, effect, input, output, viewChild } from '@angular/core';
import * as L from 'leaflet';
import { TILE_SOURCES, VALLEDUPAR } from '../core/geo.service';

export interface MapMarker {
  lat: number; lng: number; label: string; color?: string; kind?: 'stop' | 'courier'; title?: string; id?: string;
}

/** Envoltorio de Leaflet + OpenStreetMap. Marcadores con divIcon (sin assets externos). */
@Component({
  selector: 'app-map',
  standalone: true,
  template: `<div #host class="map" [class.tall]="tall()"></div>`,
})
export class MapComponent implements AfterViewInit, OnDestroy {
  markers = input<MapMarker[]>([]);
  path = input<{ lat: number; lng: number }[]>([]);
  picking = input(false);
  tall = input(false);
  /** Si es true, no reencuadra al cambiar marcadores (p. ej. tracking en vivo tras el primer encuadre). */
  keepView = input(false);
  pick = output<{ lat: number; lng: number }>();
  markerClick = output<string>();

  private host = viewChild.required<ElementRef<HTMLDivElement>>('host');
  private map?: L.Map;
  private layer = L.layerGroup();
  private fitted = false;
  private tiles?: L.TileLayer;
  private tileErrors = 0;

  constructor() {
    effect(() => {
      const markers = this.markers();
      const path = this.path();
      if (this.map) this.draw(markers, path);
    });
  }

  ngAfterViewInit() {
    this.map = L.map(this.host().nativeElement, { zoomControl: true, attributionControl: true })
      .setView([VALLEDUPAR.lat, VALLEDUPAR.lng], 14);
    this.useTiles(0);
    this.layer.addTo(this.map);
    this.map.on('click', (e: L.LeafletMouseEvent) => {
      if (this.picking()) this.pick.emit({ lat: e.latlng.lat, lng: e.latlng.lng });
    });
    this.draw(this.markers(), this.path());
    setTimeout(() => this.map?.invalidateSize(), 50);
  }

  /** Carga el proveedor de mapa base; si falla repetidamente, pasa al siguiente. */
  private useTiles(index: number) {
    const src = TILE_SOURCES[index];
    if (!src || !this.map) return;
    this.tiles?.remove();
    this.tileErrors = 0;
    let loaded = false;
    this.tiles = L.tileLayer(src.url, {
      maxZoom: src.maxZoom, subdomains: src.subdomains || 'abc', attribution: src.attribution,
    })
      .on('tileload', () => { loaded = true; })
      .on('tileerror', () => {
        if (!loaded && ++this.tileErrors >= 4) this.useTiles(index + 1);
      })
      .addTo(this.map);
    this.tiles.bringToBack();
  }

  ngOnDestroy() {
    this.map?.remove();
  }

  private draw(markers: MapMarker[], path: { lat: number; lng: number }[]) {
    this.layer.clearLayers();
    const bounds: L.LatLngExpression[] = [];
    if (path.length > 1) {
      L.polyline(path.map(p => [p.lat, p.lng] as L.LatLngTuple), { color: '#ff6a13', weight: 4, opacity: .7 })
        .addTo(this.layer);
    }
    for (const m of markers) {
      const courier = m.kind === 'courier';
      const icon = L.divIcon({
        className: '',
        html: `<div class="pin ${courier ? 'courier' : ''}" style="background:${m.color ?? (courier ? '#1b1f3b' : '#ff6a13')}"><span>${escapeHtml(m.label)}</span></div>`,
        iconSize: [30, 30],
        iconAnchor: courier ? [15, 15] : [15, 30],
      });
      const marker = L.marker([m.lat, m.lng], { icon }).addTo(this.layer);
      if (m.title) marker.bindTooltip(escapeHtml(m.title));
      if (m.id) marker.on('click', () => this.markerClick.emit(m.id!));
      bounds.push([m.lat, m.lng]);
    }
    if (bounds.length && (!this.keepView() || !this.fitted)) {
      if (bounds.length === 1) this.map!.setView(bounds[0], 15);
      else this.map!.fitBounds(L.latLngBounds(bounds), { padding: [40, 40], maxZoom: 16 });
      this.fitted = true;
    }
  }
}

function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]!));
}
