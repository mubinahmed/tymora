import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, timer, throwError } from 'rxjs';
import { concatMap, filter, first, map } from 'rxjs/operators';

/** Async lifecycle states returned by the facade (mirror AsyncRpcExecutor.State). */
export type AsyncState = 'RUNNING' | 'DONE' | 'ERROR' | 'CANCELLED';
export interface AsyncStatus<T> {
  status: AsyncState;
  result?: T;
  error?: string;
}

/**
 * Generic client for the Wave 0 facade. One method reaches every one of the
 * 152 GWT-RPC command beans:
 *
 *   rpc.execute<PageNameInterface>('PageNameRpcRequest', { name: 'Rooms' })
 *
 * requestName is the request class simple name (or FQN if ambiguous).
 * withCredentials:true reuses the existing JSESSIONID; the CSRF token is added
 * by the app-wide HttpInterceptor (Angular reads Spring Security's XSRF-TOKEN
 * cookie and echoes it as X-XSRF-TOKEN), so no per-call auth wiring is needed.
 */
@Injectable({ providedIn: 'root' })
export class RpcService {
  constructor(private http: HttpClient) {}

  /** Synchronous command. */
  execute<TResponse>(requestName: string, request: unknown = {}): Observable<TResponse> {
    return this.http.post<TResponse>(`api/rpc/${requestName}`, request, {
      withCredentials: true,
    });
  }

  /** Submit a long-running command; resolves to the execution id. */
  submitAsync(requestName: string, request: unknown = {}): Observable<string> {
    return this.http
      .post<{ executionId: string }>(`api/rpc/async/${requestName}`, request, { withCredentials: true })
      .pipe(map((r) => r.executionId));
  }

  /** One non-blocking status read. 202 => RUNNING, 200 => terminal. */
  poll<TResponse>(executionId: string): Observable<AsyncStatus<TResponse>> {
    return this.http
      .get<AsyncStatus<TResponse>>(`api/rpc/async/${executionId}`, {
        withCredentials: true,
        observe: 'response',
      })
      .pipe(map((resp: HttpResponse<AsyncStatus<TResponse>>) => resp.body as AsyncStatus<TResponse>));
  }

  /** Request cancellation. */
  cancel(executionId: string): Observable<AsyncStatus<never>> {
    return this.http.delete<AsyncStatus<never>>(`api/rpc/async/${executionId}`, { withCredentials: true });
  }

  /**
   * Submit + poll until terminal, emitting the final result (or erroring on
   * ERROR/CANCELLED). This is the one call a solver screen uses:
   *
   *   rpc.executeAsync<SolverResult>('SolverPageRequest', req, 1500)
   *      .subscribe(result => ...)
   */
  executeAsync<TResponse>(
    requestName: string,
    request: unknown = {},
    pollMs = 1500,
  ): Observable<TResponse> {
    return this.submitAsync(requestName, request).pipe(
      concatMap((id) =>
        timer(0, pollMs).pipe(
          concatMap(() => this.poll<TResponse>(id)),
          filter((s) => s.status !== 'RUNNING'),
          first(),
          concatMap((s) =>
            s.status === 'DONE'
              ? [s.result as TResponse]
              : throwError(() => new Error(s.error ?? s.status)),
          ),
        ),
      ),
    );
  }
}
