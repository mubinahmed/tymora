import { Component, computed, inject, input, output } from '@angular/core';
import { TableModule } from 'primeng/table';
import { MessageModule } from 'primeng/message';
import { TableInterface, TableRowInterface } from '../core/models';
import { ThemeService } from '../core/theme.service';
import { readableTextColor } from './contrast';
import { AnyTableCell, cellHtml } from './table-cell';

interface Col {
  index: number;
  name: string;
  align: string;
  /** header description from the server, shown as the column's tooltip */
  description: string;
}

/**
 * Generic renderer for the backend's TableInterface (header[] + rows[] of
 * positional cells). Reused by the solver reporting screens (assigned /
 * not-assigned classes, etc.), whose responses all extend TableInterface.
 * Cell formattedValue is rendered via Angular's sanitized [innerHTML] (UniTime
 * cells embed markup); colour/title/alignment from the cell/header are applied.
 */
@Component({
  selector: 'app-rpc-table',
  imports: [TableModule, MessageModule],
  templateUrl: './rpc-table.html',
})
export class RpcTable {
  private readonly theme = inject(ThemeService);
  /** Server-sent cell text colour, adjusted for readable contrast on the current theme. */
  protected cellColor(color: string | null | undefined): string {
    return readableTextColor(color, this.theme.isDark());
  }

  /** Cell display HTML: formattedValue, or the value / rooms / items / chunks the server sent instead. */
  protected html(cell: unknown): string {
    return cellHtml(cell as AnyTableCell);
  }

  readonly table = input.required<TableInterface | null>();
  /** when set, rows with an id are clickable and emit rowClick (e.g. open a dialog) */
  readonly clickable = input(false);
  readonly rowClick = output<TableRowInterface>();

  protected readonly cols = computed<Col[]>(() =>
    (this.table()?.header ?? [])
      .map((h, index) => ({ h, index }))
      .filter((c) => c.h.visible !== false)
      .map((c) => ({
        index: c.index,
        name: c.h.name ?? '',
        align: this.align(c.h.alignment),
        description: c.h.description ?? '',
      })),
  );
  protected readonly rows = computed<TableRowInterface[]>(() => this.table()?.rows ?? []);

  protected onRow(row: TableRowInterface): void {
    if (this.clickable() && row.id != null) this.rowClick.emit(row);
  }

  private align(a?: string): string {
    const s = (a ?? '').toUpperCase();
    return s.includes('RIGHT') ? 'right' : s.includes('CENTER') ? 'center' : 'left';
  }
}
