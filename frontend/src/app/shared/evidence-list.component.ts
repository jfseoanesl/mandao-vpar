import { Component, input } from '@angular/core';
import { EVIDENCE, money, time } from '../core/labels';
import { EvidenceView } from '../core/models';
import { SecureImgComponent } from './secure-img.component';

/** Evidencias del mandado: el diferenciador de confianza (Mandao.md §10). */
@Component({
  selector: 'app-evidence-list',
  standalone: true,
  imports: [SecureImgComponent],
  template: `
    @if (!items().length) {
      <p class="muted">Aún no hay evidencias.</p>
    }
    <div class="stack">
      @for (e of items(); track e.id) {
        <div class="row" style="align-items:flex-start">
          @if (e.hasFile) {
            <div style="width:88px;flex:none" class="thumbs"><app-secure-img [fileId]="e.id" [alt]="label(e.type)" /></div>
          }
          <div class="grow">
            <strong>{{ label(e.type) }}</strong> <small class="muted">· {{ time(e.createdAt) }}</small>
            @if (e.amount != null) { <div>Valor: <strong>{{ money(e.amount) }}</strong></div> }
            @if (e.note) { <div>{{ e.note }}</div> }
            @if (e.lat != null) {
              <small><a [href]="'https://www.openstreetmap.org/?mlat=' + e.lat + '&mlon=' + e.lng + '#map=18/' + e.lat + '/' + e.lng"
                        target="_blank" rel="noopener">📍 Ver ubicación</a></small>
            }
          </div>
        </div>
      }
    </div>`,
})
export class EvidenceListComponent {
  items = input<EvidenceView[]>([]);
  label = (t: string) => EVIDENCE[t] ?? t;
  money = money;
  time = time;
}
