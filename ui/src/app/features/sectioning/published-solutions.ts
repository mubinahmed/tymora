import { Component, OnInit, inject, signal } from '@angular/core';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError, PublishedSectioningSolutionInterface, PublishedSectioningSolutionsRequest } from '../../core/models';

/**
 * Published Sectioning Solutions — opens Wave 7 (Students & Sectioning) with a
 * read-only browse (command pattern). Lists published student-scheduling
 * solutions via PublishedSectioningSolutionsRequest {operation:'LIST'}.
 * Load / select / publish / remove are consequential operations, deferred.
 */
@Component({
  selector: 'app-published-solutions',
  imports: [TableModule, ButtonModule, InputTextModule, TagModule, MessageModule, ProgressSpinnerModule],
  templateUrl: './published-solutions.html',
})
export class PublishedSolutions implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly solutions = signal<PublishedSectioningSolutionInterface[]>([]);

  ngOnInit(): void {
    this.page.set('Published Sectioning Solutions');
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    const request: PublishedSectioningSolutionsRequest = { operation: 'LIST' };
    this.rpc.execute<PublishedSectioningSolutionInterface[]>('PublishedSectioningSolutionsRequest', request).subscribe({
      next: (list) => {
        this.solutions.set(list ?? []);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }
}
