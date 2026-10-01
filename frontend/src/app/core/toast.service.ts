import { Injectable, signal } from '@angular/core';
import { errorMessage } from './labels';

export interface Toast { id: number; text: string; kind: 'info' | 'ok' | 'error'; }

@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly toasts = signal<Toast[]>([]);
  private seq = 0;

  show(text: string, kind: Toast['kind'] = 'info', ms = 3500) {
    const id = ++this.seq;
    this.toasts.update(t => [...t, { id, text, kind }]);
    setTimeout(() => this.toasts.update(t => t.filter(x => x.id !== id)), ms);
  }

  ok(text: string) { this.show(text, 'ok'); }
  error(err: unknown) { this.show(typeof err === 'string' ? err : errorMessage(err), 'error', 5000); }
}
