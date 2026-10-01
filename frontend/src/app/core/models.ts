// Tipos del contrato specs/api/openapi.yaml
export type Role = 'CLIENT' | 'COURIER' | 'ADMIN' | 'COMPANY_ADMIN';
export type ErrandCategory = 'ENVIO' | 'COMPRA' | 'DILIGENCIA' | 'BUSCAR_COMPRAR' | 'RECOGER_ENTREGAR' | 'EMPRESARIAL' | 'OTRO';
export type Complexity = 'SENCILLA' | 'MEDIA' | 'COMPLEJA';
export type PricingMode = 'FIXED' | 'NEGOTIABLE';
export type PaymentMethod = 'EFECTIVO' | 'NEQUI' | 'DAVIPLATA' | 'TRANSFERENCIA';
export type ErrandStatus = 'NEGOTIATING' | 'SEARCHING' | 'ASSIGNED' | 'GOING_TO_ORIGIN' | 'IN_PROGRESS'
  | 'DELIVERED' | 'COMPLETED' | 'RATED' | 'CANCELLED' | 'FAILED';
export type EvidenceType = 'FOTO' | 'FACTURA' | 'FIRMA' | 'NOTA' | 'ADJUNTO';
export type DocumentType = 'CEDULA_FRENTE' | 'CEDULA_REVERSO' | 'SELFIE' | 'SOAT' | 'LICENCIA' | 'TARJETA_PROPIEDAD';
export type VehicleType = 'A_PIE' | 'BICICLETA' | 'MOTO' | 'CARRO';
export type RatingTag = 'CUIDADOSO' | 'RAPIDO' | 'AMABLE' | 'BUENA_COMUNICACION' | 'EXCELENTE_DILIGENCIAS';

export interface UserView {
  id: string; fullName: string; phone: string; email?: string; role: Role; companyId?: string; mustChangePassword?: boolean;
  termsPending?: boolean;
}
export interface AuthResponse { accessToken: string; expiresIn: number; user: UserView; }
export interface PublicConfig { whatsapp: string; centerLat: number; centerLng: number; radiusKm: number; }

