import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  ApiError,
  ReportTypeInterface,
  SectioningReportRpcRequest,
  SectioningReportRpcResponse,
  SectioningReportTypesRpcRequest,
  TableInterface,
} from '../../core/models';
import { RpcTable } from '../../shared/rpc-table';

/**
 * Online Scheduling Reports (legacy GWT SectioningReports page, key "onlinereport",
 * i.e. SectioningReports(online=true)).
 *
 * Flow:
 *  1. SectioningReportTypesRpcRequest({ online: true }) -> ReportTypeInterface[]
 *     (the report catalog: reference, name, implementation, parameters[], filter).
 *  2. Selecting a report and pressing Execute runs SectioningReportRpcRequest with a
 *     string->string parameters map built from { report: implementation, online: 'true',
 *     ...type.parameters pairs, filter?: <text> }.
 *  3. The response is a String[][] where row 0 is the header and the rest are data
 *     rows. We adapt it into a TableInterface and render it with the shared RpcTable
 *     (columns whose header starts with "__" carry a linkable object id and are hidden,
 *     matching the legacy behaviour).
 *
 * Deferred vs. the GWT original: the rich SectioningStatusFilterBox (replaced here by a
 * plain free-text filter string), CSV/XLS export + print (EncodeQueryRpcRequest +
 * /export servlet), row drill-down links, column sort, hierarchical value-collapsing /
 * grouping, percent/decimal cell reformatting, student selection and the bulk student
 * operations (email / mass cancel / reload / status / note), and URL history state.
 */
@Component({
  selector: 'app-online-sectioning-reports',
  imports: [
    FormsModule,
    ButtonModule,
    CardModule,
    SelectModule,
    InputTextModule,
    MessageModule,
    ProgressSpinnerModule,
    RpcTable,
  ],
  templateUrl: './online-sectioning-reports.html',
  styles: [
    `
      .filter-row {
        display: flex;
        align-items: center;
        gap: 1rem;
        margin-bottom: 1rem;
      }
      .filter-row label {
        min-width: 6rem;
        font-weight: 600;
      }
      .actions {
        display: flex;
        justify-content: flex-end;
      }
      .results-msg {
        margin-bottom: 0.75rem;
        font-style: italic;
        opacity: 0.8;
      }
    `,
  ],
})
export class OnlineSectioningReports implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  /** This screen is the online variant of the sectioning reports page. */
  private readonly online = true;

  protected readonly loading = signal(true);
  protected readonly executing = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly resultsMessage = signal<string | null>(null);

  protected readonly reportTypes = signal<ReportTypeInterface[]>([]);
  protected readonly table = signal<TableInterface | null>(null);

  /** reference of the currently selected report (bound to the select) */
  protected reportRef: string | null = null;
  /** free-text filter value (legacy SectioningStatusFilterBox is deferred) */
  protected filter = '';

  protected readonly reportOptions = computed(() =>
    this.reportTypes().map((t) => ({ label: t.name ?? '', value: t.reference ?? '' })),
  );

  protected readonly selectedType = computed<ReportTypeInterface | null>(
    () => this.reportTypes().find((t) => t.reference === this.reportRef) ?? null,
  );

  ngOnInit(): void {
    this.page.set('Online Scheduling Reports');
    this.loadTypes();
  }

  private loadTypes(): void {
    this.loading.set(true);
    this.error.set(null);
    const request: SectioningReportTypesRpcRequest = { online: this.online };
    this.rpc
      .execute<ReportTypeInterface[]>('SectioningReportTypesRpcRequest', request)
      .subscribe({
        next: (list) => {
          this.reportTypes.set(list ?? []);
          if (!list?.length) this.error.set('No reports are available.');
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
    this.table.set(null);
  }

  execute(): void {
    const type = this.selectedType();
    if (!type) {
      this.error.set('No report selected.');
      return;
    }

    const parameters: { [key: string]: string } = {
      report: type.implementation ?? '',
      online: this.online ? 'true' : 'false',
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
    this.table.set(null);
    this.rpc
      .execute<SectioningReportRpcResponse>('SectioningReportRpcRequest', request)
      .subscribe({
        next: (res) => {
          const report = res?.report ?? [];
          const table = this.toTable(report);
          if (!table || (table.rows?.length ?? 0) === 0) {
            this.resultsMessage.set('The report produced no results.');
          } else {
            this.table.set(table);
            this.resultsMessage.set(`Showing ${table.rows!.length} line(s).`);
          }
          this.executing.set(false);
        },
        error: (e: ApiError) => {
          this.error.set(e.message);
          this.executing.set(false);
        },
      });
  }

  /**
   * Adapt the raw CSV-style String[][] (row 0 = header) into a TableInterface for
   * the shared RpcTable. Cells embed "\n" for multi-line content (rendered as <br>);
   * a leading "__" header marks a hidden id column. Columns whose data is entirely
   * numeric are right-aligned.
   */
  private toTable(report: string[][]): TableInterface | null {
    if (!report.length) return null;
    const head = report[0] ?? [];
    const data = report.slice(1).filter((r) => r.length > 0);

    const numeric = head.map((_, col) =>
      data.length > 0 &&
      data.every((r) => {
        const v = (r[col] ?? '').trim();
        return v === '' || this.isNumeric(v);
      }),
    );

    const header = head.map((h, col) => ({
      name: (h ?? '').replace(/_/g, ' ').trim().replace(/\n/g, '<br>'),
      visible: !(h ?? '').startsWith('__'),
      alignment: (numeric[col] ? 'RIGHT' : 'LEFT') as 'RIGHT' | 'LEFT',
    }));

    const rows = data.map((r) => ({
      cells: head.map((_, col) => ({
        formattedValue: (r[col] ?? '').replace(/\n/g, '<br>'),
      })),
    }));

    return { header, rows };
  }

  private isNumeric(v: string): boolean {
    if (v === '∞') return true; // infinity symbol
    return /^[-]?[0-9]+(,[0-9]{3})*(\.[0-9]+)? ?%?$/.test(v);
  }
}
