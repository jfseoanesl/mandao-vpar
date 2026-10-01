import { Component, DestroyRef, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { money } from '../../core/labels';
import { CourierView, ErrandSummary, ErrandView, OpportunityView } from '../../core/models';
import { RealtimeService } from '../../core/realtime.service';
import { ToastService } from '../../core/toast.service';
import { PushPromptComponent } from '../../shared/push-prompt.component';
import { StatusChipComponent } from '../../shared/ui';
import { LocationTrackerService } from './location-tracker.service';
import { OpportunityCardComponent } from './opportunity-card.component';

const ACTIVE = ['ASSIGNED', 'GOING_TO_ORIGIN', 'IN_PROGRESS', 'DELIVERED'];
const REFRESH_MS = 30_000;

/** Inicio del mandadero: disponibilidad, mandados en curso y oportunidades cercanas (spec 004). */
@Component({
  selector: 'app-courier-home',
  standalone: true,
  imports: [RouterLink, StatusChipComponent, OpportunityCardComponent, PushPromptComponent],
  template: `
    <main class="page">
      @if (me()?.status === 'APPROVED') {
        <app-push-prompt [important]="true"
          text="Activa las notificaciones para recibir mandados aunque tengas el celular bloqueado." />
      }
      @if (me(); as me) {
        @if (me.status !== 'APPROVED') {
          <div class="alert {{ me.status === 'REJECTED' ? 'danger' : 'warn' }}">
            @if (me.status === 'REJECTED') {
              Tu registro fue rechazado: {{ me.rejectionReason }}. Corrige tus datos en tu perfil.
            } @else if (me.missingDocuments.length) {
              Completa tu perfil y documentos para empezar a recibir mandados.
            } @else {
              Tus documentos están en revisión. Te avisaremos al ser aprobado.
            }
            <div class="mt"><a class="btn sm" routerLink="/mandadero/perfil">Ir a mi perfil</a></div>
          </div>
        } @else {
          <div class="card row between">
            <div>
              <strong>{{ me.available ? 'Estás disponible' : 'No estás disponible' }}</strong>
              <div><small class="muted">{{ me.available ? 'Recibirás mandados cercanos' : 'Actívate para recibir mandados' }}</small></div>
              @if (gps.error()) { <div><small class="error-text">{{ gps.error() }}</small></div> }
            </div>
            <button class="btn {{ me.available ? 'dark' : 'ok' }}" [disabled]="busy()" (click)="toggle()">
              {{ me.available ? 'Desconectarme' : 'Conectarme' }}
            </button>
          </div>
        }
      }

      @if (active().length) {
        <h2 class="mt">En curso</h2>
        @for (e of active(); track e.id) {
          <a class="card click row" [routerLink]="['/mandadero/mandados', e.id]" style="margin-bottom:12px">
            <div class="pulse"></div>
            <div class="grow">
              <strong>{{ e.code }}</strong> · <app-status [status]="e.status" />
              <div><small class="muted">{{ e.description }}</small></div>
            </div>
            <strong>{{ money(e.courierPayout) }}</strong>
          </a>
        }
      }

      @if (me()?.status === 'APPROVED') {
        <div class="row between mt">
          <h2>Mandados cerca de ti</h2>
          <button class="btn ghost sm" (click)="loadOpportunities()">Actualizar</button>
        </div>
        @if (!me()?.available) {
          <p class="muted">Conéctate para ver mandados disponibles.</p>
        } @else {
          <div class="stack">
            @for (o of opportunities(); track o.id) {
              <app-opportunity-card [op]="o" (taken)="taken($event)" (changed)="loadOpportunities()" />
            } @empty {
              <div class="card center muted">No hay mandados cerca por ahora. Te avisaremos cuando llegue uno.</div>
            }
          </div>
        }
      }
    </main>`,
})
export class CourierHomeComponent implements OnInit, OnDestroy {
  private api = inject(ApiService);
  private rt = inject(RealtimeService);
  private toast = inject(ToastService);
  private router = inject(Router);
  private destroy = inject(DestroyRef);
  gps = inject(LocationTrackerService);

  me = signal<CourierView | null>(null);
  errands = signal<ErrandSummary[]>([]);
  opportunities = signal<OpportunityView[]>([]);
  busy = signal(false);
  active = computed(() => this.errands().filter(e => ACTIVE.includes(e.status)));
  money = money;
  private timer?: ReturnType<typeof setInterval>;

  ngOnInit() {
    this.api.courierMe().subscribe({ next: me => { this.me.set(me); this.syncGps(); this.loadOpportunities(); }, error: e => this.toast.error(e) });
    this.loadErrands();
    this.rt.watch('/user/queue/offers').pipe(takeUntilDestroyed(this.destroy)).subscribe(ev => {
      if (ev.type === 'NEW_OPPORTUNITY') {
        navigator.vibrate?.([200, 100, 200]);
        this.toast.show('🔔 ¡Nuevo mandado cerca!');
      } else if (ev.type === 'OFFER_COUNTERED') {
        this.toast.show('El cliente te hizo una contraoferta');
      } else if (ev.type.startsWith('ERRAND_')) {
        this.loadErrands();
      }
      this.loadOpportunities();
    });
    this.timer = setInterval(() => this.loadOpportunities(), REFRESH_MS);
  }

  ngOnDestroy() { clearInterval(this.timer); }

  loadOpportunities() {
    if (this.me()?.status !== 'APPROVED' || !this.me()?.available) return;
    this.api.opportunities().subscribe({ next: l => this.opportunities.set(l), error: () => {} });
  }

  private loadErrands() {
    this.api.courierErrands().subscribe({ next: l => { this.errands.set(l); this.syncGps(); }, error: () => {} });
  }

  toggle() {
    const next = !this.me()!.available;
    this.busy.set(true);
    this.api.setAvailability(next).subscribe({
      next: me => { this.busy.set(false); this.me.set(me); this.syncGps(); this.loadOpportunities(); },
      error: e => { this.busy.set(false); this.toast.error(e); },
    });
  }

  taken(e: ErrandView) {
    this.router.navigate(['/mandadero/mandados', e.id]);
  }

  /** El GPS corre solo si está disponible o tiene un mandado en curso. */
  private syncGps() {
    if (this.me()?.available || this.active().length) this.gps.start();
    else this.gps.stop();
  }
}
