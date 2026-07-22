import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SelectModule } from 'primeng/select';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ButtonModule } from 'primeng/button';
import { switchMap } from 'rxjs/operators';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  ApiError,
  FilterInterface,
  TimetableGridModel,
  TimetableGridRequest,
  TimetableGridResponse,
} from '../../core/models';
import { SolverGridView } from './solver-grid-view';

/**
 * Solver Timetable Grid (command pattern). Loads the grid filter defaults
 * (TimetableGridFilterRequest), then requests the grid (TimetableGridRequest)
 * and renders the chosen model with SolverGridView. The response is one grid
 * per resource (room/instructor/…); a selector picks which to show.
 * Deferred: the full filter UI (resource mode, weeks, times, resolution).
 */
@Component({
  selector: 'app-timetable-grid',
  imports: [FormsModule, SelectModule, MessageModule, ProgressSpinnerModule, ButtonModule, SolverGridView],
  templateUrl: './timetable-grid.html',
})
export class TimetableGrid implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly models = signal<TimetableGridModel[]>([]);
  protected readonly messages = signal<string[]>([]);

  protected modelIndex = 0;
  protected readonly modelOptions = computed(() =>
    this.models().map((m, i) => ({ label: `${m.name}${m.size ? ' (' + m.size + ')' : ''}`, value: i })),
  );
  protected readonly selected = computed<TimetableGridModel | null>(() => this.models()[this.modelIndex] ?? null);

  ngOnInit(): void {
    this.page.set('Timetable Grid');
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    // Load the default filter, then request the grid with it.
    this.rpc
      .execute<FilterInterface>('TimetableGridFilterRequest', {})
      .pipe(switchMap((filter) => this.rpc.execute<TimetableGridResponse>('TimetableGridRequest', { filter } as TimetableGridRequest)))
      .subscribe({
        next: (res) => {
          this.models.set(res.models ?? []);
          this.messages.set((res.pageMessages ?? []).map((m) => m.message ?? '').filter(Boolean));
          this.modelIndex = 0;
          this.loading.set(false);
        },
        error: (e: ApiError) => {
          this.error.set(e.message);
          this.loading.set(false);
        },
      });
  }
}
