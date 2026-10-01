import { Injectable, NgZone, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';

const MIN_INTERVAL_MS = 10_000;
const HEARTBEAT_MS = 30_000;
const MIN_DISTANCE_M = 30;

/**
 * GPS del mandadero (spec 006). Solo corre mientras está disponible o en servicio (Ley 1581):
 * envía un ping cada ~10 s si se movió ≥ 30 m, o cada 30 s como latido.
 */
@Injectable({ providedIn: 'root' })
export class LocationTrackerService {
  private api = inject(ApiService);
  private zone = inject(NgZone);
  private watchId: number | null = null;
  private last: { lat: number; lng: number; t: number } | null = null;

  readonly running = signal(false);
  readonly error = signal('');
  readonly position = signal<{ lat: number; lng: number } | null>(null);

  start() {
    if (this.watchId !== null) return;
    if (!navigator.geolocation) { this.error.set('Tu dispositivo no permite ubicación'); return; }
    this.error.set('');
    this.running.set(true);
    this.zone.runOutsideAngular(() => {
      this.watchId = navigator.geolocation.watchPosition(
        pos => this.onPosition(pos),
        err => this.zone.run(() => this.error.set(err.code === err.PERMISSION_DENIED
          ? 'Activa el permiso de ubicación para recibir mandados'
          : 'Buscando señal GPS…')),
        { enableHighAccuracy: true, maximumAge: 5000, timeout: 20000 },
      );
    });
  }

  stop() {
    if (this.watchId !== null) navigator.geolocation.clearWatch(this.watchId);
    this.watchId = null;
    this.last = null;
    this.running.set(false);
  }

  private onPosition(pos: GeolocationPosition) {
    const p = { lat: pos.coords.latitude, lng: pos.coords.longitude, t: Date.now() };
    this.zone.run(() => { this.position.set({ lat: p.lat, lng: p.lng }); this.error.set(''); });
    if (this.last) {
      const elapsed = p.t - this.last.t;
      const moved = distanceM(this.last, p);
      if (elapsed < MIN_INTERVAL_MS) return;
      if (moved < MIN_DISTANCE_M && elapsed < HEARTBEAT_MS) return;
    }
    this.last = p;
    this.api.ping(p.lat, p.lng, pos.coords.accuracy).subscribe({
      error: err => {
        // 409: ya no está disponible ni en servicio → dejamos de rastrear.
        if (err?.status === 409) this.zone.run(() => this.stop());
      },
    });
  }
}

function distanceM(a: { lat: number; lng: number }, b: { lat: number; lng: number }): number {
  const R = 6_371_000;
  const rad = (d: number) => (d * Math.PI) / 180;
  const dLat = rad(b.lat - a.lat);
  const dLng = rad(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}
