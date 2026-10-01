import { HttpClient } from '@angular/common/http';
import { Injectable, Injector, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthResponse, Role, UserView } from './models';
import { PushService } from './push.service';

/**
 * Sesión: el access token vive solo en memoria (no en localStorage, mitiga XSS);
 * el refresh token está en una cookie HttpOnly que el navegador envía a /api/auth.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private injector = inject(Injector);

  private token = signal<string | null>(null);
  readonly user = signal<UserView | null>(null);
  readonly loggedIn = computed(() => this.user() !== null);
  private refreshing: Promise<boolean> | null = null;

  accessToken() { return this.token(); }

  async login(phone: string, password: string) {
    this.apply(await firstValueFrom(this.http.post<AuthResponse>('/api/auth/login', { phone, password })));
  }

  async register(body: { fullName: string; phone: string; email?: string; password: string; role: 'CLIENT' | 'COURIER'; acceptPrivacy: boolean; utmSource?: string }) {
    this.apply(await firstValueFrom(this.http.post<AuthResponse>('/api/auth/register', body)));
  }

  /** AC-001-10: la respuesta trae una sesión nueva (las demás quedan revocadas). */
  async changePassword(currentPassword: string, newPassword: string) {
    this.apply(await firstValueFrom(this.http.put<AuthResponse>('/api/me/password', { currentPassword, newPassword })));
  }

  /** AC-001-14: acepta la versión vigente; la respuesta trae un token ya desbloqueado. */
  async acceptTerms(version: string) {
    this.apply(await firstValueFrom(this.http.post<AuthResponse>('/api/me/terms', { version })));
  }

  /** La API avisó (403 TERMS_PENDING) que se publicó una versión nueva durante la sesión. */
  markTermsPending() {
    const u = this.user();
    if (u && !u.termsPending) this.user.set({ ...u, termsPending: true });
  }

  /** Restaura la sesión con la cookie. Una sola petición concurrente. */
  refresh(): Promise<boolean> {
    if (!this.refreshing) {
      this.refreshing = firstValueFrom(this.http.post<AuthResponse>('/api/auth/refresh', {}))
        .then(r => { this.apply(r); return true; })
        .catch(() => { this.clear(); return false; })
        .finally(() => { this.refreshing = null; });
    }
    return this.refreshing;
  }

  async logout() {
    // Antes de cerrar la sesión (el DELETE necesita el token): este dispositivo deja de recibir push de la cuenta.
    await this.push().disable().catch(() => undefined);
    try { await firstValueFrom(this.http.post('/api/auth/logout', {})); } catch { /* ya expirada */ }
    this.clear();
    this.router.navigateByUrl('/');
  }

  homeFor(role: Role | undefined = this.user()?.role): string {
    switch (role) {
      case 'COURIER': return '/mandadero';
      case 'ADMIN': return '/admin';
      default: return '/app';
    }
  }

  private apply(r: AuthResponse) {
    const changedUser = this.user()?.id !== r.user.id;
    this.token.set(r.accessToken);
    this.user.set(r.user);
    if (changedUser) void this.push().sync();
  }

  /** Perezoso para evitar una dependencia circular en la construcción. */
  private push() {
    return this.injector.get(PushService);
  }

  private clear() {
    this.token.set(null);
    this.user.set(null);
  }
}
