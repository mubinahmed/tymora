import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, forkJoin, of } from 'rxjs';
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
   * POST credentials to Spring Security's form-login processing URL (/login,
   * fields username/password, CSRF disabled) using a normal form POST. The
   * server redirects on success/failure; we re-check auth to decide. No backend
   * change — this is the same endpoint the legacy login page posts to.
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
        switchMap(() => this.refresh()),
        map(() => this.isAuthenticated()),
      );
  }

  /** Hit Spring Security's logout URL, then re-check (becomes unauthenticated). */
  logout(): Observable<boolean> {
    return this.http.get('logout.action', { responseType: 'text' }).pipe(
      catchError(() => of('')),
      switchMap(() => this.refresh()),
    );
  }
}
