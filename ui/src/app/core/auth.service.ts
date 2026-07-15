import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, forkJoin, of, timeout } from 'rxjs';
import { catchError, map, shareReplay, switchMap, tap } from 'rxjs/operators';
import { RpcService } from './rpc.service';
import { SessionInfoInterface, UserInfoInterface, VersionInfoInterface } from './models';

/** Loads and holds the current user / academic session / build info as signals. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private rpc = inject(RpcService);
  private http = inject(HttpClient);

  readonly user = signal<UserInfoInterface | null>(null);
  readonly session = signal<SessionInfoInterface | null>(null);
  readonly version = signal<VersionInfoInterface | null>(null);
  readonly loaded = signal(false);

  private load$?: Observable<boolean>;

  /** Idempotent: fetches user/session/version once and caches. */
  ensureLoaded(): Observable<boolean> {
    if (!this.load$) {
      this.load$ = forkJoin({
        user: this.rpc.execute<UserInfoInterface>('UserInfoRpcRequest').pipe(catchError(() => of(null))),
        session: this.rpc.execute<SessionInfoInterface>('SessionInfoRpcRequest').pipe(catchError(() => of(null))),
        version: this.rpc.execute<VersionInfoInterface>('VersionInfoRpcRequest').pipe(catchError(() => of(null))),
      }).pipe(
        tap(({ user, session, version }) => {
          this.user.set(user);
          this.session.set(session);
          this.version.set(version);
          this.loaded.set(true);
        }),
        map(() => true),
        shareReplay(1),
      );
    }
    return this.load$;
  }

  isAuthenticated(): boolean {
    return !!this.user()?.name;
  }

  /** Clear the cache and re-fetch user/session/version. */
  refresh(): Observable<boolean> {
    this.load$ = undefined;
    this.loaded.set(false);
    return this.ensureLoaded();
  }

  /**
   * POST credentials to Spring Security's form-login URL (/login, fields
   * username/password, CSRF disabled). Multi-role accounts are redirected to
   * selectPrimaryRole.action after login; we GET it so the backend auto-assigns
   * a default authority when the user has one (idempotent for single-role
   * accounts). Accounts with several roles and no default still need a manual
   * pick — the login screen offers the classic page for that. No backend change.
   */
  login(username: string, password: string): Observable<boolean> {
    const body = new HttpParams().set('username', username).set('password', password).toString();
    return this.http
      .post('login', body, {
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        responseType: 'text',
      })
      .pipe(
        catchError(() => of('')),
        switchMap(() => this.http.get('selectPrimaryRole.action', { responseType: 'text' }).pipe(catchError(() => of('')))),
        switchMap(() => this.refresh()),
        map(() => this.isAuthenticated()),
      );
  }

  /**
   * Log out: hit the server logout to clear the JSESSIONID cookie, then reset
   * client state. We MUST await the server call before resetting/navigating —
   * otherwise the login screen re-checks auth against a still-valid session and
   * bounces straight back. The redirect target is ignored (catchError absorbs
   * the cross-origin/timeout follow); clearing the cookie is what matters.
   */
  logout(): Observable<void> {
    return this.http.get('logout.action', { responseType: 'text' }).pipe(
      timeout(5000),
      catchError(() => of('')),
      tap(() => {
        this.user.set(null);
        this.session.set(null);
        this.version.set(null);
        this.loaded.set(false);
        this.load$ = undefined;
      }),
      map(() => undefined),
    );
  }
}
