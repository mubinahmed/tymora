import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';

/** Row of the Application Configuration listing (mirrors ApplicationConfigItem). */
interface ApplicationConfigItem {
  key?: string;
  value?: string;
  description?: string;
}

/** Response of ApplicationConfigListRequest (mirrors ApplicationConfigListResponse). */
interface ApplicationConfigListResponse {
  items?: ApplicationConfigItem[];
}

/**
 * Read-only listing of persisted application configuration settings served by
 * the new ApplicationConfigListBackend command bean. Angular migration of the
 * legacy applicationConfig.action list view. Editing settings is intentionally
 * not offered here (that remains on the legacy page until a verified editor is
 * ported).
 */
@Component({
  selector: 'app-application-config',
  imports: [TableModule, ButtonModule, InputTextModule, MessageModule, ProgressSpinnerModule],
  templateUrl: './application-config.html',
})
export class ApplicationConfig implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly items = signal<ApplicationConfigItem[]>([]);
  protected readonly filter = signal('');

  protected readonly rows = computed<ApplicationConfigItem[]>(() => {
    const all = this.items();
    const f = this.filter().trim().toLowerCase();
    if (!f) return all;
    return all.filter((r) =>
      [r.key, r.value, r.description].filter(Boolean).join(' ').toLowerCase().includes(f),
    );
  });

  ngOnInit(): void {
    this.page.set('Application Configuration');
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.rpc.execute<ApplicationConfigListResponse>('ApplicationConfigListRequest', {}).subscribe({
      next: (d) => {
        this.items.set(d.items ?? []);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }
}
