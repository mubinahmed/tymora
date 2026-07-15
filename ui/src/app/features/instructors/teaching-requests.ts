import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  ApiError,
  InstructorAssignmentRequest,
  InstructorInfo,
  InstructorInterface_SubjectAreaInterface,
  SectionInfo,
  TeachingRequestInfo,
  TeachingRequestsPagePropertiesResponse,
  TeachingRequestsPageRequest,
} from '../../core/models';

/**
 * Assigned Teaching Requests (command pattern). Filter-driven: pick a subject
 * area (from TeachingRequestsPagePropertiesRequest), then list its teaching
 * requests via TeachingRequestsPageRequest. Read-only view of course / sections
 * / load / assigned instructors; assignment editing is solver-adjacent and
 * deferred (see Teaching Assignments).
 */
@Component({
  selector: 'app-teaching-requests',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    TagModule,
    MessageModule,
    ProgressSpinnerModule,
    ConfirmDialogModule,
  ],
  providers: [ConfirmationService],
  templateUrl: './teaching-requests.html',
})
export class TeachingRequests implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private confirm = inject(ConfirmationService);
  private messages = inject(MessageService);

  protected readonly loading = signal(true);
  protected readonly loadingList = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly subjectAreas = signal<InstructorInterface_SubjectAreaInterface[]>([]);
  protected readonly requests = signal<TeachingRequestInfo[]>([]);
  protected readonly searched = signal(false);

  protected subjectAreaId: number | null = null;

  ngOnInit(): void {
    this.page.set('Teaching Requests');
    this.rpc.execute<TeachingRequestsPagePropertiesResponse>('TeachingRequestsPagePropertiesRequest', {}).subscribe({
      next: (p) => {
        this.subjectAreas.set(p.subjecAreas ?? []);
        this.subjectAreaId = p.lastSubjectAreaId ?? p.subjecAreas?.[0]?.id ?? null;
        this.loading.set(false);
        if (this.subjectAreaId != null) this.loadRequests();
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  loadRequests(): void {
    if (this.subjectAreaId == null) return;
    this.loadingList.set(true);
    this.error.set(null);
    const request: TeachingRequestsPageRequest = {
      request: { command: 'ENUMERATE', options: { subjectId: [String(this.subjectAreaId)] } },
    };
    this.rpc.execute<TeachingRequestInfo[]>('TeachingRequestsPageRequest', request).subscribe({
      next: (list) => {
        this.requests.set(list ?? []);
        this.searched.set(true);
        this.loadingList.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loadingList.set(false);
      },
    });
  }

  sections(r: TeachingRequestInfo): string {
    return (r.sections ?? [])
      .map((s: SectionInfo) => [s.type, s.sectionName].filter(Boolean).join(' '))
      .join(', ');
  }

  instructorNames(r: TeachingRequestInfo): string {
    return (r.instructors ?? []).map((i: InstructorInfo) => i.name).filter(Boolean).join(', ') || '—';
  }

  /** Unassign the instructor at `index` of a request (InstructorAssignmentRequest,
   *  instructor=null). The full request is sent so the backend knows the prior
   *  assignment at that index. Assigning via suggestions is a separate build. */
  unassign(r: TeachingRequestInfo, index: number): void {
    const instr = r.instructors?.[index];
    this.confirm.confirm({
      header: 'Unassign instructor',
      message: `Remove ${instr?.name ?? 'this instructor'} from ${r.course?.courseName ?? 'the request'}?`,
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        const request: InstructorAssignmentRequest = {
          assignments: [{ request: r, index, instructor: undefined }],
          ignoreConflicts: true,
        };
        this.rpc.execute<unknown>('InstructorAssignmentRequest', request).subscribe({
          next: () => {
            this.messages.add({ severity: 'success', summary: 'Instructor unassigned' });
            this.loadRequests();
          },
          error: (e: ApiError) => this.messages.add({ severity: 'error', summary: 'Unassign failed', detail: e.message }),
        });
      },
    });
  }
}
