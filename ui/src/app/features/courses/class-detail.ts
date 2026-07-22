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
import { ClassDetailRequest, ClassDetailResponse } from './class-detail.models';

/**
 * Class Detail (legacy classDetail.action) — read view of a single class, backed
 * by the additive ClassDetailRequest command bean. Reached by class id from the
 * offering detail tree; links to the class editor and instructor assignment.
 */
@Component({
  selector: 'app-class-detail',
  imports: [RouterLink, ButtonModule, TagModule, TableModule, MessageModule, ProgressSpinnerModule],
  templateUrl: './class-detail.html',
})
export class ClassDetail implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  readonly id = input<string>();

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<ClassDetailResponse | null>(null);

  protected readonly offeringId = computed(() => this.data()?.offeringId ?? null);

  ngOnInit(): void {
    this.page.set('Class Detail');
    const cid = this.id() == null ? NaN : Number(this.id());
    if (!Number.isFinite(cid)) {
      this.error.set('No class was specified.');
      this.loading.set(false);
      return;
    }
    const request: ClassDetailRequest = { classId: cid };
    this.rpc.execute<ClassDetailResponse>('ClassDetailRequest', request).subscribe({
      next: (d) => {
        this.data.set(d);
        if (d.className) this.page.set(d.className);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }
}
