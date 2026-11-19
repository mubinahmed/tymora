import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { RpcService } from './rpc.service';
import { AssignmentChangeRequest, AssignmentChangeResponse } from '../features/approval/change-requests.models';

const RPC = 'AssignmentChangeRequest';

/**
 * Shared state of the reason / approval step: what the user may do and the counts behind the
 * header badge (requests awaiting my decision, my decided requests I have not seen yet).
 * Every AssignmentChangeRequest response carries the counts, so any call refreshes the badge.
 */
@Injectable({ providedIn: 'root' })
export class ChangeRequestsService {
  private rpc = inject(RpcService);

  readonly info = signal<AssignmentChangeResponse | null>(null);
  readonly pendingCount = computed(() => this.info()?.pendingCount ?? 0);
  readonly unseenDecisions = computed(() => this.info()?.unseenDecisions ?? 0);
  readonly badge = computed(() => this.pendingCount() + this.unseenDecisions());
  readonly visible = computed(() => !!(this.info()?.canApprove || this.info()?.canRequest));

  call(req: AssignmentChangeRequest): Observable<AssignmentChangeResponse> {
    return this.rpc.execute<AssignmentChangeResponse>(RPC, req).pipe(tap((r) => this.info.set(r)));
  }

  /** refresh permissions and counts (errors ignored: the badge simply stays hidden) */
  refresh(): void {
    this.call({ operation: 'INFO' }).subscribe({ error: () => {} });
  }
}
