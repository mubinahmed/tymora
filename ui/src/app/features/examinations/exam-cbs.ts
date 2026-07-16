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
import { ApiError } from '../../core/models';

/**
 * Examination Conflict-Based Statistics (migrates legacy ecbs.action).
 * READ-ONLY: the backend reads the in-memory examination solver and returns a
 * recursive CBSNode tree (GwtRpcResponseList<CBSNode>). A toggle switches
 * between constraint- and variable-oriented views. When no exam solver is
 * loaded the backend returns an empty list, so an info banner is shown.
 * HTML labels are rendered via [innerHTML] (colored preferences).
 */

// Inline mirror of gwt/shared SuggestionsInterface.CBSNode (subset used here).
interface CBSNode {
  count?: number;
  name?: string;
  hTML?: string;
  pref?: string;
  link?: string;
  classId?: number;
  nodes?: CBSNode[];
}

interface ExamCbsRequest {
  variableOriented: boolean;
  limit: number;
}

@Component({
  selector: 'app-exam-cbs',
  imports: [FormsModule, TreeModule, SelectButtonModule, ButtonModule, MessageModule, ProgressSpinnerModule],
  templateUrl: './exam-cbs.html',
})
export class ExamCbs implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly orientations = [
    { label: 'By Constraint', value: false },
    { label: 'By Exam', value: true },
  ];
  protected variableOriented = false;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly tree = signal<TreeNode[]>([]);

  ngOnInit(): void {
    this.page.set('Examination Conflict-based Statistics');
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    const request: ExamCbsRequest = { variableOriented: this.variableOriented, limit: 100 };
    this.rpc.execute<CBSNode[]>('ExamCbsRequest', request).subscribe({
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
      const raw = (n.hTML ?? n.name ?? '').trim();
      return {
        label: n.count != null ? `[${n.count}] ${raw}` : raw,
        children: n.nodes?.length ? this.toTree(n.nodes) : undefined,
        leaf: !n.nodes?.length,
      } as TreeNode;
    });
  }
}
