import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TableModule } from 'primeng/table';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { SubpartDetailRequest, SubpartDetailResponse } from './subpart-detail.models';

/**
 * Scheduling Subpart Detail (legacy schedulingSubpartDetail.action) — read view of
 * a subpart, backed by the additive SchedulingSubpartDetailRequest command bean.
 * Reached by subpart id from the offering detail tree; links to the subpart editor.
 */
@Component({
  selector: 'app-subpart-detail',
  imports: [RouterLink, ButtonModule, TagModule, TableModule, MessageModule, ProgressSpinnerModule],
  templateUrl: './subpart-detail.html',
})
export class SchedulingSubpartDetail implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  readonly id = input<string>();

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<SubpartDetailResponse | null>(null);

  protected readonly offeringId = computed(() => this.data()?.offeringId ?? null);

  ngOnInit(): void {
    this.page.set('Scheduling Subpart Detail');
    const sid = this.id() == null ? NaN : Number(this.id());
    if (!Number.isFinite(sid)) {
      this.error.set('No scheduling subpart was specified.');
      this.loading.set(false);
      return;
    }
    const request: SubpartDetailRequest = { subpartId: sid };
    this.rpc.execute<SubpartDetailResponse>('SchedulingSubpartDetailRequest', request).subscribe({
      next: (d) => {
        this.data.set(d);
        if (d.instructionalTypeLabel && d.courseName) this.page.set(d.courseName + ' ' + d.instructionalTypeLabel);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }
}
