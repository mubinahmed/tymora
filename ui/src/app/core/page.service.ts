import { Injectable, inject, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { RpcService } from './rpc.service';
import { PageNameInterface } from './models';

/**
 * Resolves a localized page title + help URL via the real PageNameBackend
 * (the endpoint the Wave 0 proof exercised) and exposes it as a signal the
 * shell renders. Each feature calls set('Raw Name') on init.
 */
@Injectable({ providedIn: 'root' })
export class PageService {
  private rpc = inject(RpcService);

  readonly title = signal<string>('');
  readonly helpUrl = signal<string | undefined>(undefined);

  set(rawName: string): void {
    this.title.set(rawName);
    this.helpUrl.set(undefined);
    document.title = `${rawName} - UniTime`;
    this.rpc
      .execute<PageNameInterface>('PageNameRpcRequest', { name: rawName })
      .pipe(catchError(() => of<PageNameInterface>({ name: rawName })))
      .subscribe((p) => {
        this.title.set(p.name ?? rawName);
        this.helpUrl.set(p.helpUrl);
        document.title = `${p.name ?? rawName} - UniTime`;
      });
  }
}
