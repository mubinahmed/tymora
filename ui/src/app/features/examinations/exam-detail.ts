import { Component, OnInit, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { ExamDetailRequest, ExamEditResponse } from './exam-edit.models';

/**
 * Examination Detail (legacy examDetail.action) — read view of one exam, backed by
 * ExamDetailRequest. Reached by exam id from the examinations list; links to the editor.
 */
@Component({
  selector: 'app-exam-detail',
  imports: [RouterLink, ButtonModule, TagModule, MessageModule, ProgressSpinnerModule],
  templateUrl: './exam-detail.html',
})
export class ExamDetail implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  readonly id = input<string>();

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<ExamEditResponse | null>(null);

  ngOnInit(): void {
    this.page.set('Examination Detail');
    const eid = this.id() == null ? NaN : Number(this.id());
    if (!Number.isFinite(eid)) {
      this.error.set('No examination was specified.');
      this.loading.set(false);
      return;
    }
    const request: ExamDetailRequest = { examId: eid };
    this.rpc.execute<ExamEditResponse>('ExamDetailRequest', request).subscribe({
      next: (d) => {
        this.data.set(d);
        if (d.label) this.page.set(d.label);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }
}
