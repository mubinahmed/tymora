import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { SelectModule } from 'primeng/select';
import { MultiSelectModule } from 'primeng/multiselect';
import { CheckboxModule } from 'primeng/checkbox';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { CardModule } from 'primeng/card';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  ApiError,
  HQLExecuteRpcRequest,
  HQLOptionsInterface,
  HQLQueriesRpcRequest,
  Option,
  Query,
  SavedHQLInterface_IdValue,
  SavedHQLInterface_Parameter,
  SavedHQLInterface_Table,
} from '../../core/models';

interface Col {
  /** original index into the Table row (String[]) */
  index: number;
  field: string;
  label: string;
}

const PAGE_SIZE = 100;

/**
 * HQL / Saved Reports (command pattern). Mirrors the legacy SavedHQLPage.
 *
 * Flow: HQLOptionsRpcRequest loads the reusable filter "options" (Subject Area,
 * Department, ... — the %TYPE% placeholders a query may reference) plus flags/
 * editable. HQLQueriesRpcRequest(appearance) loads the report catalog for the
 * chosen appearance (courses / exams / sectioning / events / administration).
 * Selecting a report reveals only the option filters its query references and
 * its own parameters; Execute runs HQLExecuteRpcRequest and renders the returned
 * Table (row 0 = header, remaining rows = data). The first column is hidden when
 * its header starts with "__" (it carries a linkable object id in the legacy UI).
 *
 * The legacy contract packs both option filters and query parameters into a
 * single IdValue list where value = the option/parameter key and text = the
 * selected value(s) (comma-joined for multi-select).
 *
 * Deferred vs. the GWT original: create/edit/delete report dialog (HQLStore/
 * HQLDelete), CSV/XLS export + print (EncodeQueryRpcRequest + export servlet),
 * row drill-down links, column sorting, student selection, and URL/history back
 * state. Result paging (from/max rows) is supported via Previous/Next.
 */
