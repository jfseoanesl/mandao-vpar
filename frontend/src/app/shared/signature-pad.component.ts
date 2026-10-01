import { AfterViewInit, Component, ElementRef, output, signal, viewChild } from '@angular/core';

/** Firma en pantalla (dedo o mouse) → PNG. */
@Component({
  selector: 'app-signature-pad',
  standalone: true,
  template: `
    <canvas #canvas class="sig" (pointerdown)="down($event)" (pointermove)="move($event)"
            (pointerup)="up()" (pointerleave)="up()"></canvas>
    <div class="row between mt">
      <button type="button" class="btn ghost sm" (click)="clear()">Borrar</button>
      <button type="button" class="btn sm" [disabled]="!dirty()" (click)="save()">Guardar firma</button>
    </div>`,
  styles: [`.sig { width: 100%; height: 180px; background: #fff; border: 1px dashed var(--line); border-radius: 12px; touch-action: none; }`],
})
export class SignaturePadComponent implements AfterViewInit {
  signed = output<Blob>();
  dirty = signal(false);
  private canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private ctx!: CanvasRenderingContext2D;
  private drawing = false;

  ngAfterViewInit() {
    const c = this.canvas().nativeElement;
    const ratio = window.devicePixelRatio || 1;
    c.width = c.offsetWidth * ratio;
    c.height = c.offsetHeight * ratio;
    this.ctx = c.getContext('2d')!;
    this.ctx.scale(ratio, ratio);
    this.ctx.lineWidth = 2.5;
    this.ctx.lineCap = 'round';
    this.ctx.strokeStyle = '#1b1f3b';
  }

  down(e: PointerEvent) {
    this.drawing = true;
    this.ctx.beginPath();
    this.ctx.moveTo(e.offsetX, e.offsetY);
  }

  move(e: PointerEvent) {
    if (!this.drawing) return;
    this.ctx.lineTo(e.offsetX, e.offsetY);
    this.ctx.stroke();
    this.dirty.set(true);
  }

  up() { this.drawing = false; }

  clear() {
    const c = this.canvas().nativeElement;
    this.ctx.clearRect(0, 0, c.width, c.height);
    this.dirty.set(false);
  }

  save() {
    this.canvas().nativeElement.toBlob(b => b && this.signed.emit(b), 'image/png');
  }
}
