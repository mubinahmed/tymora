import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { ConfigInfo, OfferingModifyRequest, OfferingModifyResponse, OfferingModifyUpdateRequest } from './offering-modify.models';

/**
 * Modify Instructional Offering (legacy instructionalOfferingModify.action) —
 * configuration-level read + save (each configuration's name and limit), backed by
 * OfferingModifyRequest / OfferingModifyUpdateRequest. Adding/removing configs,
 * subparts and classes stays on the GWT config editors. Reached by offering id.
 */
@Component({
  selector: 'app-offering-modify',
  imports: [
    FormsModule,
    RouterLink,
    ButtonModule,
    InputTextModule,
    InputNumberModule,
    TagModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './offering-modify.html',
})
export class OfferingModify implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);

  readonly id = input<string>();

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<OfferingModifyResponse | null>(null);
  protected readonly configs = signal<ConfigInfo[]>([]);

  ngOnInit(): void {
    this.page.set('Modify Instructional Offering');
    const oid = this.id() == null ? NaN : Number(this.id());
    if (!Number.isFinite(oid)) {
      this.error.set('No instructional offering was specified.');
      this.loading.set(false);
      return;
    }
    const request: OfferingModifyRequest = { offeringId: oid };
    this.rpc.execute<OfferingModifyResponse>('OfferingModifyRequest', request).subscribe({
      next: (d) => this.apply(d),
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  private apply(d: OfferingModifyResponse): void {
    this.data.set(d);
    if (d.offeringName) this.page.set('Modify — ' + d.offeringName);
    this.configs.set((d.configs ?? []).map((c) => ({ ...c })));
    this.loading.set(false);
  }

  save(): void {
    const d = this.data();
    if (!d) return;
    this.saving.set(true);
    const request: OfferingModifyUpdateRequest = {
      offeringId: d.offeringId,
      configs: this.configs().map((c) => ({ configId: c.configId, name: c.name, limit: c.unlimited ? null : c.limit ?? null })),
    };
    this.rpc.execute<OfferingModifyResponse>('OfferingModifyUpdateRequest', request).subscribe({
      next: (res) => {
        this.saving.set(false);
        this.apply(res);
        this.messages.add({ severity: 'success', summary: 'Saved', detail: 'Configurations updated.' });
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    });
  }
}
