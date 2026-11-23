import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { RpcService } from './rpc.service';
import { ApiError } from './models';
import { AssistantEntry, AssistantPendingAction, AssistantRequest, AssistantResponse } from '../features/assistant/assistant.models';

const RPC = 'AssistantRequest';

/**
 * State of the AI assistant chat, shared by the header button and the panel so the conversation survives
 * navigation. The server keeps the conversation Claude sees (including tool calls and results); this service
 * only keeps what is shown.
 */
@Injectable({ providedIn: 'root' })
export class AssistantService {
  private rpc = inject(RpcService);
  private router = inject(Router);

  /** null until STATUS answered; false when the user may not use the assistant */
  readonly available = signal<boolean | null>(null);
  readonly status = signal<AssistantResponse | null>(null);
  readonly open = signal(false);
  /** Panel collapsed to its title bar (the conversation and draft are kept). */
  readonly minimized = signal(false);
  readonly busy = signal(false);
  readonly entries = signal<AssistantEntry[]>([]);
  readonly pending = signal<AssistantPendingAction[]>([]);
  readonly servedBy = signal<string | undefined>(undefined);
  private conversationId: string | undefined;

  refresh(): void {
    this.rpc.execute<AssistantResponse>(RPC, { operation: 'STATUS' } satisfies AssistantRequest).subscribe({
      next: (r) => { this.status.set(r); this.available.set(true); },
      error: () => this.available.set(false),
    });
  }

  toggle(): void {
    // A minimised panel is restored rather than closed, so the header button always brings the chat back.
    if (this.open() && this.minimized()) { this.minimized.set(false); return; }
    this.open.update((o) => !o);
    if (this.open()) this.minimized.set(false);
    if (this.open() && !this.status()) this.refresh();
  }

  send(text: string): void {
    const t = text.trim();
    if (!t || this.busy()) return;
    this.call({ operation: 'SEND', text: t, conversationId: this.conversationId, page: this.router.url.split('#')[0] }, { role: 'user', text: t });
  }

  confirm(approve: boolean): void {
    if (this.busy() || !this.pending().length) return;
    this.call({ operation: 'CONFIRM', approve, conversationId: this.conversationId });
  }

  reset(): void {
    if (this.conversationId) this.rpc.execute<AssistantResponse>(RPC, { operation: 'RESET', conversationId: this.conversationId }).subscribe({ error: () => {} });
    this.conversationId = undefined;
    this.entries.set([]);
    this.pending.set([]);
    this.servedBy.set(undefined);
  }

  private call(req: AssistantRequest, optimistic?: AssistantEntry): void {
    this.busy.set(true);
    this.pending.set([]);
    if (optimistic) this.entries.update((e) => [...e, optimistic]);
    this.rpc.execute<AssistantResponse>(RPC, req).subscribe({
      next: (r) => {
        this.busy.set(false);
        this.conversationId = r.conversationId ?? this.conversationId;
        // the server echoes the user's message; it is already shown
        const fresh = (r.entries ?? []).filter((e, i) => !(optimistic && i === 0 && e.role === 'user'));
        this.entries.update((e) => [...e, ...fresh]);
        this.pending.set(r.pending ?? []);
        this.servedBy.set(r.servedBy);
      },
      error: (e: ApiError) => {
        this.busy.set(false);
        this.entries.update((x) => [...x, { role: 'notice', text: e.message }]);
      },
    });
  }
}
