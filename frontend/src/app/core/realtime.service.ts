import { Injectable, NgZone, inject } from '@angular/core';
import { Client, StompSubscription } from '@stomp/stompjs';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';
import { RealtimeEvent } from './models';

/**
 * Cliente STOMP sobre WebSocket (/ws). El JWT viaja en el frame CONNECT.
 * Se reconecta solo; las suscripciones se rehacen al reconectar.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private auth = inject(AuthService);
  private zone = inject(NgZone);
  private client: Client | null = null;
  private subs = new Map<string, { handlers: Set<(e: RealtimeEvent) => void>; stomp?: StompSubscription }>();

  watch(destination: string): Observable<RealtimeEvent> {
    return new Observable<RealtimeEvent>(observer => {
      const handler = (e: RealtimeEvent) => this.zone.run(() => observer.next(e));
      let entry = this.subs.get(destination);
      if (!entry) {
        entry = { handlers: new Set() };
        this.subs.set(destination, entry);
      }
      entry.handlers.add(handler);
      this.ensureClient();
      this.subscribeIfConnected(destination);
      return () => {
        const current = this.subs.get(destination);
        if (!current) return;
        current.handlers.delete(handler);
        if (current.handlers.size === 0) {
          current.stomp?.unsubscribe();
          this.subs.delete(destination);
        }
        if (this.subs.size === 0) this.disconnect();
      };
    });
  }

  private ensureClient() {
    if (this.client) return;
    const proto = location.protocol === 'https:' ? 'wss' : 'ws';
    this.client = new Client({
      brokerURL: `${proto}://${location.host}/ws`,
      reconnectDelay: 4000,
      heartbeatIncoming: 20000,
      heartbeatOutgoing: 20000,
      beforeConnect: async () => {
        if (!this.auth.accessToken()) await this.auth.refresh();
        this.client!.connectHeaders = { Authorization: `Bearer ${this.auth.accessToken() ?? ''}` };
      },
      onConnect: () => {
        for (const [dest, entry] of this.subs) {
          entry.stomp = undefined;
          this.subscribeIfConnected(dest);
        }
      },
      // El token pudo expirar: se renueva antes del próximo intento.
      onStompError: () => { void this.auth.refresh(); },
    });
    this.zone.runOutsideAngular(() => this.client!.activate());
  }

  private subscribeIfConnected(dest: string) {
    const entry = this.subs.get(dest);
    if (!this.client?.connected || !entry || entry.stomp) return;
    entry.stomp = this.client.subscribe(dest, msg => {
      let event: RealtimeEvent;
      try { event = JSON.parse(msg.body); } catch { return; }
      entry.handlers.forEach(h => h(event));
    });
  }

  private disconnect() {
    void this.client?.deactivate();
    this.client = null;
  }
}
