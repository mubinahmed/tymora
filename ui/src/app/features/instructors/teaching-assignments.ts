import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  ApiError,
  InstructorInfo,
  InstructorInterface_DepartmentInterface,
  TeachingAssignmentsPageRequest,
  TeachingRequestInfo,
  TeachingRequestsPagePropertiesResponse,
} from '../../core/models';

/**
 * Teaching Assignments (command pattern), filter-driven by department. Lists
 * instructors with their assigned teaching load / requests via
 * TeachingAssignmentsPageRequest. Read-only load overview; the interactive
 * assignment editor + solver suggestions are deferred (solver-adjacent).
 */
@Component({
  selector: 'app-teaching-assignments',
  imports: [
    FormsModule,
    TableModule,
    InputTextModule,
    SelectModule,
    TagModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './teaching-assignments.html',
})
export class TeachingAssignments implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly loading = signal(true);
  protected readonly loadingList = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly departments = signal<InstructorInterface_DepartmentInterface[]>([]);
  protected readonly instructors = signal<InstructorInfo[]>([]);
  protected readonly searched = signal(false);

  protected departmentId: number | null = null;

  ngOnInit(): void {
    this.page.set('Teaching Assignments');
    this.rpc.execute<TeachingRequestsPagePropertiesResponse>('TeachingRequestsPagePropertiesRequest', {}).subscribe({
      next: (p) => {
        this.departments.set(p.departments ?? []);
        this.departmentId = p.lastDepartmentId ?? p.departments?.[0]?.id ?? null;
        this.loading.set(false);
        if (this.departmentId != null) this.loadAssignments();
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  loadAssignments(): void {
    if (this.departmentId == null) return;
    this.loadingList.set(true);
    this.error.set(null);
    const request: TeachingAssignmentsPageRequest = {
      request: { command: 'ENUMERATE', options: { departmentId: [String(this.departmentId)] } },
    };
    this.rpc.execute<InstructorInfo[]>('TeachingAssignmentsPageRequest', request).subscribe({
      next: (list) => {
        this.instructors.set(list ?? []);
        this.searched.set(true);
        this.loadingList.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loadingList.set(false);
      },
    });
  }

  courses(i: InstructorInfo): string {
    return (i.assignedRequests ?? [])
      .map((r: TeachingRequestInfo) => r.course?.courseName)
      .filter(Boolean)
      .join(', ') || '—';
  }

  over(i: InstructorInfo): boolean {
    return (i.assignedLoad ?? 0) > (i.maxLoad ?? 0);
  }
}
