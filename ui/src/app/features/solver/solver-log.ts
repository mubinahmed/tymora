import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { CardModule } from 'primeng/card';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  ApiError,
  ProgressLogLevel,
  ProgressMessage,
  SolutionLog,
  SolverLogPageRequest,
  SolverLogPageResponse,
  SolverType,
} from '../../core/models';

/** A flat log section: either the live solver log, or one saved solution's log. */
interface LogSection {
  owner: string | null;
  messages: ProgressMessage[];
}

/**
 * Solver Log (command pattern → SolverLogPageRequest / SolverLogPageResponse).
 *
 * Legacy GWT SolverLogPage reads the solver type from the URL `type` param
 * (course/exam/student/instructor) and a log level from a cookie, then renders
 * the progress log of the currently-running solver. When no solver is running
 * for the COURSE type the backend returns the saved log(s) of the selected
 * solution(s) instead (grouped by owner). This screen exposes the type + level
 * as selects and shows the returned messages in a table, colour-coded by level.
 *
 * The legacy 1s auto-refresh timer (append-since-lastDate) is deferred; a manual
 * Refresh button re-runs the full request.
 */
@Component({
  selector: 'app-solver-log',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    SelectModule,
    TagModule,
    MessageModule,
    ProgressSpinnerModule,
    CardModule,
  ],
  templateUrl: './solver-log.html',
  styles: [
    `
      .solver-log {
        padding: 1rem;
        display: flex;
        flex-direction: column;
        gap: 1rem;
      }
      .toolbar {
        display: flex;
        gap: 0.5rem;
        align-items: center;
        flex-wrap: wrap;
      }
      .center {
        display: flex;
        justify-content: center;
        padding: 2rem;
      }
      .section-owner {
        font-weight: 600;
        padding: 0.75rem 1rem;
      }
      .mono {
        font-family: monospace;
        white-space: nowrap;
      }
      .trace {
        margin: 0.25rem 0 0;
        font-size: 0.8rem;
        white-space: pre-wrap;
        color: var(--p-text-muted-color, #888);
      }
    `,
  ],
})
export class SolverLog implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private route = inject(ActivatedRoute);

  protected readonly types: { label: string; value: SolverType }[] = [
    { label: 'Course Timetabling', value: 'COURSE' },
    { label: 'Examination Timetabling', value: 'EXAM' },
    { label: 'Student Scheduling', value: 'STUDENT' },
    { label: 'Instructor Scheduling', value: 'INSTRUCTOR' },
  ];
  protected type: SolverType = 'COURSE';

  protected readonly levels: { label: string; value: ProgressLogLevel }[] = [
    { label: 'Trace', value: 'TRACE' },
    { label: 'Debug', value: 'DEBUG' },
    { label: 'Progress', value: 'PROGRESS' },
    { label: 'Info', value: 'INFO' },
    { label: 'Stage', value: 'STAGE' },
    { label: 'Warning', value: 'WARN' },
    { label: 'Error', value: 'ERROR' },
    { label: 'Fatal', value: 'FATAL' },
  ];
  protected level: ProgressLogLevel = 'INFO';

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly loaded = signal(false);
  protected readonly sections = signal<LogSection[]>([]);

  protected readonly isEmpty = computed(
    () => this.loaded() && this.sections().every((s) => s.messages.length === 0),
  );

  ngOnInit(): void {
    this.page.set('Solver Log');
    const t = (this.route.snapshot.queryParamMap.get('type') || '').toUpperCase();
    if (t === 'COURSE' || t === 'EXAM' || t === 'STUDENT' || t === 'INSTRUCTOR') {
      this.type = t;
    }
    this.refresh();
  }

  refresh(): void {
    this.loading.set(true);
    this.error.set(null);
    const request: SolverLogPageRequest = { type: this.type, level: this.level };
    this.rpc.execute<SolverLogPageResponse>('SolverLogPageRequest', request).subscribe({
      next: (res) => {
        this.sections.set(this.toSections(res));
        this.loaded.set(true);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.sections.set([]);
        this.loaded.set(true);
        this.loading.set(false);
      },
    });
  }

  private toSections(res: SolverLogPageResponse): LogSection[] {
    if (res.log && res.log.length) {
      return [{ owner: null, messages: res.log }];
    }
    if (res.solutionLogs && res.solutionLogs.length) {
      return res.solutionLogs
        .filter((l: SolutionLog) => (l.log?.length ?? 0) > 0)
        .map((l: SolutionLog) => ({ owner: l.owner ?? '', messages: l.log ?? [] }));
    }
    return [{ owner: null, messages: [] }];
  }

  /** PrimeNG Tag severity per progress level. */
  severity(level?: ProgressLogLevel): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
    switch (level) {
      case 'WARN':
        return 'warn';
      case 'ERROR':
      case 'FATAL':
        return 'danger';
      case 'STAGE':
        return 'info';
      case 'TRACE':
      case 'DEBUG':
        return 'secondary';
      default:
        return 'success';
    }
  }
}
