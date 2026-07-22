import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, throwError, timer } from 'rxjs';
import { concatMap, filter, first, map } from 'rxjs/operators';

/** Async lifecycle states returned by the facade (mirror AsyncRpcExecutor.State). */
export type AsyncState = 'RUNNING' | 'DONE' | 'ERROR' | 'CANCELLED';
export interface AsyncStatus<T> {
  status: AsyncState;
  result?: T;
  error?: string;
}

/**
 * Single client for the whole GWT-RPC command surface via the Wave 0 facade.
 *   rpc.execute<BuildingsDataResponse>('GetBuildingsRequest', {})
 * requestName is the request class simple name (or FQN if ambiguous).
 */
@Injectable({ providedIn: 'root' })
export class RpcService {
  private http = inject(HttpClient);

  /** Synchronous command (command-pattern beans). */
  execute<TResponse>(requestName: string, request: unknown = {}): Observable<TResponse> {
    return this.http.post<TResponse>(`api/rpc/${requestName}`, request);
  }

  /**
   * Invoke a classic GWT RemoteService method (Curricula/Reservation/Sectioning/
   * LimitAndProjectionSnapshot). servicePath is the @RemoteServiceRelativePath
   * (e.g. 'reservation.gwt'); args are positional.
   *   rpc.service<ReservationInterface[]>('reservation.gwt', 'findReservations', [filter])
   */
  service<TResponse>(servicePath: string, method: string, args: unknown[] = []): Observable<TResponse> {
    return this.http.post<TResponse>(`api/service/${servicePath}/${method}`, args);
  }

  /** Submit a long-running command; resolves to the execution id. */
  submitAsync(requestName: string, request: unknown = {}): Observable<string> {
    return this.http
      .post<{ executionId: string }>(`api/rpc/async/${requestName}`, request)
      .pipe(map((r) => r.executionId));
  }

  /** One non-blocking status read. */
  poll<TResponse>(executionId: string): Observable<AsyncStatus<TResponse>> {
    return this.http
      .get<AsyncStatus<TResponse>>(`api/rpc/async/${executionId}`, { observe: 'response' })
      .pipe(map((resp: HttpResponse<AsyncStatus<TResponse>>) => resp.body as AsyncStatus<TResponse>));
  }

  /** Request cancellation. */
  cancel(executionId: string): Observable<AsyncStatus<never>> {
    return this.http.delete<AsyncStatus<never>>(`api/rpc/async/${executionId}`);
  }

  /** Submit + poll until terminal, emitting the final result (errors on ERROR/CANCELLED). */
  executeAsync<TResponse>(requestName: string, request: unknown = {}, pollMs = 1500): Observable<TResponse> {
    return this.submitAsync(requestName, request).pipe(
      concatMap((id) =>
        timer(0, pollMs).pipe(
          concatMap(() => this.poll<TResponse>(id)),
          filter((s) => s.status !== 'RUNNING'),
          first(),
          concatMap((s) =>
            s.status === 'DONE' ? [s.result as TResponse] : throwError(() => new Error(s.error ?? s.status)),
          ),
        ),
      ),
    );
  }
}
