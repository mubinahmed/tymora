import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { InputTextModule } from 'primeng/inputtext';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { TooltipModule } from 'primeng/tooltip';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { downloadCsv } from '../../core/csv';
import { DisplayDatePipe } from '../../shared/display-date.pipe';

type Change = 'SAME' | 'TIME' | 'ROOM' | 'TIME_AND_ROOM' | 'ONLY_A' | 'ONLY_B';

interface SolutionOption {
  id?: number;
  owner?: string;
  ownerName?: string;
  note?: string;
  creator?: string;
  created?: string;
  commitDate?: string;
  committed?: boolean;
  nrAssignments?: number;
}
interface Metric {
  name?: string;
  valueA?: string;
  valueB?: string;
  numberA?: number;
  numberB?: number;
}
interface ClassRow {
  classId?: number;
  className?: string;
  department?: string;
  timeA?: string;
  roomA?: string;
  dateA?: string;
  instructorA?: string;
  timeB?: string;
  roomB?: string;
  dateB?: string;
  instructorB?: string;
  studentConflictsA?: number;
  studentConflictsB?: number;
  change?: Change;
}
interface SolutionCompareResponse {
  solutions?: SolutionOption[];
  a?: SolutionOption;
  b?: SolutionOption;
  metrics?: Metric[];
  rows?: ClassRow[];
  same?: number;
  timeChanged?: number;
  roomChanged?: number;
  timeAndRoomChanged?: number;
  onlyA?: number;
  onlyB?: number;
  message?: string;
}

const CHANGE_LABEL: Record<Change, string> = {
  SAME: 'Same',
  TIME: 'Time differs',
  ROOM: 'Room differs',
  TIME_AND_ROOM: 'Time and room differ',
  ONLY_A: 'Only in A',
  ONLY_B: 'Only in B',
};

/** Statistics where a higher value is better (everything else: lower is better). */
const HIGHER_IS_BETTER = /assigned variables|assigned classes|^assigned/i;
/** Solver run statistics: shown with the difference but not judged better / worse. */
const NEUTRAL = /^(time|iteration|memory usage|speed)$/i;

/**
 * Side-by-side comparison of two saved course timetables (scenarios): the solution statistics of
 * each next to each other with the difference, and class by class where the two place a class
 * differently (time, room, only in one), with the student conflicts of each placement.
 * Backed by SolutionCompareRequest; opened from Saved Timetables (or directly with ?a=&b=).
 */
