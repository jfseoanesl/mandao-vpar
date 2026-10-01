import { Complexity, DocumentType, ErrandCategory, ErrandStatus, PaymentMethod, RatingTag, VehicleType } from './models';

export interface CategoryInfo { key: ErrandCategory; emoji: string; title: string; hint: string; example: string; }

/** Las 6 categorías + "Otro mandado" de Mandao.md (sección 3). */
export const CATEGORIES: CategoryInfo[] = [
  { key: 'ENVIO', emoji: '🚚', title: 'Enviar algo', hint: 'Llaves, paquetes, documentos', example: 'Lleva estas llaves a mi esposa en el barrio Novalito.' },
  { key: 'COMPRA', emoji: '🛒', title: 'Comprar algo', hint: 'Medicamentos, mercado, repuestos', example: 'Compra estos medicamentos en la droguería y tráemelos.' },
  { key: 'DILIGENCIA', emoji: '📋', title: 'Hacer una diligencia', hint: 'Radicar, reclamar, filas', example: 'Lleva estos documentos a la UPC, entrégalos en registro y tráeme copia sellada.' },
  { key: 'RECOGER_ENTREGAR', emoji: '📦', title: 'Recoger algo', hint: 'Tiendas, talleres, Marketplace', example: 'Recoge mi pedido en Guatapurí y tráemelo.' },
  { key: 'BUSCAR_COMPRAR', emoji: '🏪', title: 'Buscar y comprar', hint: 'Cotizar, encontrar y comprar', example: 'Averigua si tienen este repuesto, mándame foto y si cuesta menos de $350.000 cómpralo.' },
  { key: 'EMPRESARIAL', emoji: '🏢', title: 'Mandado empresarial', hint: 'Bancos, proveedores, facturas', example: 'Lleva estas facturas al proveedor y que me firmen recibido.' },
  { key: 'OTRO', emoji: '✨', title: 'Otro mandado', hint: '¿Qué necesitas que hagamos por ti?', example: 'Haz esta fila y avísame cuando falten tres turnos.' },
];

export const categoryInfo = (k: ErrandCategory) => CATEGORIES.find(c => c.key === k)!;

export const STATUS: Record<ErrandStatus, { label: string; tone: string }> = {
  NEGOTIATING: { label: 'Recibiendo ofertas', tone: 'info' },
  SEARCHING: { label: 'Buscando mandadero', tone: 'info' },
  ASSIGNED: { label: 'Mandadero asignado', tone: '' },
  GOING_TO_ORIGIN: { label: 'En camino', tone: '' },
  IN_PROGRESS: { label: 'Haciendo tu mandado', tone: '' },
  DELIVERED: { label: 'Entregado', tone: 'ok' },
  COMPLETED: { label: 'Completado', tone: 'ok' },
  RATED: { label: 'Calificado', tone: 'ok' },
  CANCELLED: { label: 'Cancelado', tone: 'neutral' },
  FAILED: { label: 'No completado', tone: 'danger' },
};

export const COMPLEXITY: Record<Complexity, string> = {
  SENCILLA: 'Sencillo', MEDIA: 'Diligencia sencilla (+$2.500)', COMPLEJA: 'Diligencia compleja (+$5.000)',
};

export const PAYMENT: Record<PaymentMethod, string> = {
  EFECTIVO: 'Efectivo', NEQUI: 'Nequi', DAVIPLATA: 'Daviplata', TRANSFERENCIA: 'Transferencia',
};

export const DOCUMENTS: Record<DocumentType, string> = {
  CEDULA_FRENTE: 'Cédula (frente)', CEDULA_REVERSO: 'Cédula (reverso)', SELFIE: 'Selfie',
  SOAT: 'SOAT', LICENCIA: 'Licencia de conducción', TARJETA_PROPIEDAD: 'Tarjeta de propiedad',
};

export const VEHICLES: Record<VehicleType, string> = { A_PIE: 'A pie', BICICLETA: 'Bicicleta', MOTO: 'Moto', CARRO: 'Carro' };

export const TAGS: Record<RatingTag, string> = {
  CUIDADOSO: '⭐ Muy cuidadoso', RAPIDO: '⚡ Rápido', AMABLE: '😊 Amable',
  BUENA_COMUNICACION: '📱 Buena comunicación', EXCELENTE_DILIGENCIAS: '📋 Excelente en diligencias',
};

export const EVIDENCE: Record<string, string> = {
  FOTO: '📸 Foto', FACTURA: '🧾 Factura', FIRMA: '✍️ Firma', NOTA: '📝 Nota', ADJUNTO: '📎 Adjunto del cliente',
};

const COP = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });
export const money = (n: number | null | undefined) => (n == null ? '—' : COP.format(n));

const DT = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Bogota' });
const T = new Intl.DateTimeFormat('es-CO', { timeStyle: 'short', timeZone: 'America/Bogota' });
export const dateTime = (iso?: string) => (iso ? DT.format(new Date(iso)) : '—');
export const time = (iso?: string) => (iso ? T.format(new Date(iso)) : '—');

/** Mensaje legible desde un error HTTP RFC 7807. */
export function errorMessage(err: any): string {
  const e = err?.error;
  if (e?.errors && typeof e.errors === 'object') {
    const first = Object.values(e.errors)[0];
    if (first) return `${e.detail ?? 'Datos inválidos'}: ${first}`;
  }
  if (e?.detail) return e.detail;
  if (err?.status === 0) return 'Sin conexión. Revisa tu internet.';
  return 'Algo salió mal. Intenta de nuevo.';
}
