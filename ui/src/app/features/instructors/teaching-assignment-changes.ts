import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  ApiError,
  AssignmentChangesRequest,
  AssignmentChangesResponse,
  AssignmentInfo,
  ChangesType,
  InstructorInfo,
  SectionInfo,
  TeachingRequestInfo,
  TeachingRequestsPagePropertiesResponse,
} from '../../core/models';

/** One flattened assignment-change row: the request plus the before/after instructor. */
interface ChangeRow {
  request: TeachingRequestInfo;
  baseInstructor: InstructorInfo | null;
  instructor: InstructorInfo | null;
  conflicts: string[];
}

/**
 * Teaching Assignment Changes (command pattern). Mirrors the legacy
 * TeachingAssignmentsChangesPage: pick a comparison base (Initial / Best /
 * Saved) and list how instructor assignments changed against it.
 *
 * On load we call TeachingRequestsPagePropertiesRequest purely as the
 * access/permission gate (matching the GWT page); the actual data comes from
 * AssignmentChangesRequest which returns AssignmentInfo rows. Each row is
 * flattened into base-instructor -> new-instructor for a given request index.
 * The preference / time-grid / attribute / objective columns are deferred; the
 * core course/section/time/room/load and the instructor change are shown.
 */
@Component({
  selector: 'app-teaching-assignment-changes',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    SelectModule,
    TagModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './teaching-assignment-changes.html',
})
export class TeachingAssignmentChanges implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  /** Comparison bases; value is the ChangesType enum sent to the backend. */
  protected readonly bases = [
    { label: 'Initial Solution', value: 'INITIAL' as ChangesType },
    { label: 'Best Solution', value: 'BEST' as ChangesType },
    { label: 'Saved Assignments', value: 'SAVED' as ChangesType },
  ];
  protected base: ChangesType = 'INITIAL';

  protected readonly loading = signal(false);
  protected readonly searching = signal(false);
  protected readonly ready = signal(false);
  protected readonly searched = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly rows = signal<ChangeRow[]>([]);

  protected readonly baseLabel = computed(
    () => this.bases.find((b) => b.value === this.base)?.label ?? '',
  );

  ngOnInit(): void {
    this.page.set('Teaching Assignment Changes');
    this.loadProperties();
  }

  /** Access gate: the properties request fails if the user cannot see the page. */
  private loadProperties(): void {
    this.loading.set(true);
    this.error.set(null);
    this.rpc
      .execute<TeachingRequestsPagePropertiesResponse>('TeachingRequestsPagePropertiesRequest', {})
      .subscribe({
        next: () => {
          this.ready.set(true);
          this.loading.set(false);
        },
        error: (e: ApiError) => {
          this.error.set(e.message);
          this.loading.set(false);
        },
      });
  }

  search(): void {
    if (!this.ready()) return;
    this.searching.set(true);
    this.error.set(null);
    const request: AssignmentChangesRequest = { type: this.base };
    this.rpc.execute<AssignmentChangesResponse>('AssignmentChangesRequest', request).subscribe({
      next: (res) => {
        this.rows.set((res.changes ?? []).map((a) => this.toRow(a)));
        this.searched.set(true);
        this.searching.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.searching.set(false);
      },
    });
  }

  private toRow(a: AssignmentInfo): ChangeRow {
    const request = a.request ?? {};
    return {
      request,
      baseInstructor: this.instructorAt(request, a.index ?? 0),
      instructor: a.instructor ?? null,
      conflicts: a.conflicts ?? [],
    };
  }

  /** Replicates TeachingRequestInfo.getInstructor(index) from the legacy page. */
  private instructorAt(request: TeachingRequestInfo, index: number): InstructorInfo | null {
    const list = request.instructors ?? [];
    if (!list.length || index < 0) return null;
    for (let i = 0; i < list.length; i++) {
      const ins = list[i];
      if (ins.assignmentIndex != null && ins.assignmentIndex === index) return ins;
      if (ins.assignmentIndex == null && i === index) return ins;
    }
    return null;
  }

  sectionLabel(s: SectionInfo): string {
    return (s.type ?? '') + (s.externalId ? ' ' + s.externalId : '');
  }

  instructorName(i: InstructorInfo | null): string {
    if (!i) return '';
    return i.name || i.externalId || '';
  }

  assignedCount(r: TeachingRequestInfo): string {
    return `${r.instructors?.length ?? 0} / ${r.nrInstructors ?? 0}`;
  }
}
