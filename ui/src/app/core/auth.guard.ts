import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs/operators';
import { AuthService } from './auth.service';

/**
 * Ensures the user is authenticated before a protected route renders. Loads
 * user/session info once, then allows navigation if signed in, otherwise
 * redirects to the in-app login (preserving the requested URL). This is what
 * sends an unauthenticated visitor to /signin on first load.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.ensureLoaded().pipe(
    map(() =>
      auth.isAuthenticated()
        ? true
        : router.createUrlTree(['/signin'], { queryParams: { returnUrl: state.url } }),
    ),
  );
};
