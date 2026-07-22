import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { ApiError } from './models';

/**
 * - Sends the session cookie (withCredentials) so the facade rides the same
 *   JSESSIONID as the rest of UniTime.
 * - Normalizes errors to ApiError; on 401 it routes to the in-app Angular login
 *   (preserving the requested URL), which then posts to Spring Security.
 */
export const apiInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const request = req.clone({ withCredentials: true });
  return next(request).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && !router.url.startsWith('/signin')) {
        router.navigate(['/signin'], { queryParams: { returnUrl: router.url } });
      }
      const message =
        (err.error && (err.error.error || err.error.message)) ||
        err.message ||
        `Request failed (${err.status})`;
      const apiError: ApiError = { status: err.status, message };
      return throwError(() => apiError);
    }),
  );
};