export interface GeoPoint { lat: number; lng: number; }
export interface Quote {
  distanceKm: number; base: number; distance: number; waitCharge: number; extraStops: number; complexity: number;
  total: number; platformFee: number; courierPayout: number; tariffVersion: number;
}
export interface StopInput {
  address: string; lat: number; lng: number; instructions?: string; contactName?: string; contactPhone?: string;
}
export interface StopView extends StopInput { seq: number; }
export interface CreateErrandRequest {
  category: ErrandCategory; description: string; complexity: Complexity; pricingMode: PricingMode;
  stops: StopInput[]; estimatedWaitMinutes: number; purchaseBudget?: number; paymentMethod: PaymentMethod;
  scheduledFor?: string; repeatOf?: string;
}
export interface Reputation {
  averageStars?: number; ratingsCount: number; completedCount: number; completionRate?: number;
  punctualityRate?: number; memberSince?: string; tags: Record<string, number>;
}
export interface CourierSummary {
  id: string; fullName?: string; phone?: string; vehicleType?: VehicleType; plate?: string; reputation?: Reputation;
}
export interface HistoryView { from?: ErrandStatus; to: ErrandStatus; note?: string; at: string; }
export interface EvidenceView {
  id: string; type: EvidenceType; note?: string; amount?: number; lat?: number; lng?: number;
  contentType?: string; hasFile: boolean; createdAt: string;
}
export interface LivePoint { lat: number; lng: number; at: string; }
export interface ErrandView {
  id: string; code: string; category: ErrandCategory; description: string; complexity: Complexity;
  pricingMode: PricingMode; status: ErrandStatus; stops: StopView[]; distanceKm: number; estimatedWaitMinutes: number;
  quotedPrice: number; totalPrice: number; platformFee: number; courierPayout: number; purchaseBudget: number;
  purchasesTotal: number; refundTotal: number; paymentMethod: PaymentMethod; paymentStatus?: 'PENDING' | 'CONFIRMED' | 'VOIDED';
  scheduledFor?: string; createdAt: string; assignedAt?: string; startedAt?: string; deliveredAt?: string;
  completedAt?: string; cancelledAt?: string; cancelReason?: string; needsAttention?: boolean; attentionReason?: string;
  otpFailedAttempts?: number; client?: { id: string; fullName: string; phone?: string }; courier?: CourierSummary;
  startOtp?: string; endOtp?: string; history: HistoryView[]; evidences: EvidenceView[]; courierLocation?: LivePoint;
  rating?: { stars: number; tags: string[]; comment?: string }; courierPaidAt?: string; companyId?: string;
  viewer: 'OWNER' | 'COMPANY_ADMIN' | 'COURIER' | 'PROSPECT' | 'ADMIN';
}
export interface ErrandSummary {
  id: string; code: string; category: ErrandCategory; description: string; status: ErrandStatus; pricingMode: PricingMode;
  totalPrice: number; courierPayout: number; createdAt: string; firstAddress?: string; lastAddress?: string;
  clientName?: string; courierName?: string; needsAttention: boolean; attentionReason?: string;
}
export interface OfferView {
  id: string; errandId: string; courier?: CourierSummary; amount: number; counterAmount?: number; message?: string;
  status: 'PENDING' | 'COUNTERED' | 'ACCEPTED' | 'REJECTED'; createdAt: string;
}
export interface OpportunityView {
  id: string; code: string; category: ErrandCategory; description: string; complexity: Complexity; pricingMode: PricingMode;
  status: ErrandStatus; courierPayout: number; suggestedPrice: number; routeKm: number; distanceToOriginKm?: number;
  stopsCount: number; originAddress: string; destinationAddress?: string; purchaseBudget: number; createdAt: string;
  scheduledFor?: string; myOffer?: OfferView;
}
export interface CourierView {
  userId: string; fullName: string; phone: string; status: 'PENDING' | 'APPROVED' | 'REJECTED'; rejectionReason?: string;
  vehicleType?: VehicleType; plate?: string; payoutMethod?: 'NEQUI' | 'DAVIPLATA' | 'BANCO'; payoutAccount?: string;
  available: boolean; lat?: number; lng?: number; lastPingAt?: string; approvedAt?: string;
  documents: { id: string; type: DocumentType; uploadedAt: string }[]; missingDocuments: DocumentType[];
}
export interface Earnings {
  today: number; week: number; pendingPayout: number; paidTotal: number; completedCount: number;
  items: { errandId: string; code: string; status: ErrandStatus; payout: number; completedAt?: string; paidAt?: string }[];
}
export interface CompanyView { id: string; name: string; nit?: string; plan: string; monthlyFee: number; active: boolean; createdAt: string; members: number; }
export interface AddressView { id: string; label: string; address: string; lat: number; lng: number; }
export interface Kpi { key: string; label: string; value?: number; target: number; unit: string; higherIsBetter: boolean; met?: boolean; }
export interface Breakdown { key: string; errands: number; completed: number; }
export interface KpiReport {
  from: string; to: string; days: number; kpis: Kpi[];
  totals: { errands: number; completed: number; cancelled: number; failed: number; gmv: number; platformRevenue: number; activeClients: number; signups: number; activeCouriers: number };
  funnel: { quotes: number; requested: number; completed: number };
  daily: { date: string; errands: number; completed: number; gmv: number }[];
  byCategory: Breakdown[]; bySegment: Breakdown[]; byChannel: Breakdown[];
}
export interface LiveMap {
  couriers: { id: string; fullName: string; lat: number; lng: number; available: boolean; lastPingAt: string; activeErrandCode?: string; activeErrandId?: string }[];
  errands: { id: string; code: string; status: ErrandStatus; lat: number; lng: number; courierId?: string; needsAttention: boolean }[];
}
export interface PaymentView {
  id: string; errandId: string; errandCode: string; errandStatus: ErrandStatus; clientName?: string; method: PaymentMethod;
  amount: number; status: 'PENDING' | 'CONFIRMED' | 'VOIDED'; reference?: string; confirmedAt?: string; purchases: number;
  refund: number; courierPayout: number; courierName?: string; courierPaidAt?: string; createdAt: string;
}
export interface Tariff {
  version?: number; baseFare: number; perKm: number; includedWaitMin: number; perExtraMin: number; perExtraStop: number;
  complexityMedium: number; complexityComplex: number; commissionPct: number; createdAt?: string;
}
export interface AdminUserView { user: UserView; active: boolean; createdAt: string; acquisitionSource?: string; }
export interface RealtimeEvent { type: string; errandId?: string; status?: string; data?: Record<string, unknown>; at: string; }
