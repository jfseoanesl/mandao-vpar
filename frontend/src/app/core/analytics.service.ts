import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

const UTM_KEY = 'mandao_utm';

/** Embudo de validación: captura el canal de adquisición (?utm_source / ?ref) y envía eventos permitidos. */
@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private http = inject(HttpClient);

  captureSource() {
    const params = new URLSearchParams(location.search);
    const src = params.get('utm_source') ?? params.get('ref');
    if (src) {
      try { localStorage.setItem(UTM_KEY, src.slice(0, 60)); } catch { /* modo privado */ }
    }
  }

  source(): string | undefined {
    try { return localStorage.getItem(UTM_KEY) ?? undefined; } catch { return undefined; }
  }

  track(name: 'landing_view' | 'whatsapp_click' | 'signup_started' | 'install_prompt' | 'category_selected', props?: string) {
    this.http.post('/api/events', { name, props, utmSource: this.source() }).subscribe({ error: () => {} });
  }
}
