import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { CardModule } from 'primeng/card';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  ApiError,
  ReportTypeInterface,
  SectioningReportRpcRequest,
  SectioningReportRpcResponse,
  SectioningReportTypesRpcRequest,
} from '../../core/models';

interface Col {
  /** original index into the report row (string[]) */
  index: number;
  field: string;
  label: string;
  percent: boolean;
}

/**
 * Batch Scheduling Reports (legacy GWT SectioningReports(false) — the offline /
 * batch variant that reads from the running student sectioning solver).
 *
 * Flow: SectioningReportTypesRpcRequest({online:false}) loads the catalog of
 * report types (each carries a display name, a report implementation class, and
 * a flat list of key/value parameter pairs baked into the type). Selecting a
 * report and pressing Execute sends SectioningReportRpcRequest whose `parameters`
 * map = { report: <implementation>, online: "false", ...the type's own pairs,
 * filter?: <text> }. The backend returns SectioningReportRpcResponse.report as a
 * raw grid (string[][]) where row 0 is the header and the rest are data rows;
 * the first column is hidden when its header starts with "__" (it carries a
 * linkable object id in the legacy UI). Cells may contain embedded newlines
 * (rendered as <br>) and numeric/percent values are lightly formatted.
 *
 * Deferred vs. the GWT original: the SectioningStatusFilterBox (a rich student
 * filter builder) is replaced by an optional free-text filter input; row
 * drill-down links (class/offering/student/etc.), column sorting, the parent-row
 * value inheritance for blank cells, student selection + bulk actions (email,
 * status change, mass cancel, reload, override checks), and CSV/XLS export /
 * print (EncodeQueryRpcRequest + /export servlet). Result paging is handled
 * client-side by the table paginator rather than the legacy 100-row windows.
 */
@Component({
  selector: 'app-batch-sectioning-reports',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    MessageModule,
    ProgressSpinnerModule,
    CardModule,
  ],
  templateUrl: './batch-sectioning-reports.html',
})
export class BatchSectioningReports implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly loading = signal(true);
  protected readonly executing = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly resultsMessage = signal<string | null>(null);

  protected readonly reportTypes = signal<ReportTypeInterface[]>([]);
  protected readonly report = signal<string[][] | null>(null);

  /** reference of the currently selected report type (bound to the select) */
  protected reference: string | null = null;
  /** optional free-text student filter (replaces SectioningStatusFilterBox) */
  protected filter = '';

  protected readonly reportOptions = computed(() =>
    this.reportTypes().map((t) => ({ label: t.name ?? '', value: t.reference ?? '' })),
  );

  protected readonly selectedType = computed<ReportTypeInterface | null>(
    () => this.reportTypes().find((t) => t.reference === this.reference) ?? null,
  );

  protected readonly showFilter = computed<boolean>(() => this.selectedType()?.filter === true);

  /** visible (non-hidden) columns derived from the report header row */
  protected readonly cols = computed<Col[]>(() => {
    const data = this.report() ?? [];
    if (!data.length) return [];
    const header = data[0];
    const hideFirst = (header[0] ?? '').startsWith('__');
    return header
      .map((h, index) => ({
        index,
        field: String(index),
        label: (h ?? '').replace(/_/g, ' ').trim(),
        percent: (h ?? '').includes('%'),
      }))
      .filter((c) => !(hideFirst && c.index === 0));
  });

  protected readonly rows = computed<string[][]>(() => (this.report() ?? []).slice(1));

  ngOnInit(): void {
    this.page.set('Batch Scheduling Reports');
    this.loadTypes();
  }

  private loadTypes(): void {
    this.loading.set(true);
    this.error.set(null);
    const request: SectioningReportTypesRpcRequest = { online: false };
    this.rpc
      .execute<ReportTypeInterface[]>('SectioningReportTypesRpcRequest', request)
      .subscribe({
        next: (list) => {
          this.reportTypes.set(list ?? []);
          if (!list?.length) this.error.set('No report types are available.');
          this.loading.set(false);
        },
        error: (e: ApiError) => {
          this.error.set(e.message);
          this.loading.set(false);
        },
      });
  }

  onReportChange(): void {
    this.error.set(null);
    this.resultsMessage.set(null);
    this.report.set(null);
  }

  execute(): void {
    const type = this.selectedType();
    if (!type) {
      this.error.set('No report selected.');
      return;
    }

    const parameters: { [key: string]: string } = {
      report: type.implementation ?? '',
      online: 'false',
    };
    const params = type.parameters ?? [];
    for (let i = 0; i + 1 < params.length; i += 2) {
      parameters[params[i]] = params[i + 1];
    }
    if (type.filter && this.filter.trim()) {
      parameters['filter'] = this.filter.trim();
    }

    const request: SectioningReportRpcRequest = { parameters };
    this.executing.set(true);
    this.error.set(null);
    this.resultsMessage.set(null);
    this.report.set(null);
    this.rpc
      .execute<SectioningReportRpcResponse>('SectioningReportRpcRequest', request)
      .subscribe({
        next: (res) => {
          const data = res?.report ?? [];
          const size = data.length;
          if (size <= 1) {
            this.report.set(null);
            this.resultsMessage.set('The report produced no results.');
          } else {
            this.report.set(data);
            this.resultsMessage.set(`Showing all ${size - 1} line(s).`);
          }
          this.executing.set(false);
        },
        error: (e: ApiError) => {
          this.error.set(e.message);
          this.executing.set(false);
        },
      });
  }

  cell(row: string[], c: Col): string {
    const raw = row[c.index];
    if (raw == null || raw === '') return '';
    return raw
      .split('\n')
      .map((t) => this.formatToken(t, c.percent))
      .join('<br>');
  }

  /** Light numeric formatting mirroring the legacy NumberFormat usage. */
  private formatToken(t: string, percent: boolean): string {
    const n = Number(t);
    if (t.trim() !== '' && !Number.isNaN(n)) {
      if (percent) return (n * 100).toFixed(1) + '%';
      if (/^-?\d+\.\d+$/.test(t.trim())) return n.toFixed(2);
    }
    return t;
  }
}
