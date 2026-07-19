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
import { OfferingDetailRequest, OfferingDetailResponse } from './offering-detail.models';

/**
 * Instructional Offering Detail (legacy instructionalOfferingDetail.action) — the
 * read hub of the offering/class detail tree, now backed by the additive
 * InstructionalOfferingDetailRequest command bean. Reached by course-offering id
 * from the offerings search (the request resolves the offering from either id).
 */
@Component({
  selector: 'app-offering-detail',
  imports: [RouterLink, ButtonModule, TagModule, TableModule, MessageModule, ProgressSpinnerModule],
  templateUrl: './offering-detail.html',
})
export class OfferingDetail implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  /** Course-offering id from the route (offerings search links by course id). */
  readonly id = input<string>();

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<OfferingDetailResponse | null>(null);

  /** Offering-scoped id for the modify / cross-lists / instructor screens. */
  protected readonly offeringId = computed(() => this.data()?.offeringId ?? this.id());
  /** Controlling course id for the course-offering editor. */
  protected readonly courseId = computed(() => this.data()?.controllingCourseId ?? this.id());

  ngOnInit(): void {
    this.page.set('Instructional Offering Detail');
    const raw = this.id();
    const cid = raw == null ? NaN : Number(raw);
    if (!Number.isFinite(cid)) {
      this.error.set('No offering was specified.');
      this.loading.set(false);
      return;
    }
    const request: OfferingDetailRequest = { courseOfferingId: cid };
    this.rpc.execute<OfferingDetailResponse>('InstructionalOfferingDetailRequest', request).subscribe({
      next: (d) => {
        this.data.set(d);
        if (d.courseName) this.page.set(d.courseName);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }
}