@Component({
  selector: 'app-hql-reports',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    TextareaModule,
    SelectModule,
    MultiSelectModule,
    CheckboxModule,
    MessageModule,
    ProgressSpinnerModule,
    CardModule,
  ],
  templateUrl: './hql-reports.html',
})
export class HqlReports implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly appearances = [
    { label: 'Course Reports', value: 'courses' },
    { label: 'Examination Reports', value: 'exams' },
    { label: 'Student Sectioning Reports', value: 'sectioning' },
    { label: 'Event Reports', value: 'events' },
    { label: 'Administration Reports', value: 'administration' },
  ];
  protected appearance = 'courses';

  protected readonly loading = signal(true);
  protected readonly executing = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly resultsMessage = signal<string | null>(null);

  protected readonly hqlOptions = signal<Option[]>([]);
  protected readonly queries = signal<Query[]>([]);
  protected readonly table = signal<SavedHQLInterface_Table | null>(null);

  /** id of the currently selected report (bound to the select) */
  protected reportId: number | null = null;

  /** page offset of the currently displayed result window */
  protected fromRow = 0;

  /** option-filter key -> current value(s); string (single) | string[] (multi) */
  protected optionValues: Record<string, string | string[]> = {};
  /** parameter name -> current value(s); string | string[] | boolean (checkbox) */
  protected paramValues: Record<string, string | string[] | boolean> = {};

  protected readonly reportOptions = computed(() =>
    this.queries().map((q) => ({ label: q.name ?? '', value: q.id ?? 0 })),
  );

  protected readonly selectedQuery = computed<Query | null>(
    () => this.queries().find((q) => q.id === this.reportId) ?? null,
  );

  /** Option filters whose %TYPE% placeholder appears in the selected query. */
  protected readonly visibleOptions = computed<Option[]>(() => {
    const q = this.selectedQuery();
    if (!q?.query) return [];
    return this.hqlOptions().filter((o) => !!o.type && q.query!.includes('%' + o.type + '%'));
  });

  protected readonly queryParams = computed<SavedHQLInterface_Parameter[]>(
    () => this.selectedQuery()?.parameters ?? [],
  );

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

  protected readonly hasPrev = computed(() => this.fromRow > 0);
  protected readonly hasNext = computed(() => this.rows().length >= PAGE_SIZE);

  ngOnInit(): void {
    this.page.set('HQL Reports');
    this.loadOptions();
  }

  private loadOptions(): void {
    this.loading.set(true);
    this.error.set(null);
    this.rpc.execute<HQLOptionsInterface>('HQLOptionsRpcRequest', {}).subscribe({
      next: (res) => {
        this.hqlOptions.set(res.options ?? []);
        this.loadQueries();
      },
      error: (e: ApiError) => this.fail(e),
    });
  }

  private loadQueries(): void {
    this.loading.set(true);
    this.error.set(null);
    this.reportId = null;
    this.table.set(null);
    this.resultsMessage.set(null);
    const request: HQLQueriesRpcRequest = { appearance: this.appearance };
    this.rpc.execute<Query[]>('HQLQueriesRpcRequest', request).subscribe({
      next: (list) => {
        this.queries.set(list ?? []);
        if (!list?.length) this.error.set('No reports are available for this appearance.');
        this.loading.set(false);
      },
      error: (e: ApiError) => this.fail(e),
    });
  }

  onAppearanceChange(): void {
    this.loadQueries();
  }

  onReportChange(): void {
    this.error.set(null);
    this.resultsMessage.set(null);
    this.table.set(null);
    this.fromRow = 0;
    this.seedDefaults();
  }

  /** Seed option/parameter inputs to their default (legacy) values. */
  private seedDefaults(): void {
    const opt: Record<string, string | string[]> = {};
    for (const o of this.visibleOptions()) {
      if (!o.type) continue;
      opt[o.type] = o.multiSelect ? (o.values ?? []).map((v) => v.value ?? '') : '';
    }
    this.optionValues = opt;

    const par: Record<string, string | string[] | boolean> = {};
    for (const p of this.queryParams()) {
      if (!p.name) continue;
      const def = p.default ?? '';
      if (this.isBoolean(p)) {
        par[p.name] = def.toLowerCase() === 'true';
      } else if (this.hasOptions(p) && p.multiSelect) {
        par[p.name] = def ? def.split(',').filter(Boolean) : [];
      } else {
        par[p.name] = def;
      }
    }
    this.paramValues = par;
  }

  hasOptions(p: SavedHQLInterface_Parameter): boolean {
    return !!p.options && p.options.length > 0;
  }

  isBoolean(p: SavedHQLInterface_Parameter): boolean {
    return (p.type ?? '').toLowerCase() === 'boolean';
  }

  isTextarea(p: SavedHQLInterface_Parameter): boolean {
    return (p.type ?? '').toLowerCase() === 'textarea';
  }

  paramLabel(p: SavedHQLInterface_Parameter): string {
    return p.label && p.label.trim() ? p.label : (p.name ?? '');
  }

  optionItems(o: Option): { label: string; value: string }[] {
    return (o.values ?? []).map((v) => ({ label: v.text ?? '', value: v.value ?? '' }));
  }

  paramItems(p: SavedHQLInterface_Parameter): { label: string; value: string }[] {
    return (p.options ?? []).map((v) => ({ label: v.text ?? '', value: v.value ?? '' }));
  }

  /** Collect option filters + parameters into the legacy IdValue list. */
  private buildOptions(): SavedHQLInterface_IdValue[] | null {
    const out: SavedHQLInterface_IdValue[] = [];

    for (const o of this.visibleOptions()) {
      if (!o.type) continue;
      const raw = this.optionValues[o.type];
      const value = Array.isArray(raw) ? raw.join(',') : (raw ?? '');
      if (!value) {
        this.error.set(`Please select ${o.name}.`);
        return null;
      }
      out.push({ value: o.type, text: value });
    }

    for (const p of this.queryParams()) {
      if (!p.name) continue;
      const raw = this.paramValues[p.name];
      let value = '';
      if (this.isBoolean(p)) {
        value = raw === true || raw === 'true' ? 'true' : 'false';
      } else if (Array.isArray(raw)) {
        value = raw.join(',');
      } else {
        value = typeof raw === 'string' ? raw : '';
      }
      if (value === '') continue; // optional; backend falls back to the default
      out.push({ value: p.name, text: value });
    }

    return out;
  }

  execute(reset = true): void {
    const q = this.selectedQuery();
    if (!q) {
      this.error.set('No report selected.');
      return;
    }
    if (reset) this.fromRow = 0;

    const options = this.buildOptions();
    if (options === null) return;

    const request: HQLExecuteRpcRequest = {
      query: q,
      options,
      fromRow: this.fromRow,
      maxRows: PAGE_SIZE,
    };
    this.executing.set(true);
    this.error.set(null);
    this.resultsMessage.set(null);
    this.rpc.execute<SavedHQLInterface_Table>('HQLExecuteRpcRequest', request).subscribe({
      next: (t) => {
        const size = t?.data?.length ?? 0;
        if (size <= 1) {
          this.table.set(null);
          this.resultsMessage.set(
            this.fromRow > 0 ? 'No more results.' : 'The report produced no results.',
          );
        } else {
          this.table.set(t);
          const first = this.fromRow + 1;
          const last = this.fromRow + (size - 1);
          this.resultsMessage.set(`Showing line(s) ${first} - ${last}.`);
        }
        this.executing.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.executing.set(false);
      },
    });
  }

  previous(): void {
    if (!this.hasPrev()) return;
    this.fromRow = Math.max(0, this.fromRow - PAGE_SIZE);
    this.execute(false);
  }

  next(): void {
    if (!this.hasNext()) return;
    this.fromRow += PAGE_SIZE;
    this.execute(false);
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