@Component({
  selector: 'app-solution-compare',
  imports: [DisplayDatePipe, 
    DatePipe,
    FormsModule,
    RouterLink,
    ButtonModule,
    SelectModule,
    TableModule,
    TagModule,
    MessageModule,
    InputTextModule,
    ProgressSpinnerModule,
    TooltipModule,
  ],
  templateUrl: './solution-compare.html',
  styleUrl: './solution-compare.scss',
})
export class SolutionCompare implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<SolutionCompareResponse | null>(null);
  protected solutionA: number | null = null;
  protected solutionB: number | null = null;
  protected readonly changeFilter = signal<'DIFF' | Change | 'ALL'>('DIFF');
  protected readonly search = signal('');
  protected readonly metricsDiffOnly = signal(false);

  protected readonly options = computed(() =>
    (this.data()?.solutions ?? []).map((s) => ({ value: s.id, label: this.optionLabel(s) })),
  );
  protected readonly compared = computed(() => !!(this.data()?.a && this.data()?.b));
  protected readonly metrics = computed(() => {
    const all = this.data()?.metrics ?? [];
    return this.metricsDiffOnly() ? all.filter((m) => (m.valueA ?? '') !== (m.valueB ?? '')) : all;
  });
  protected readonly differing = computed(() => (this.data()?.rows ?? []).filter((r) => r.change !== 'SAME').length);
  protected readonly rows = computed(() => {
    const f = this.changeFilter();
    const q = this.search().trim().toLowerCase();
    return (this.data()?.rows ?? []).filter((r) => {
      if (f === 'DIFF' && r.change === 'SAME') return false;
      if (f !== 'DIFF' && f !== 'ALL' && r.change !== f) return false;
      if (!q) return true;
      return [r.className, r.department, r.timeA, r.timeB, r.roomA, r.roomB, r.instructorA, r.instructorB]
        .join(' ')
        .toLowerCase()
        .includes(q);
    });
  });
  protected readonly filterOptions = computed(() => {
    const d = this.data();
    const opt = (value: 'DIFF' | Change | 'ALL', label: string, n?: number) => ({ value, label: n == null ? label : `${label} (${n})` });
    return [
      opt('DIFF', 'All differences', this.differing()),
      opt('TIME', CHANGE_LABEL.TIME, d?.timeChanged),
      opt('ROOM', CHANGE_LABEL.ROOM, d?.roomChanged),
      opt('TIME_AND_ROOM', CHANGE_LABEL.TIME_AND_ROOM, d?.timeAndRoomChanged),
      opt('ONLY_A', CHANGE_LABEL.ONLY_A, d?.onlyA),
      opt('ONLY_B', CHANGE_LABEL.ONLY_B, d?.onlyB),
      opt('SAME', CHANGE_LABEL.SAME, d?.same),
      opt('ALL', 'All classes', (d?.rows ?? []).length),
    ];
  });

  ngOnInit(): void {
    this.page.set('Compare Saved Timetables');
    const q = this.route.snapshot.queryParamMap;
    this.solutionA = q.get('a') ? Number(q.get('a')) : null;
    this.solutionB = q.get('b') ? Number(q.get('b')) : null;
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.rpc
      .execute<SolutionCompareResponse>('SolutionCompareRequest', { solutionA: this.solutionA, solutionB: this.solutionB })
      .subscribe({
        next: (d) => {
          this.data.set(d);
          this.loading.set(false);
        },
        error: (e: ApiError) => {
          this.error.set(e.message);
          this.loading.set(false);
        },
      });
  }

  protected compare(): void {
    if (this.solutionA == null || this.solutionB == null) return;
    this.router.navigate([], { queryParams: { a: this.solutionA, b: this.solutionB }, replaceUrl: true });
    this.changeFilter.set('DIFF');
    this.load();
  }

  protected swap(): void {
    [this.solutionA, this.solutionB] = [this.solutionB, this.solutionA];
    this.compare();
  }

  protected optionLabel(s: SolutionOption): string {
    const d = s.created ? new Date(s.created) : null;
    const pad = (n: number) => String(n).padStart(2, '0');
    const when = d ? `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()}, ${pad(d.getHours())}:${pad(d.getMinutes())}` : '';
    return `${s.owner} · ${when}${s.committed ? ' · committed' : ''}${s.note ? ' · ' + s.note : ''}`;
  }

  protected changeLabel(c: Change | undefined): string {
    return CHANGE_LABEL[c ?? 'SAME'];
  }

  protected changeSeverity(c: Change | undefined): 'secondary' | 'warn' | 'info' | 'danger' | 'success' {
    switch (c) {
      case 'TIME':
      case 'ROOM':
        return 'warn';
      case 'TIME_AND_ROOM':
        return 'danger';
      case 'ONLY_A':
      case 'ONLY_B':
        return 'info';
      default:
        return 'secondary';
    }
  }

  /** "+3" / "-1.5" difference B - A, and whether B is better or worse. */
  protected metricDelta(m: Metric): { text: string; cls: string } {
    if (m.numberA == null || m.numberB == null || m.numberA === m.numberB) return { text: '', cls: '' };
    const d = m.numberB - m.numberA;
    const r = Math.abs(d - Math.round(d)) < 1e-9 ? String(Math.round(d)) : d.toFixed(2);
    const text = (d > 0 ? '+' : '') + r;
    if (NEUTRAL.test(m.name ?? '')) return { text, cls: 'muted' };
    const better = HIGHER_IS_BETTER.test(m.name ?? '') ? d > 0 : d < 0;
    return { text, cls: better ? 'better' : 'worse' };
  }

  protected conflictDelta(r: ClassRow): string {
    if (r.studentConflictsA == null || r.studentConflictsB == null || r.studentConflictsA === r.studentConflictsB) return '';
    return r.studentConflictsB > r.studentConflictsA ? 'worse' : 'better';
  }

  protected exportCsv(): void {
    const cols = ['Class', 'Department', 'Difference', 'Time A', 'Date A', 'Room A', 'Instructor A', 'Student conflicts A', 'Time B', 'Date B', 'Room B', 'Instructor B', 'Student conflicts B'];
    downloadCsv(
      'Timetable comparison',
      cols,
      this.rows().map((r) => [
        r.className ?? '', r.department ?? '', this.changeLabel(r.change),
        r.timeA ?? '', r.dateA ?? '', r.roomA ?? '', r.instructorA ?? '', r.studentConflictsA?.toString() ?? '',
        r.timeB ?? '', r.dateB ?? '', r.roomB ?? '', r.instructorB ?? '', r.studentConflictsB?.toString() ?? '',
      ]),
    );
  }
}
