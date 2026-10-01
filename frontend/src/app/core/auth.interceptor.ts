import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** Agrega el bearer token y, ante un 401, intenta una vez renovar la sesión y reintentar. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  // Solo nuestra API recibe el token (nunca terceros como Nominatim u OSM).
  if (!req.url.startsWith('/api/')) return next(req);
  const isAuthCall = req.url.startsWith('/api/auth/');
  const withToken = () => {
    const token = auth.accessToken();
    return token && !isAuthCall ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  };

  return next(withToken()).pipe(
    catchError((err: HttpErrorResponse) => {
      // AC-001-13: términos nuevos publicados durante la sesión ⇒ pantalla de aceptación.
      if (err.status === 403 && err.error?.code === 'TERMS_PENDING') {
        auth.markTermsPending();
        void router.navigateByUrl('/aceptar-terminos');
        return throwError(() => err);
      }
      if (err.status !== 401 || isAuthCall || !auth.accessToken()) {
        return throwError(() => err);
      }
      return from(auth.refresh()).pipe(
        switchMap(ok => (ok ? next(withToken()) : throwError(() => err))),
      );
    }),
  );
};
