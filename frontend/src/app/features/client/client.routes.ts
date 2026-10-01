import { Routes } from '@angular/router';
import { roleGuard } from '../../core/guards';
import { ClientShellComponent } from './client-shell.component';

export const CLIENT_ROUTES: Routes = [
  {
    path: '',
    component: ClientShellComponent,
    children: [
      { path: '', loadComponent: () => import('./client-home.component').then(m => m.ClientHomeComponent) },
      { path: 'nuevo/:categoria', loadComponent: () => import('./new-errand.component').then(m => m.NewErrandComponent) },
      { path: 'mandados', loadComponent: () => import('./my-errands.component').then(m => m.MyErrandsComponent) },
      { path: 'mandados/:id', loadComponent: () => import('./errand-detail.component').then(m => m.ErrandDetailComponent) },
      {
        path: 'empresa',
        canActivate: [roleGuard('COMPANY_ADMIN')],
        loadComponent: () => import('./company.component').then(m => m.CompanyComponent),
      },
    ],
  },
];
