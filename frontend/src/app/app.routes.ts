import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './core/guards';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./features/public/landing.component').then(m => m.LandingComponent) },
  { path: 'terminos', loadComponent: () => import('./features/public/terms.component').then(m => m.TermsComponent) },
  { path: 'privacidad', loadComponent: () => import('./features/public/privacy.component').then(m => m.PrivacyComponent) },
  { path: 'entrar', canActivate: [guestGuard], loadComponent: () => import('./features/auth/login.component').then(m => m.LoginComponent) },
  { path: 'registro', canActivate: [guestGuard], loadComponent: () => import('./features/auth/register.component').then(m => m.RegisterComponent) },
  {
    path: 'aceptar-terminos',
    canActivate: [authGuard],
    loadComponent: () => import('./features/auth/accept-terms.component').then(m => m.AcceptTermsComponent),
  },
  {
    path: 'cambiar-clave',
    canActivate: [authGuard],
    loadComponent: () => import('./features/auth/change-password.component').then(m => m.ChangePasswordComponent),
  },
  {
    path: 'app',
    canActivate: [roleGuard('CLIENT', 'COMPANY_ADMIN')],
    loadChildren: () => import('./features/client/client.routes').then(m => m.CLIENT_ROUTES),
  },
  {
    path: 'mandadero',
    canActivate: [roleGuard('COURIER')],
    loadChildren: () => import('./features/courier/courier.routes').then(m => m.COURIER_ROUTES),
  },
  {
    path: 'admin',
    canActivate: [roleGuard('ADMIN')],
    loadChildren: () => import('./features/admin/admin.routes').then(m => m.ADMIN_ROUTES),
  },
  {
    path: 'comprobante/:id',
    canActivate: [roleGuard('CLIENT', 'COMPANY_ADMIN', 'ADMIN', 'COURIER')],
    loadComponent: () => import('./features/receipt/receipt.component').then(m => m.ReceiptComponent),
  },
  { path: '**', redirectTo: '' },
];
