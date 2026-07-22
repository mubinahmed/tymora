import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TreeModule } from 'primeng/tree';
import { SelectButtonModule } from 'primeng/selectbutton';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { TreeNode } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError, CBSNode, ConflictBasedStatisticsRequest } from '../../core/models';

/**
 * Conflict-Based Statistics (command pattern). The backend returns a recursive
 * CBSNode tree (GwtRpcResponseList<CBSNode>); this maps it to PrimeNG tree nodes.
 * A toggle switches between constraint- and variable-oriented views.
 * Cell HTML labels are shown as text (fidelity gap).
 */
@Component({
  selector: 'app-cbs',
  imports: [FormsModule, TreeModule, SelectButtonModule, ButtonModule, MessageModule, ProgressSpinnerModule],
  templateUrl: './cbs.html',
})
export class Cbs implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly orientations = [
    { label: 'By Constraint', value: false },
    { label: 'By Variable', value: true },
  ];
  protected variableOriented = false;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly tree = signal<TreeNode[]>([]);

  ngOnInit(): void {
    this.page.set('Conflict-based Statistics');
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    const request: ConflictBasedStatisticsRequest = { variableOriented: this.variableOriented, limit: 100 };
    this.rpc.execute<CBSNode[]>('ConflictBasedStatisticsRequest', request).subscribe({
      next: (nodes) => {
        this.tree.set(this.toTree(nodes ?? []));
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  private toTree(nodes: CBSNode[]): TreeNode[] {
    return nodes.map((n) => {
      // Keep the backend's HTML label; rendered via [innerHTML] in the tree template.
      const raw = (n.hTML ?? n.name ?? '').trim();
      return {
        label: n.count != null ? `[${n.count}] ${raw}` : raw,
        children: n.nodes?.length ? this.toTree(n.nodes) : undefined,
        leaf: !n.nodes?.length,
      } as TreeNode;
    });
  }
}
