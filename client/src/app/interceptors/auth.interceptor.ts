import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, throwError } from 'rxjs';
import { catchError, filter, switchMap, take } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';

let isRefreshing = false;
const refreshSubject = new BehaviorSubject<string | null>(null);

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (req.url.includes('/api/auth/')) {
    return next(req);
  }

  const token = authService.getAccessToken();
  const authReq = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if ((error.status === 401 || error.status === 403) && authService.getRefreshToken()) {
        return handleTokenRefresh(authService, router, req, next);
      }
      return throwError(() => error);
    })
  );
};

function handleTokenRefresh(
  authService: AuthService,
  router: Router,
  req: import('@angular/common/http').HttpRequest<unknown>,
  next: import('@angular/common/http').HttpHandlerFn
): Observable<import('@angular/common/http').HttpEvent<unknown>> {
  if (!isRefreshing) {
    isRefreshing = true;
    refreshSubject.next(null);

    return authService.refresh().pipe(
      switchMap(res => {
        isRefreshing = false;
        refreshSubject.next(res.accessToken);
        return next(req.clone({ setHeaders: { Authorization: `Bearer ${res.accessToken}` } }));
      }),
      catchError(err => {
        isRefreshing = false;
        refreshSubject.next(null);
        authService.logout();
        router.navigate(['/login']);
        return throwError(() => err);
      })
    );
  }

  // Another request triggered refresh — wait for it to complete
  return refreshSubject.pipe(
    filter(token => token !== null),
    take(1),
    switchMap(token => next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })))
  );
}
