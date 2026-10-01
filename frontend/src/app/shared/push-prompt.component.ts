import { Component, inject, input } from '@angular/core';
import { PushService } from '../core/push.service';
import { ToastService } from '../core/toast.service';

/** Invitación a activar notificaciones; se oculta cuando ya están activas o no aplican. */
@Component({
  selector: 'app-push-prompt',
  standalone: true,
  template: `
    @switch (push.state()) {
      @case ('off') {
        <div class="alert info row between wrap" style="margin-bottom:12px">
          <span class="grow">🔔 {{ text() }}</span>
          <button class="btn sm" [disabled]="push.busy()" (click)="enable()">Activar</button>
        </div>
      }
      @case ('ios-install') {
        <div class="alert info" style="margin-bottom:12px">
          🔔 Para recibir avisos en iPhone, instala Mandao: toca <strong>Compartir</strong> → <strong>Agregar a inicio</strong>
          y ábrelo desde el ícono.
        </div>
      }
      @case ('denied') {
        @if (important()) {
          <div class="alert warn" style="margin-bottom:12px">
            🔕 Bloqueaste las notificaciones. Actívalas en la configuración del navegador para este sitio.
          </div>
        }
      }
    }`,
})
export class PushPromptComponent {
  push = inject(PushService);
  private toast = inject(ToastService);
  text = input('Activa las notificaciones para enterarte de tu mandado aunque no tengas la app abierta.');
  /** Si es true, también se avisa cuando el usuario las bloqueó (p. ej. mandaderos). */
  important = input(false);

  async enable() {
    const ok = await this.push.enable();
    if (ok) this.toast.ok('Notificaciones activadas');
    else if (this.push.state() === 'denied') this.toast.error('Permiso denegado');
    else this.toast.error('No se pudieron activar las notificaciones');
  }
}
