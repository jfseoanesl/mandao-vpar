import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AnalyticsService } from '../../core/analytics.service';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { CATEGORIES } from '../../core/labels';

/** Landing de validación (MVP 0 + MVP 1): propuesta de valor, categorías y dos canales: app y WhatsApp. */
@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [RouterLink],
  template: `
    <header class="topbar">
      <div class="inner">
        <span class="logo">Man<b>dao</b></span>
        <span class="grow"></span>
        @if (auth.user()) {
          <a class="btn sm" [routerLink]="auth.homeFor()">Ir a mi cuenta</a>
        } @else {
          <a class="btn ghost sm" routerLink="/entrar">Entrar</a>
        }
      </div>
    </header>

    <main class="page">
      <section class="hero">
        <span class="chip">📍 Valledupar</span>
        <h1>Lo que tengas que hacer,<br><span class="accent">lo hacemos por ti.</span></h1>
        <p class="lead">Compras, envíos, filas, trámites y diligencias. Un mandadero verificado lo resuelve
          y te envía <strong>evidencias</strong> en tiempo real: fotos, factura, firma y ubicación.</p>
        <div class="stack">
          <a class="btn block" routerLink="/registro" (click)="analytics.track('signup_started', 'hero')">Pide tu primer Mandao</a>
          @if (whatsapp()) {
            <a class="btn ghost block" [href]="waLink()" target="_blank" rel="noopener" (click)="analytics.track('whatsapp_click', 'hero')">
              💬 Prefiero pedirlo por WhatsApp
            </a>
          }
        </div>
      </section>

      <h2 class="mt">¿Qué necesitas hacer hoy?</h2>
      <div class="cat-grid">
        @for (c of categories; track c.key) {
          <a class="cat" routerLink="/registro" (click)="analytics.track('category_selected', c.key)">
            <span class="emoji">{{ c.emoji }}</span>
            <strong>{{ c.title }}</strong>
            <small class="muted">{{ c.hint }}</small>
          </a>
        }
      </div>

      <section class="card mt">
        <h2>Así funciona</h2>
        <ol class="steps">
          <li><strong>Cuéntanos el mandado</strong> con tus palabras, fotos y direcciones.</li>
          <li><strong>Conoce el precio</strong> antes de confirmar. Sin sorpresas.</li>
          <li><strong>Un mandadero verificado</strong> lo toma y lo sigues en el mapa.</li>
          <li><strong>Recibe evidencias</strong> y entrega el código de finalización solo cuando esté listo.</li>
        </ol>
      </section>

      <section class="grid two mt">
        <div class="card">
          <h3>🔒 Confianza primero</h3>
          <p class="muted">Mandaderos con cédula, selfie y documentos verificados. Códigos de inicio y fin: el servicio solo se cierra con tu código.</p>
        </div>
        <div class="card">
          <h3>🛒 Bolsa de compras</h3>
          <p class="muted">Autoriza un máximo, el mandadero sube la factura y te devolvemos lo que no se gaste.</p>
        </div>
        <div class="card">
          <h3>🏢 Mandao Empresas</h3>
          <p class="muted">No contrate un mensajero: solicítelo cuando lo necesite. Historial, reportes y comprobantes.</p>
          <a [href]="waLink('Hola, quiero información de Mandao Empresas')" target="_blank" rel="noopener"
             (click)="analytics.track('whatsapp_click', 'empresas')">Hablar con ventas →</a>
        </div>
        <div class="card">
          <h3>🛵 ¿Quieres ser mandadero?</h3>
          <p class="muted">Genera ingresos con horarios flexibles en tu ciudad.</p>
          <a routerLink="/registro" [queryParams]="{ rol: 'mandadero' }">Regístrate como mandadero →</a>
        </div>
      </section>

      <p class="center mt"><small class="muted">Mandao · Valledupar, Cesar · <a routerLink="/terminos">Términos y condiciones</a> · <a routerLink="/privacidad">Tratamiento de datos</a></small></p>
    </main>`,
  styles: [`
    .hero { padding: 24px 0 8px; display: flex; flex-direction: column; gap: 12px; }
    .hero h1 { font-size: 2.1rem; letter-spacing: -1px; }
    .accent { color: var(--brand); }
    .lead { font-size: 1.05rem; color: var(--ink-2); }
    .steps { margin: 0; padding-left: 20px; display: flex; flex-direction: column; gap: 8px; }
    @media (min-width: 900px) { .hero h1 { font-size: 2.8rem; } }
  `],
})
export class LandingComponent implements OnInit {
  auth = inject(AuthService);
  analytics = inject(AnalyticsService);
  private api = inject(ApiService);
  categories = CATEGORIES;
  whatsapp = signal<string | null>(null);

  ngOnInit() {
    this.analytics.track('landing_view');
    this.api.publicConfig().subscribe({ next: c => this.whatsapp.set(c.whatsapp), error: () => {} });
  }

  waLink(text = 'Hola Mandao, necesito que me hagan un mandado') {
    return `https://wa.me/${this.whatsapp()}?text=${encodeURIComponent(text)}`;
  }
}
