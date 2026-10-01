import { Injectable, inject, signal } from '@angular/core';
import { SwPush } from '@angular/service-worker';
import { firstValueFrom } from 'rxjs';
import { ApiService } from './api.service';

export type PushState = 'unsupported' | 'ios-install' | 'off' | 'on' | 'denied';

/**
 * Notificaciones push (spec 012) con el service worker de Angular, que ya sabe mostrar la
 * notificación y abrir la URL al tocarla (payload {notification: {..., data: {onActionClick}}}).
 */
@Injectable({ providedIn: 'root' })
export class PushService {
  private sw = inject(SwPush);
  private api = inject(ApiService);
  readonly state = signal<PushState>('off');
  readonly busy = signal(false);

  constructor() {
    this.refreshState();
  }

  /** Llamar tras iniciar sesión: re-asocia la suscripción existente de este dispositivo al usuario actual. */
  async sync() {
    this.refreshState();
    if (!this.sw.isEnabled || Notification.permission !== 'granted') return;
    const sub = await firstValueFrom(this.sw.subscription).catch(() => null);
    if (sub) {
      await firstValueFrom(this.api.subscribePush(sub.toJSON())).catch(() => undefined);
      this.state.set('on');
    }
  }

  /** Debe llamarse desde un toque del usuario (requisito de iOS y buena práctica). */
  async enable(): Promise<boolean> {
    if (!this.sw.isEnabled) return false;
    this.busy.set(true);
    try {
      const { publicKey } = await firstValueFrom(this.api.pushKey());
      const sub = await this.sw.requestSubscription({ serverPublicKey: publicKey });
      await firstValueFrom(this.api.subscribePush(sub.toJSON()));
      this.state.set('on');
      return true;
    } catch {
      this.refreshState();
      return false;
    } finally {
      this.busy.set(false);
    }
  }

  /** Al cerrar sesión: este dispositivo deja de recibir notificaciones de esa cuenta. */
  async disable() {
    if (!this.sw.isEnabled) return;
    const sub = await firstValueFrom(this.sw.subscription).catch(() => null);
    if (!sub) return;
    await firstValueFrom(this.api.unsubscribePush(sub.endpoint)).catch(() => undefined);
    await this.sw.unsubscribe().catch(() => undefined);
    this.state.set('off');
  }

  private refreshState() {
    const ios = /iphone|ipad|ipod/i.test(navigator.userAgent);
    const standalone = matchMedia('(display-mode: standalone)').matches || (navigator as any).standalone === true;
    if (!('Notification' in window) || !('PushManager' in window)) {
      // En iPhone, el push solo existe con la PWA instalada en la pantalla de inicio (iOS ≥ 16.4).
      this.state.set(ios && !standalone ? 'ios-install' : 'unsupported');
      return;
    }
    if (!this.sw.isEnabled) { this.state.set('unsupported'); return; }
    if (Notification.permission === 'denied') { this.state.set('denied'); return; }
    if (this.state() !== 'on') this.state.set('off');
  }
}
