import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';

/**
 * Read-only Class Search (legacy classSearch.action), served by the additive
 * ClassesSearchBackend command bean. One request drives two modes:
 *   - subjectAreaId omitted -> subject-area picker rows (loaded on init)
 *   - subjectAreaId set      -> class rows for that subject area (+ course nbr)
 * Both share the SimpleListResponse {columns[], rows:[{id, cells[]}]} shape.
 */

interface SimpleListRow {
  id?: number;
  cells?: string[];
}
interface SimpleListResponse {
  title?: string;
  columns?: string[];
  rows?: SimpleListRow[];
}
interface ClassesSearchRequest {
  subjectAreaId?: number;
  courseNbr?: string;
}
interface SubjectOption {
  id: number;
  label: string;
}

@Component({
  selector: 'app-classes-search',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './classes-search.html',
})
export class ClassesSearch {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly subjects = signal<SubjectOption[]>([]);
  protected readonly subjectAreaId = signal<number | null>(null);
  protected readonly courseNbr = signal<string>('');

  protected readonly loadingSubjects = signal(true);
  protected readonly searching = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly searched = signal(false);

  protected readonly result = signal<SimpleListResponse | null>(null);
  protected readonly columns = computed<string[]>(() => this.result()?.columns ?? []);
  protected readonly rows = computed<SimpleListRow[]>(() => this.result()?.rows ?? []);

  constructor() {
    this.page.set('Classes');
    this.loadSubjects();
  }

  private loadSubjects(): void {
    this.loadingSubjects.set(true);
    this.error.set(null);
    // subjectAreaId omitted -> backend returns the subject-area picker rows.
    this.rpc.execute<SimpleListResponse>('ClassesSearchRequest', {} as ClassesSearchRequest).subscribe({
      next: (d) => {
        const opts = (d.rows ?? [])
          .filter((r) => r.id != null)
          .map((r) => ({ id: r.id as number, label: (r.cells ?? [''])[0] ?? '' }));
        this.subjects.set(opts);
        this.loadingSubjects.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loadingSubjects.set(false);
      },
    });
  }

  search(): void {
    const sid = this.subjectAreaId();
    if (sid == null) return;
    this.searching.set(true);
    this.error.set(null);
    const req: ClassesSearchRequest = { subjectAreaId: sid };
    const nbr = this.courseNbr().trim();
    if (nbr) req.courseNbr = nbr;
    this.rpc.execute<SimpleListResponse>('ClassesSearchRequest', req).subscribe({
      next: (d) => {
        this.result.set(d);
        this.searched.set(true);
        this.searching.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.searching.set(false);
      },
    });
  }
}
