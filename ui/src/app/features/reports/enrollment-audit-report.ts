import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { SelectModule } from 'primeng/select';
import { MultiSelectModule } from 'primeng/multiselect';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import {
  EnrollmentAuditGenerateRequest,
  EnrollmentAuditInitResponse,
  EnrollmentAuditReportResponse,
  Option,
  SubjectAreaOption,
} from './enrollment-audit-report.models';

/**
 * Enrollment Audit PDF Reports (legacy enrollmentAuditPdfReport.action) — pick one or more of the
 * four registered audit reports, an output mode, and either the whole session or selected subject
 * areas; the backend generates the file(s) (zipping when several) and returns the bytes, which are
 * downloaded here. E-mail delivery from the legacy screen is not migrated.
 */
@Component({
  selector: 'app-enrollment-audit-report',
  imports: [
    FormsModule,
    ButtonModule,
    CheckboxModule,
    SelectModule,
    MultiSelectModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './enrollment-audit-report.html',
})
export class EnrollmentAuditReport implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);

  protected readonly loading = signal(true);
  protected readonly generating = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly reportOptions = signal<Option[]>([]);
  protected readonly modeOptions = signal<Option[]>([]);
  protected readonly subjectAreas = signal<SubjectAreaOption[]>([]);

  // Form state
  protected selectedReports: string[] = [];
  protected mode = '';
  protected all = true;
  protected selectedSubjects: number[] = [];
  protected showExternalId = false;
  protected showStudentName = false;

  protected readonly canGenerate = computed(() => this.reportOptions().length > 0);

  ngOnInit(): void {
    this.page.set('Enrollment Audit PDF Reports');
    this.rpc.execute<EnrollmentAuditInitResponse>('EnrollmentAuditInitRequest', {}).subscribe({
      next: (d) => {
        this.reportOptions.set(d.reports ?? []);
        this.modeOptions.set(d.modes ?? []);
        this.subjectAreas.set(d.subjectAreas ?? []);
        this.mode = d.defaultMode ?? d.modes?.[0]?.value ?? '';
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  generate(): void {
    if (this.selectedReports.length === 0) {
      this.messages.add({ severity: 'warn', summary: 'No report', detail: 'Select at least one report.' });
      return;
    }
    if (!this.all && this.selectedSubjects.length === 0) {
      this.messages.add({ severity: 'warn', summary: 'No subject area', detail: 'Select at least one subject area, or choose all subjects.' });
      return;
    }
    this.generating.set(true);
    const request: EnrollmentAuditGenerateRequest = {
      reports: this.selectedReports,
      mode: this.mode,
      all: this.all,
      subjectIds: this.all ? [] : this.selectedSubjects,
      showExternalId: this.showExternalId,
      showStudentName: this.showStudentName,
    };
    this.rpc.execute<EnrollmentAuditReportResponse>('EnrollmentAuditGenerateRequest', request).subscribe({
      next: (d) => {
        this.generating.set(false);
        this.download(d);
      },
      error: (e: ApiError) => {
        this.generating.set(false);
        this.messages.add({ severity: 'error', summary: 'Report failed', detail: e.message });
      },
    });
  }

  private download(res: EnrollmentAuditReportResponse): void {
    try {
      const bytes = Uint8Array.from(res.content ?? []);
      const blob = new Blob([bytes], { type: res.contentType || 'application/octet-stream' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = res.fileName || 'enrollment-audit-report';
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
      this.messages.add({ severity: 'success', summary: 'Report ready', detail: res.fileName });
    } catch {
      this.messages.add({ severity: 'error', summary: 'Download failed', detail: 'Could not save the generated report.' });
    }
  }
}
