import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AddressView, AdminUserView, CompanyView, CourierView, CreateErrandRequest, Complexity, DocumentType, Earnings,
  ErrandStatus, ErrandSummary, ErrandView, EvidenceView, GeoPoint, KpiReport, LiveMap, LivePoint, OfferView,
  OpportunityView, PaymentView, PublicConfig, Quote, RatingTag, Reputation, Tariff, UserView,
} from './models';

/** Cliente HTTP del contrato specs/api/openapi.yaml. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);

  // Público
  publicConfig() { return this.http.get<PublicConfig>('/api/public/config'); }

  // Cliente
  quote(complexity: Complexity, stops: GeoPoint[], estimatedWaitMinutes: number) {
    return this.http.post<Quote>('/api/quotes', { complexity, stops, estimatedWaitMinutes });
  }
  createErrand(body: CreateErrandRequest) { return this.http.post<ErrandView>('/api/errands', body); }
  myErrands() { return this.http.get<ErrandSummary[]>('/api/errands'); }
  errand(id: string) { return this.http.get<ErrandView>(`/api/errands/${id}`); }
  cancel(id: string, reason?: string) { return this.http.post<ErrandView>(`/api/errands/${id}/cancel`, { reason }); }
  confirm(id: string) { return this.http.post<ErrandView>(`/api/errands/${id}/confirm`, {}); }
  rate(id: string, stars: number, tags: RatingTag[], comment?: string) {
    return this.http.post<ErrandView>(`/api/errands/${id}/rating`, { stars, tags, comment });
  }
  attach(id: string, file: Blob, note?: string) {
    const form = new FormData();
    form.append('file', file, 'adjunto.jpg');
    if (note) form.append('note', note);
    return this.http.post<EvidenceView>(`/api/errands/${id}/attachments`, form);
  }
  offers(id: string) { return this.http.get<OfferView[]>(`/api/errands/${id}/offers`); }
  acceptOffer(id: string, offerId: string) { return this.http.post<ErrandView>(`/api/errands/${id}/offers/${offerId}/accept`, {}); }
  rejectOffer(id: string, offerId: string) { return this.http.post<OfferView>(`/api/errands/${id}/offers/${offerId}/reject`, {}); }
  counterOffer(id: string, offerId: string, amount: number) {
    return this.http.post<OfferView>(`/api/errands/${id}/offers/${offerId}/counter`, { amount });
  }
  track(id: string) { return this.http.get<LivePoint[]>(`/api/errands/${id}/track`); }
  reputation(courierId: string) { return this.http.get<Reputation>(`/api/couriers/${courierId}/reputation`); }
  file(id: string): Observable<Blob> { return this.http.get(`/api/files/${id}`, { responseType: 'blob' }); }
  myAddresses() { return this.http.get<AddressView[]>('/api/me/addresses'); }

  // Notificaciones push (spec 012)
  pushKey() { return this.http.get<{ publicKey: string }>('/api/push/key'); }
  subscribePush(sub: PushSubscriptionJSON) { return this.http.post<void>('/api/push/subscriptions', sub); }
  unsubscribePush(endpoint: string) { return this.http.delete<void>('/api/push/subscriptions', { params: { endpoint } }); }

  // Mandadero
  courierMe() { return this.http.get<CourierView>('/api/courier/me'); }
  updateCourier(body: Partial<CourierView>) { return this.http.put<CourierView>('/api/courier/me', body); }
  uploadDocument(type: DocumentType, file: Blob) {
    const form = new FormData();
    form.append('type', type);
    form.append('file', file, file instanceof File ? file.name : 'documento.jpg');
    return this.http.post<CourierView>('/api/courier/documents', form);
  }
  setAvailability(available: boolean) { return this.http.put<CourierView>('/api/courier/availability', { available }); }
  ping(lat: number, lng: number, accuracy?: number) { return this.http.post<void>('/api/courier/location', { lat, lng, accuracy }); }
  opportunities() { return this.http.get<OpportunityView[]>('/api/courier/opportunities'); }
  courierErrands() { return this.http.get<ErrandSummary[]>('/api/courier/errands'); }
  accept(id: string) { return this.http.post<ErrandView>(`/api/courier/errands/${id}/accept`, {}); }
  bid(id: string, amount: number, message?: string) { return this.http.post<OfferView>(`/api/courier/errands/${id}/bid`, { amount, message }); }
  acceptCounter(id: string) { return this.http.post<ErrandView>(`/api/courier/errands/${id}/accept-counter`, {}); }
  onTheWay(id: string) { return this.http.post<ErrandView>(`/api/courier/errands/${id}/on-the-way`, {}); }
  start(id: string, otp: string) { return this.http.post<ErrandView>(`/api/courier/errands/${id}/start`, { otp }); }
  deliver(id: string, otp: string) { return this.http.post<ErrandView>(`/api/courier/errands/${id}/deliver`, { otp }); }
  fail(id: string, reason: string) { return this.http.post<ErrandView>(`/api/courier/errands/${id}/fail`, { reason }); }
  addEvidence(id: string, type: string, opts: { file?: Blob; note?: string; amount?: number; lat?: number; lng?: number }) {
    const form = new FormData();
    form.append('type', type);
    if (opts.file) form.append('file', opts.file, type === 'FIRMA' ? 'firma.png' : 'evidencia.jpg');
    if (opts.note) form.append('note', opts.note);
    if (opts.amount != null) form.append('amount', String(opts.amount));
    if (opts.lat != null) form.append('lat', String(opts.lat));
    if (opts.lng != null) form.append('lng', String(opts.lng));
    return this.http.post<EvidenceView>(`/api/courier/errands/${id}/evidences`, form);
  }
  earnings() { return this.http.get<Earnings>('/api/courier/earnings'); }

  // Empresa
  company() { return this.http.get<CompanyView>('/api/company'); }
  companyMembers() { return this.http.get<UserView[]>('/api/company/members'); }
  addMember(fullName: string, phone: string, password: string) {
    return this.http.post<UserView>('/api/company/members', { fullName, phone, password });
  }
  companyErrands() { return this.http.get<ErrandSummary[]>('/api/company/errands'); }
  addAddress(body: Omit<AddressView, 'id'>) { return this.http.post<AddressView>('/api/company/addresses', body); }
  deleteAddress(id: string) { return this.http.delete<void>(`/api/company/addresses/${id}`); }
  companyReport(month: string) { return this.http.get('/api/company/report', { params: { month }, responseType: 'blob' }); }

  // Administración
  kpis(from?: string, to?: string) {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<KpiReport>('/api/admin/kpis', { params });
  }
  adminErrands(status?: ErrandStatus | '') {
    return this.http.get<ErrandSummary[]>('/api/admin/errands', { params: status ? { status } : {} });
  }
  assign(id: string, courierId: string) { return this.http.post<ErrandView>(`/api/admin/errands/${id}/assign`, { courierId }); }
  adminCancel(id: string, reason: string) { return this.http.post<ErrandView>(`/api/admin/errands/${id}/cancel`, { reason }); }
  adjustPrice(id: string, amount: number) { return this.http.put<ErrandView>(`/api/admin/errands/${id}/price`, { amount }); }
  resolve(id: string) { return this.http.post<ErrandView>(`/api/admin/errands/${id}/resolve`, {}); }
  courierPaid(id: string) { return this.http.post<ErrandView>(`/api/admin/errands/${id}/courier-paid`, {}); }
  exportErrands(from: string, to: string) {
    return this.http.get('/api/admin/export/errands', { params: { from, to }, responseType: 'blob' });
  }
  payments(status?: string) { return this.http.get<PaymentView[]>('/api/admin/payments', { params: status ? { status } : {} }); }
  confirmPayment(id: string, reference: string) { return this.http.post(`/api/admin/payments/${id}/confirm`, { reference }); }
  couriers(status?: string) { return this.http.get<CourierView[]>('/api/admin/couriers', { params: status ? { status } : {} }); }
  approveCourier(id: string) { return this.http.post<CourierView>(`/api/admin/couriers/${id}/approve`, {}); }
  rejectCourier(id: string, reason: string) { return this.http.post<CourierView>(`/api/admin/couriers/${id}/reject`, { reason }); }
  document(id: string): Observable<Blob> { return this.http.get(`/api/admin/documents/${id}`, { responseType: 'blob' }); }
  live() { return this.http.get<LiveMap>('/api/admin/live'); }
  tariff() { return this.http.get<Tariff>('/api/admin/tariff'); }
  updateTariff(t: Tariff) { return this.http.put<Tariff>('/api/admin/tariff', t); }
  companies() { return this.http.get<CompanyView[]>('/api/admin/companies'); }
  createCompany(body: { name: string; nit?: string; plan: string; adminFullName: string; adminPhone: string; adminPassword: string }) {
    return this.http.post<CompanyView>('/api/admin/companies', body);
  }
  users() { return this.http.get<AdminUserView[]>('/api/admin/users'); }
  resetPassword(id: string) { return this.http.post<{ temporaryPassword: string }>(`/api/admin/users/${id}/reset-password`, {}); }
  setUserActive(id: string, active: boolean) { return this.http.post<AdminUserView>(`/api/admin/users/${id}/active`, { active }); }
}

/** Descarga un Blob como archivo. */
export function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
