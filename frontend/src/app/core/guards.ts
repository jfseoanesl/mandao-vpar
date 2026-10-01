import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Role } from './models';

/** Exige sesión y uno de los roles indicados; si no, redirige al inicio del rol o al login. */
export const roleGuard = (...roles: Role[]): CanActivateFn => (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const user = auth.user();
  if (!user) {
    return router.createUrlTree(['/entrar'], { queryParams: { volver: state.url } });
  }
  // AC-001-11: con contraseña temporal no se usa la app hasta cambiarla.
  if (user.mustChangePassword) {
    return router.parseUrl('/cambiar-clave');
  }
  // AC-001-13: primero se aceptan los términos vigentes.
  if (user.termsPending) {
    return router.createUrlTree(['/aceptar-terminos'], { queryParams: { volver: state.url } });
  }
  return roles.includes(user.role) ? true : router.parseUrl(auth.homeFor(user.role));
};

/** Exige sesión, sin importar el rol. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  return auth.user() ? true : inject(Router).createUrlTree(['/entrar'], { queryParams: { volver: state.url } });
};

/** Pantallas públicas (login/registro): si ya hay sesión, lleva al inicio del rol. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.user() ? inject(Router).parseUrl(auth.homeFor()) : true;
};
