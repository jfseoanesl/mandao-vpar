import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { compressImage } from '../../core/images';
import { DOCUMENTS, VEHICLES, dateTime } from '../../core/labels';
import { CourierView, DocumentType, VehicleType } from '../../core/models';
import { ToastService } from '../../core/toast.service';

const BASE_DOCS: DocumentType[] = ['CEDULA_FRENTE', 'CEDULA_REVERSO', 'SELFIE'];
const MOTOR_DOCS: DocumentType[] = ['SOAT', 'LICENCIA', 'TARJETA_PROPIEDAD'];
const PAYOUT = { NEQUI: 'Nequi', DAVIPLATA: 'Daviplata', BANCO: 'Cuenta bancaria' } as const;

/** Onboarding del mandadero (spec 009): datos, vehículo, cuenta de pago y documentos. */
@Component({
  selector: 'app-onboarding',
  standalone: true,
  imports: [FormsModule],
  template: `
    <main class="page">
      <h1>Mi perfil</h1>
      @if (me(); as me) {
        <p class="muted">{{ me.fullName }} · {{ me.phone }}</p>
        <div class="alert {{ tone(me) }}">{{ statusText(me) }}</div>

        <form class="card stack mt" (ngSubmit)="save()">
          <h2>Vehículo y pagos</h2>
          <label class="field">¿Cómo te movilizas?
            <select name="vehicle" [(ngModel)]="vehicle">
              @for (v of vehicleKeys; track v) { <option [value]="v">{{ vehicles[v] }}</option> }
            </select>
          </label>
          @if (motorized()) {
            <label class="field">Placa
              <input name="plate" maxlength="8" style="text-transform:uppercase" [(ngModel)]="plate" />
            </label>
          }
          <label class="field">¿Dónde te pagamos?
            <select name="payoutMethod" [(ngModel)]="payoutMethod">
              @for (p of payoutKeys; track p) { <option [value]="p">{{ payout[p] }}</option> }
            </select>
          </label>
          <label class="field">Número de cuenta o celular
            <input name="payoutAccount" inputmode="numeric" maxlength="30" [(ngModel)]="payoutAccount" required />
          </label>
          <button class="btn" [disabled]="busy()">Guardar</button>
        </form>

        <div class="card stack mt">
          <h2>Documentos</h2>
          <small class="muted">Solo el equipo de Mandao puede verlos. Se usan únicamente para verificar tu identidad.</small>
          @for (d of required(); track d) {
            <div class="row">
              <div class="grow">
                <strong>{{ docs[d] }}</strong>
                <div><small class="muted">{{ uploadedAt(me, d) ? '✅ Subido ' + uploadedAt(me, d) : 'Pendiente' }}</small></div>
              </div>
              <label class="btn ghost sm">
                {{ uploadedAt(me, d) ? 'Cambiar' : 'Subir' }}
                <input type="file" hidden [accept]="d === 'SELFIE' ? 'image/*' : 'image/*,application/pdf'"
                       [attr.capture]="d === 'SELFIE' ? 'user' : null" (change)="upload(d, $event)" />
              </label>
            </div>
          }
          @if (uploading()) { <div class="row"><div class="spinner"></div><small>Subiendo…</small></div> }
        </div>
      } @else { <div class="spinner"></div> }
    </main>`,
})
export class OnboardingComponent implements OnInit {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  me = signal<CourierView | null>(null);
  busy = signal(false);
  uploading = signal(false);
  vehicles = VEHICLES;
  vehicleKeys = Object.keys(VEHICLES) as VehicleType[];
  payout = PAYOUT;
  payoutKeys = Object.keys(PAYOUT) as (keyof typeof PAYOUT)[];
  docs = DOCUMENTS;
  vehicle: VehicleType = 'MOTO';
  plate = '';
  payoutMethod: keyof typeof PAYOUT = 'NEQUI';
  payoutAccount = '';

  motorized = () => this.vehicle === 'MOTO' || this.vehicle === 'CARRO';
  required = () => (this.motorized() ? [...BASE_DOCS, ...MOTOR_DOCS] : BASE_DOCS);

  ngOnInit() {
    this.api.courierMe().subscribe({ next: me => this.apply(me), error: e => this.toast.error(e) });
  }

  private apply(me: CourierView) {
    this.me.set(me);
    this.vehicle = me.vehicleType ?? this.vehicle;
    this.plate = me.plate ?? '';
    this.payoutMethod = me.payoutMethod ?? this.payoutMethod;
    this.payoutAccount = me.payoutAccount ?? '';
  }

  save() {
    this.busy.set(true);
    this.api.updateCourier({
      vehicleType: this.vehicle,
      plate: this.motorized() ? this.plate.trim().toUpperCase() : undefined,
      payoutMethod: this.payoutMethod,
      payoutAccount: this.payoutAccount.trim(),
    }).subscribe({
      next: me => { this.busy.set(false); this.apply(me); this.toast.ok('Perfil guardado'); },
      error: e => { this.busy.set(false); this.toast.error(e); },
    });
  }

  async upload(type: DocumentType, ev: Event) {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    this.uploading.set(true);
    const blob = await compressImage(file);
    const named = new File([blob], file.type === 'application/pdf' ? file.name : 'documento.jpg', { type: blob.type });
    this.api.uploadDocument(type, named).subscribe({
      next: me => { this.uploading.set(false); this.me.set(me); this.toast.ok(`${DOCUMENTS[type]} subido`); },
      error: e => { this.uploading.set(false); this.toast.error(e); },
    });
  }

  uploadedAt(me: CourierView, d: DocumentType): string {
    const doc = me.documents.filter(x => x.type === d).at(-1);
    return doc ? dateTime(doc.uploadedAt) : '';
  }

  tone(me: CourierView) {
    return me.status === 'APPROVED' ? 'ok' : me.status === 'REJECTED' ? 'danger' : 'info';
  }

  statusText(me: CourierView): string {
    if (me.status === 'APPROVED') return '✅ Verificado. Ya puedes recibir mandados.';
    if (me.status === 'REJECTED') return `Registro rechazado: ${me.rejectionReason ?? ''}. Corrige y vuelve a subir.`;
    if (!me.vehicleType || !me.payoutAccount) return 'Paso 1: completa tu vehículo y cuenta de pago.';
    if (me.missingDocuments.length) return `Paso 2: faltan ${me.missingDocuments.length} documento(s).`;
    return '⏳ En revisión. Te avisaremos cuando seas aprobado.';
  }
}
