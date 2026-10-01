import { Routes } from '@angular/router';
import { AdminShellComponent } from './admin-shell.component';

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    component: AdminShellComponent,
    children: [
      { path: '', loadComponent: () => import('./dashboard.component').then(m => m.DashboardComponent) },
      { path: 'en-vivo', loadComponent: () => import('./live-map.component').then(m => m.LiveMapComponent) },
      { path: 'mandados', loadComponent: () => import('./admin-errands.component').then(m => m.AdminErrandsComponent) },
      { path: 'mandados/:id', loadComponent: () => import('./admin-errand-detail.component').then(m => m.AdminErrandDetailComponent) },
      { path: 'mandaderos', loadComponent: () => import('./couriers-admin.component').then(m => m.CouriersAdminComponent) },
      { path: 'pagos', loadComponent: () => import('./payments.component').then(m => m.PaymentsComponent) },
      { path: 'tarifas', loadComponent: () => import('./tariff.component').then(m => m.TariffComponent) },
      { path: 'empresas', loadComponent: () => import('./companies-admin.component').then(m => m.CompaniesAdminComponent) },
      { path: 'usuarios', loadComponent: () => import('./users-admin.component').then(m => m.UsersAdminComponent) },
    ],
  },
];
