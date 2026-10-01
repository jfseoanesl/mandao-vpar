import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, of } from 'rxjs';
import { GeoPoint } from './models';

export const VALLEDUPAR: GeoPoint = { lat: 10.4631, lng: -73.2532 };

export interface TileSource { url: string; subdomains: string; attribution: string; maxZoom: number; }

/**
 * Mapas base. Para el piloto se usan los servidores de OpenStreetMap (tráfico bajo, con atribución).
 * Para escalar, agrega primero un proveedor con API key (MapTiler, Stadia…) y su dominio en la CSP
 * del Caddyfile (img-src y connect-src): el mapa pasa al siguiente si uno falla.
 */
export const TILE_SOURCES: TileSource[] = [
  {
    url: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
    subdomains: '',
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    maxZoom: 19,
  },
];

export interface Place { label: string; lat: number; lng: number; }

/** Geocodificación con Nominatim (OpenStreetMap), acotada a Valledupar. */
@Injectable({ providedIn: 'root' })
export class GeoService {
  private http = inject(HttpClient);
  private base = 'https://nominatim.openstreetmap.org';

  search(q: string): Observable<Place[]> {
    if (q.trim().length < 3) return of([]);
    const params = {
      q: `${q}, Valledupar`, format: 'jsonv2', limit: '6', countrycodes: 'co', 'accept-language': 'es',
      viewbox: '-73.40,10.60,-73.10,10.33', bounded: '1',
    };
    return this.http.get<any[]>(`${this.base}/search`, { params }).pipe(
      map(rows => rows.map(r => ({ label: shorten(r.display_name), lat: +r.lat, lng: +r.lon }))),
    );
  }

  reverse(p: GeoPoint): Observable<string> {
    const params = { lat: String(p.lat), lon: String(p.lng), format: 'jsonv2', 'accept-language': 'es', zoom: '18' };
    return this.http.get<any>(`${this.base}/reverse`, { params }).pipe(
      map(r => (r?.display_name ? shorten(r.display_name) : `${p.lat.toFixed(5)}, ${p.lng.toFixed(5)}`)),
    );
  }

  current(highAccuracy = true): Promise<GeoPoint & { accuracy?: number }> {
    return new Promise((resolve, reject) => {
      if (!navigator.geolocation) return reject(new Error('Tu dispositivo no permite ubicación'));
      navigator.geolocation.getCurrentPosition(
        pos => resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude, accuracy: pos.coords.accuracy }),
        () => reject(new Error('No pudimos obtener tu ubicación. Revisa los permisos.')),
        { enableHighAccuracy: highAccuracy, timeout: 12000, maximumAge: 10000 },
      );
    });
  }
}

function shorten(name: string): string {
  return name.split(',').slice(0, 3).join(',').trim();
}
