import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { map } from 'rxjs/operators';
import { AuthService } from './auth.service';

/**
 * Ensures user/session info is loaded before a protected route renders. It
 * currently always allows navigation — data-level authorization is enforced by
 * the backend (the interceptor redirects to login on 401). When Angular owns
 * more screens, this is where route-level permission checks (from the loaded
 * user's rights) will gate access.
 */
export const authGuard: CanActivateFn = () => {
  return inject(AuthService).ensureLoaded().pipe(map(() => true));
};
