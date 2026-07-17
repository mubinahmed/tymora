import { Injectable, inject } from '@angular/core';
import { RpcService } from './rpc.service';
import { EncodeQueryRpcResponse } from './models';

/**
 * Server-side report export (PDF/XLS/CSV/JSON) via the legacy ExportServlet.
 *
 * The servlet renders the report server-side; the client must first encode the
 * report's query (output=<name.ext>&<report-specific params>) into a URL-safe
 * token via EncodeQueryRpcRequest, then open /export?q=<token>. Mirrors the GWT
 * pages' `EncodeQueryRpcRequest.encode(query)` -> `ToolBox.open("export?q="+q)`.
 *
 * Each report builds its own `query` string (see the per-screen exportServer
 * callers) — there is no generic PDF; only outputs that have a server Exporter
 * (e.g. solution-reports.pdf, assignment-history.pdf, hql-report.xls) work.
 */
@Injectable({ providedIn: 'root' })
export class ExportService {
  private rpc = inject(RpcService);

  /**
   * @param query the raw export query, e.g. "output=solution-reports.pdf&sort=0&table=abc".
   * On success opens /export?q=<encoded token> in a new tab; the token is already
   * URL-safe (matches the legacy which appends it unescaped).
   */
  export(query: string): void {
    // Addressed by FQN — EncodeQueryRpcRequest is nested in EventInterface.
    this.rpc
      .execute<EncodeQueryRpcResponse>('org.unitime.timetable.gwt.shared.EventInterface$EncodeQueryRpcRequest', { query })
      .subscribe({
        next: (res) => {
          if (res?.query) window.open('/export?q=' + res.query, '_blank');
        },
        // silent: the calling screen already surfaces its own errors; export is a side action
        error: () => {},
      });
  }
}
