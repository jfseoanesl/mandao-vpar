import { Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { TAGS } from '../../core/labels';
import { ErrandView, RatingTag } from '../../core/models';
import { ToastService } from '../../core/toast.service';

/** Calificación con etiquetas (spec 008, Mandao.md §15). */
@Component({
  selector: 'app-rating-panel',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="card stack">
      <h2>¿Cómo te fue?</h2>
      <div class="row" role="radiogroup" aria-label="Calificación" style="gap:4px">
        @for (n of [1, 2, 3, 4, 5]; track n) {
          <button type="button" class="btn link" style="font-size:34px" [attr.aria-label]="n + ' estrellas'"
                  (click)="stars.set(n)">{{ n <= stars() ? '★' : '☆' }}</button>
        }
      </div>
      <div class="row wrap" style="gap:6px">
        @for (t of tagKeys; track t) {
          <button type="button" class="chip select" [class.on]="tags().includes(t)" (click)="toggle(t)">{{ tagLabel[t] }}</button>
        }
      </div>
      <textarea name="comment" maxlength="500" placeholder="Comentario (opcional)" [(ngModel)]="comment"></textarea>
      <button type="button" class="btn block" [disabled]="!stars() || busy()" (click)="send()">Enviar calificación</button>
    </div>`,
})
export class RatingPanelComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  errand = input.required<ErrandView>();
  updated = output<ErrandView>();
  tagKeys = Object.keys(TAGS) as RatingTag[];
  tagLabel = TAGS;
  stars = signal(0);
  tags = signal<RatingTag[]>([]);
  busy = signal(false);
  comment = '';

  toggle(t: RatingTag) {
    this.tags.update(l => (l.includes(t) ? l.filter(x => x !== t) : [...l, t]));
  }

  send() {
    this.busy.set(true);
    this.api.rate(this.errand().id, this.stars(), this.tags(), this.comment.trim() || undefined).subscribe({
      next: e => { this.busy.set(false); this.toast.ok('¡Gracias por calificar!'); this.updated.emit(e); },
      error: e => { this.busy.set(false); this.toast.error(e); },
    });
  }
}
