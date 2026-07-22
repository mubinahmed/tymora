import { Component, computed, input } from '@angular/core';
import { TableModule } from 'primeng/table';
import { MessageModule } from 'primeng/message';
import { TableInterface, TableRowInterface } from '../core/models';

interface Col {
  index: number;
  name: string;
  align: string;
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
  readonly table = input.required<TableInterface | null>();

  protected readonly cols = computed<Col[]>(() =>
    (this.table()?.header ?? [])
      .map((h, index) => ({ h, index }))
      .filter((c) => c.h.visible !== false)
      .map((c) => ({ index: c.index, name: c.h.name ?? '', align: this.align(c.h.alignment) })),
  );
  protected readonly rows = computed<TableRowInterface[]>(() => this.table()?.rows ?? []);

  private align(a?: string): string {
    const s = (a ?? '').toUpperCase();
    return s.includes('RIGHT') ? 'right' : s.includes('CENTER') ? 'center' : 'left';
  }
}
