import { Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { GeoService } from '../../core/geo.service';
import { compressImage } from '../../core/images';
import { money } from '../../core/labels';
import { EvidenceView } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { SignaturePadComponent } from '../../shared/signature-pad.component';

type Kind = 'FOTO' | 'FACTURA' | 'FIRMA' | 'NOTA';

/** Captura de evidencias (spec 005): cada una con hora y GPS del momento. */
@Component({
  selector: 'app-evidence-capture',
  standalone: true,
  imports: [FormsModule, SignaturePadComponent],
  template: `
    <div class="card stack">
      <h2>Agregar evidencia</h2>
      <div class="row wrap" style="gap:6px">
        <button type="button" class="chip select" [class.on]="kind() === 'FOTO'" (click)="kind.set('FOTO')">📸 Foto</button>
        <button type="button" class="chip select" [class.on]="kind() === 'FACTURA'" (click)="kind.set('FACTURA')">🧾 Factura</button>
        <button type="button" class="chip select" [class.on]="kind() === 'FIRMA'" (click)="kind.set('FIRMA')">✍️ Firma</button>
        <button type="button" class="chip select" [class.on]="kind() === 'NOTA'" (click)="kind.set('NOTA')">📝 Nota</button>
      </div>

      @switch (kind()) {
        @case ('FOTO') {
          <label class="btn ghost block">📷 Tomar foto
            <input type="file" accept="image/*" capture="environment" hidden (change)="photo($event)" />
          </label>
          <input name="pnote" maxlength="500" placeholder="Descripción (opcional)" [(ngModel)]="note" />
        }
        @case ('FACTURA') {
          <label class="field">Valor pagado
            @if (budget() > 0) { <span class="hint">Bolsa disponible: {{ money(budget()) }}</span> }
            <input name="amount" type="number" min="0" step="100" [(ngModel)]="amount" />
          </label>
          <label class="btn ghost block" [class.disabled]="!amount">📷 Foto de la factura
            <input type="file" accept="image/*,application/pdf" capture="environment" hidden [disabled]="!amount" (change)="invoice($event)" />
          </label>
        }
        @case ('FIRMA') {
          <small class="muted">Pide a quien recibe que firme aquí.</small>
          <app-signature-pad (signed)="signature($event)" />
        }
        @case ('NOTA') {
          <textarea name="note" maxlength="500" placeholder="Ej.: El radicado es 2024-1234. Me atendió la Sra. Pérez." [(ngModel)]="note"></textarea>
          <button type="button" class="btn" [disabled]="!note.trim() || busy()" (click)="textNote()">Guardar nota</button>
        }
      }
      @if (busy()) { <div class="row"><div class="spinner"></div><small>Enviando evidencia…</small></div> }
    </div>`,
})
export class EvidenceCaptureComponent {
  private api = inject(ApiService);
  private geo = inject(GeoService);
  private toast = inject(ToastService);
  errandId = input.required<string>();
  budget = input(0);
  added = output<EvidenceView>();
  kind = signal<Kind>('FOTO');
  busy = signal(false);
  note = '';
  amount: number | null = null;
  money = money;

  async photo(ev: Event) {
    const file = this.take(ev);
    if (file) await this.send('FOTO', { file: await compressImage(file), note: this.note.trim() || undefined });
  }

  async invoice(ev: Event) {
    const file = this.take(ev);
    if (file) await this.send('FACTURA', { file: await compressImage(file), amount: Number(this.amount) });
  }

  async signature(blob: Blob) {
    await this.send('FIRMA', { file: blob });
  }

  async textNote() {
    await this.send('NOTA', { note: this.note.trim() });
  }

  private take(ev: Event): File | undefined {
    const input = ev.target as HTMLInputElement;
    const f = input.files?.[0];
    input.value = '';
    return f;
  }

  private async send(type: Kind, opts: { file?: Blob; note?: string; amount?: number }) {
    this.busy.set(true);
    // La ubicación es best-effort: la evidencia se guarda aunque no haya GPS.
    const pos = await this.geo.current().catch(() => null);
    this.api.addEvidence(this.errandId(), type, { ...opts, lat: pos?.lat, lng: pos?.lng }).subscribe({
      next: e => {
        this.busy.set(false);
        this.note = '';
        this.amount = null;
        this.toast.ok('Evidencia guardada');
        this.added.emit(e);
      },
      error: e => { this.busy.set(false); this.toast.error(e); },
    });
  }
}
