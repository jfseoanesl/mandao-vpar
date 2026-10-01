import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, saveBlob } from '../../core/api.service';
import { GeoService } from '../../core/geo.service';
import { dateTime, money } from '../../core/labels';
import { AddressView, CompanyView, ErrandSummary, UserView } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { MapComponent } from '../../shared/map.component';
import { StatusChipComponent } from '../../shared/ui';

/** Cuenta empresa (spec 011): usuarios, direcciones frecuentes, historial y reporte mensual. */
@Component({
  selector: 'app-company',
  standalone: true,
  imports: [FormsModule, RouterLink, MapComponent, StatusChipComponent],
  template: `
    <main class="page">
      @if (company(); as c) {
        <h1>🏢 {{ c.name }}</h1>
        <p class="muted">{{ c.nit ? 'NIT ' + c.nit + ' · ' : '' }}Plan {{ c.plan }} · {{ money(c.monthlyFee) }}/mes · {{ c.members }} usuario(s)</p>
      }

      <div class="card stack">
        <h2>Reporte mensual</h2>
        <div class="row">
          <input type="month" class="grow" name="month" [(ngModel)]="month" />
          <button class="btn sm" (click)="report()">Descargar CSV</button>
        </div>
      </div>

      <h2 class="mt">Mandados de la empresa</h2>
      <div class="card list">
        @for (e of errands(); track e.id) {
          <a class="list-item" [routerLink]="['/app/mandados', e.id]" style="color:inherit">
            <div class="grow">
              <strong>{{ e.code }}</strong> <small class="muted">· {{ e.clientName }} · {{ dt(e.createdAt) }}</small>
              <div><small>{{ e.description }}</small></div>
            </div>
            <div style="text-align:right"><app-status [status]="e.status" /><div><small>{{ money(e.totalPrice) }}</small></div></div>
          </a>
        } @empty { <p class="muted">Sin mandados todavía.</p> }
      </div>

      <h2 class="mt">Usuarios</h2>
      <div class="card stack">
        @for (m of members(); track m.id) {
          <div class="kv"><span>{{ m.fullName }}</span><span>{{ m.phone }}</span></div>
        }
        <div class="divider"></div>
        <form class="stack" (ngSubmit)="addMember()">
          <strong>Agregar usuario</strong>
          <input name="mName" placeholder="Nombre completo" [(ngModel)]="mName" required />
          <input name="mPhone" placeholder="Celular" inputmode="numeric" maxlength="10" [(ngModel)]="mPhone" required />
          <input name="mPass" type="password" placeholder="Contraseña inicial" autocomplete="new-password" [(ngModel)]="mPass" required />
          <button class="btn sm" [disabled]="busy()">Agregar</button>
        </form>
      </div>

      <h2 class="mt">Direcciones frecuentes</h2>
      <div class="card stack">
        @for (a of addresses(); track a.id) {
          <div class="row">
            <div class="grow"><strong>{{ a.label }}</strong><div><small class="muted">{{ a.address }}</small></div></div>
            <button class="btn ghost sm" (click)="removeAddress(a)">Quitar</button>
          </div>
        }
        <div class="divider"></div>
        <strong>Nueva dirección</strong>
        <small class="muted">Toca el mapa para marcarla.</small>
        <app-map [markers]="point() ? [{ lat: point()!.lat, lng: point()!.lng, label: '★' }] : []" [picking]="true" (pick)="picked($event)" />
        <input name="aLabel" placeholder="Nombre (ej.: Bodega, Banco principal)" maxlength="60" [(ngModel)]="aLabel" />
        <input name="aAddress" placeholder="Dirección" maxlength="250" [(ngModel)]="aAddress" />
        <button class="btn sm" [disabled]="!point() || !aLabel.trim() || busy()" (click)="addAddress()">Guardar dirección</button>
      </div>
    </main>`,
})
export class CompanyComponent implements OnInit {
  private api = inject(ApiService);
  private geo = inject(GeoService);
  private toast = inject(ToastService);
  company = signal<CompanyView | null>(null);
  members = signal<UserView[]>([]);
  errands = signal<ErrandSummary[]>([]);
  addresses = signal<AddressView[]>([]);
  point = signal<{ lat: number; lng: number } | null>(null);
  busy = signal(false);
  month = new Date().toISOString().slice(0, 7);
  mName = ''; mPhone = ''; mPass = '';
  aLabel = ''; aAddress = '';
  money = money;
  dt = dateTime;

  ngOnInit() {
    this.api.company().subscribe({ next: c => this.company.set(c), error: e => this.toast.error(e) });
    this.api.companyMembers().subscribe({ next: m => this.members.set(m), error: () => {} });
    this.api.companyErrands().subscribe({ next: l => this.errands.set(l), error: () => {} });
    this.loadAddresses();
  }

  private loadAddresses() {
    this.api.myAddresses().subscribe({ next: a => this.addresses.set(a), error: () => {} });
  }

  report() {
    this.api.companyReport(this.month).subscribe({
      next: b => saveBlob(b, `mandao-${this.month}.csv`),
      error: e => this.toast.error(e),
    });
  }

  addMember() {
    this.busy.set(true);
    this.api.addMember(this.mName.trim(), this.mPhone.trim(), this.mPass).subscribe({
      next: u => {
        this.busy.set(false);
        this.members.update(l => [...l, u]);
        this.mName = this.mPhone = this.mPass = '';
        this.toast.ok('Usuario agregado');
      },
      error: e => { this.busy.set(false); this.toast.error(e); },
    });
  }

  picked(p: { lat: number; lng: number }) {
    this.point.set(p);
    this.geo.reverse(p).subscribe({ next: a => (this.aAddress = a), error: () => {} });
  }

  addAddress() {
    const p = this.point()!;
    this.busy.set(true);
    this.api.addAddress({ label: this.aLabel.trim(), address: this.aAddress.trim() || 'Punto en el mapa', lat: p.lat, lng: p.lng })
      .subscribe({
        next: a => {
          this.busy.set(false);
          this.addresses.update(l => [...l, a]);
          this.aLabel = this.aAddress = '';
          this.point.set(null);
        },
        error: e => { this.busy.set(false); this.toast.error(e); },
      });
  }

  removeAddress(a: AddressView) {
    this.api.deleteAddress(a.id).subscribe({
      next: () => this.addresses.update(l => l.filter(x => x.id !== a.id)),
      error: e => this.toast.error(e),
    });
  }
}
