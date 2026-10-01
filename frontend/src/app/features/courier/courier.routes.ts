import { Routes } from '@angular/router';
import { CourierShellComponent } from './courier-shell.component';

export const COURIER_ROUTES: Routes = [
  {
    path: '',
    component: CourierShellComponent,
    children: [
      { path: '', loadComponent: () => import('./courier-home.component').then(m => m.CourierHomeComponent) },
      { path: 'perfil', loadComponent: () => import('./onboarding.component').then(m => m.OnboardingComponent) },
      { path: 'mandados/:id', loadComponent: () => import('./courier-errand.component').then(m => m.CourierErrandComponent) },
      { path: 'ganancias', loadComponent: () => import('./earnings.component').then(m => m.EarningsComponent) },
    ],
  },
];
