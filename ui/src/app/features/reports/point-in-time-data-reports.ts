import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { MultiSelectModule } from 'primeng/multiselect';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { CardModule } from 'primeng/card';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  ApiError,
  IdValue,
  PITDExecuteRpcRequest,
  PITDParametersInterface,
  Parameter,
  Report,
  Table,
} from '../../core/models';

interface Col {
  /** original index into the Table row (String[]) */
  index: number;
  field: string;
  label: string;
}

/**
 * Point In Time Data Reports (command pattern). Loads the report catalog
 * (PITDQueriesRpcRequest -> Report[]) and the available parameters
 * (PITDParametersRpcRequest -> PITDParametersInterface). Selecting a report
 * reveals only the parameters its query references; Execute runs
 * PITDExecuteRpcRequest and renders the returned Table (row 0 = header,
 * remaining rows = data). The first column is hidden when its header starts
 * with "__" (it carries a linkable object id in the legacy page).
 *
 * Deferred vs. the GWT original: CSV export (EncodeQueryRpcRequest + export
 * servlet), print view, row drill-down links, and URL history/back state.
 */
@Component({
  selector: 'app-point-in-time-data-reports',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    MultiSelectModule,
    MessageModule,
    ProgressSpinnerModule,
    CardModule,
  ],
  templateUrl: './point-in-time-data-reports.html',
})
export class PointInTimeDataReports implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly loading = signal(true);
  protected readonly executing = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly resultsMessage = signal<string | null>(null);

  protected readonly reports = signal<Report[]>([]);
  protected readonly parameters = signal<Parameter[]>([]);
  protected readonly table = signal<Table | null>(null);

  /** id of the currently selected report (bound to the select) */
  protected reportId: string | null = null;

  /** parameter type -> current value(s); string for text/single, string[] for multi */
  protected paramValues: Record<string, string | string[]> = {};

  protected readonly reportOptions = computed(() =>
    this.reports().map((r) => ({ label: r.name ?? '', value: r.id ?? '' })),
  );

  protected readonly selectedReport = computed<Report | null>(
    () => this.reports().find((r) => r.id === this.reportId) ?? null,
  );

  protected readonly visibleParameters = computed<Parameter[]>(() => {
    const rpt = this.selectedReport();
    if (!rpt) return [];
    const types = new Set((rpt.parameters ?? []).map((p) => p.type));
    return this.parameters().filter((p) => types.has(p.type));
  });

  /** visible (non-hidden) columns derived from the Table header row */
  protected readonly cols = computed<Col[]>(() => {
    const data = this.table()?.data ?? [];
    if (!data.length) return [];
    const header = data[0];
    const hideFirst = (header[0] ?? '').startsWith('__');
    return header
      .map((h, index) => ({ index, field: String(index), label: (h ?? '').replace(/_/g, ' ').trim() }))
      .filter((c) => !(hideFirst && c.index === 0));
  });

  protected readonly rows = computed<string[][]>(() => (this.table()?.data ?? []).slice(1));

  ngOnInit(): void {
    this.page.set('Point In Time Data Reports');
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.rpc.execute<PITDParametersInterface>('PITDParametersRpcRequest', {}).subscribe({
      next: (res) => {
        const params = res.parameters ?? [];
        this.parameters.set(params);
        this.initDefaults(params);
        this.loadQueries();
      },
      error: (e: ApiError) => this.fail(e),
    });
  }

  private loadQueries(): void {
    this.rpc.execute<Report[]>('PITDQueriesRpcRequest', {}).subscribe({
      next: (list) => {
        this.reports.set(list ?? []);
        if (!list?.length) this.error.set('No reports are available.');
        this.loading.set(false);
      },
      error: (e: ApiError) => this.fail(e),
    });
  }

  /** Seed sensible defaults matching the legacy widget behaviour. */
  private initDefaults(params: Parameter[]): void {
    const values: Record<string, string | string[]> = {};
    for (const p of params) {
      if (!p.type) continue;
      if (p.textField) {
        values[p.type] = p.defaultTextValue ?? '';
      } else if (p.multiSelect) {
        // multi-select defaults to everything selected
        values[p.type] = (p.values ?? []).map((v) => v.value ?? '');
      } else {
        // single-select: auto-select when there is exactly one option
        const opts = p.values ?? [];
        values[p.type] = opts.length === 1 ? (opts[0].value ?? '') : '';
      }
    }
    this.paramValues = values;
  }

  onReportChange(): void {
    this.error.set(null);
    this.resultsMessage.set(null);
    this.table.set(null);
  }

  optionsFor(p: Parameter): { label: string; value: string }[] {
    return (p.values ?? []).map((v) => ({ label: v.text ?? '', value: v.value ?? '' }));
  }

  execute(): void {
    const rpt = this.selectedReport();
    if (!rpt) {
      this.error.set('No report selected.');
      return;
    }

    const parameters: IdValue[] = [];
    for (const p of this.visibleParameters()) {
      if (!p.type) continue;
      let value = '';
      if (p.textField) {
        value = (this.paramValues[p.type] as string) ?? '';
      } else if (p.multiSelect) {
        value = ((this.paramValues[p.type] as string[]) ?? []).join(',');
      } else {
        value = (this.paramValues[p.type] as string) ?? '';
      }
      if (!value) {
        this.error.set(`Please select ${p.name}.`);
        return;
      }
      // legacy contract: IdValue(value=type, text=selected value(s))
      parameters.push({ value: p.type, text: value });
    }

    const request: PITDExecuteRpcRequest = { report: rpt, parameters };
    this.executing.set(true);
    this.error.set(null);
    this.resultsMessage.set(null);
    this.table.set(null);
    this.rpc.execute<Table>('PITDExecuteRpcRequest', request).subscribe({
      next: (t) => {
        const size = t?.data?.length ?? 0;
        if (size <= 1) {
          this.resultsMessage.set('The report produced no results.');
        } else {
          this.table.set(t);
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

  cell(row: string[], index: number): string {
    const v = row[index];
    return v == null ? '' : v.replace(/\\n/g, '<br>');
  }

  private fail(e: ApiError): void {
    this.error.set(e.message);
    this.loading.set(false);
  }
}
