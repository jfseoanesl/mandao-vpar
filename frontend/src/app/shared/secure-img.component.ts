import { Component, OnDestroy, effect, inject, input, signal } from '@angular/core';
import { ApiService } from '../core/api.service';

/**
 * Muestra un archivo privado: lo descarga con el token (los <img> no envían Authorization)
 * y lo presenta como object URL. Los PDF se abren en pestaña nueva.
 */
@Component({
  selector: 'app-secure-img',
  standalone: true,
  template: `
    @if (url(); as u) {
      @if (pdf()) {
        <a class="btn ghost sm" [href]="u" target="_blank" rel="noopener">📄 Ver PDF</a>
      } @else {
        <a [href]="u" target="_blank" rel="noopener"><img [src]="u" [alt]="alt()" /></a>
      }
    } @else if (failed()) {
      <small class="muted">No disponible</small>
    } @else {
      <div class="spinner"></div>
    }`,
})
export class SecureImgComponent implements OnDestroy {
  private api = inject(ApiService);
  fileId = input.required<string>();
  /** 'evidence' usa /api/files; 'document' usa /api/admin/documents. */
  source = input<'evidence' | 'document'>('evidence');
  alt = input('Evidencia');
  url = signal<string | null>(null);
  pdf = signal(false);
  failed = signal(false);

  constructor() {
    effect(() => {
      const id = this.fileId();
      const req = this.source() === 'document' ? this.api.document(id) : this.api.file(id);
      req.subscribe({
        next: blob => {
          this.revoke();
          this.pdf.set(blob.type === 'application/pdf');
          this.url.set(URL.createObjectURL(blob));
        },
        error: () => this.failed.set(true),
      });
    }, { allowSignalWrites: true });
  }

  ngOnDestroy() { this.revoke(); }

  private revoke() {
    const u = this.url();
    if (u) URL.revokeObjectURL(u);
  }
}
