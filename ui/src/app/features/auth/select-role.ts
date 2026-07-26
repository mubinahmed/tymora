import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';

type Operation = 'LOAD' | 'SELECT';

interface AuthorityInfo {
  id?: string;
  role?: string;
  session?: string;
  initiative?: string;
  status?: string;
  current?: boolean;
}

interface SelectPrimaryRoleRequest {
  operation: Operation;
  authorityId?: string;
}

interface SelectPrimaryRoleResponse {
  name?: string;
  currentId?: string;
  authorities?: AuthorityInfo[];
}

/**
 * Change Role — Angular replacement for the legacy selectPrimaryRole.action. Lists
 * the signed-in user's authorities (role × academic session) and, on click, makes
 * one the active authority (SelectPrimaryRoleRequest SELECT →
 * user.setCurrentAuthority). After switching, the whole app is reloaded so the new
 * role applies everywhere (menu, permissions, header) — same approach as the
 * masquerade screen. Reachable from the user chip's "Change Role" and after a login
 * where the account has several roles and no default.
 */
@Component({
  selector: 'app-select-role',
  imports: [TableModule, ButtonModule, MessageModule, ProgressSpinnerModule],
  templateUrl: './select-role.html',
})
export class SelectRole implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly selectingId = signal<string | null>(null);
  protected readonly data = signal<SelectPrimaryRoleResponse | null>(null);

  protected readonly authorities = computed<AuthorityInfo[]>(() => this.data()?.authorities ?? []);
  protected readonly multiRole = computed(() => new Set(this.authorities().map((a) => a.role)).size > 1);
  /** A session is already active (reached via "Change Role", not a forced login). */
  protected readonly hasCurrent = computed(() => this.data()?.currentId != null);

  ngOnInit(): void {
    this.page.set('Change Role');
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.rpc.execute<SelectPrimaryRoleResponse>('SelectPrimaryRoleRequest', { operation: 'LOAD' }).subscribe({
      next: (d) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        if (e.status === 401 || e.status === 403) this.goSignin();
        else {
          this.error.set(e.message);
          this.loading.set(false);
        }
      },
    });
  }

  select(a: AuthorityInfo): void {
    if (!a.id || this.selectingId()) return;
    this.selectingId.set(a.id);
    this.error.set(null);
    const request: SelectPrimaryRoleRequest = { operation: 'SELECT', authorityId: a.id };
    this.rpc.execute<SelectPrimaryRoleResponse>('SelectPrimaryRoleRequest', request).subscribe({
      next: () => this.finish(),
      error: (e: ApiError) => {
        this.selectingId.set(null);
        this.error.set(e.message);
      },
    });
  }

  /** Full reload so the new role applies app-wide (mirrors the masquerade screen). */
  private finish(): void {
    const base = document.baseURI;
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    const target = returnUrl ? new URL(returnUrl.replace(/^\//, ''), base).href : new URL('home', base).href;
    window.location.assign(target);
  }

  goSignin(): void {
    window.location.assign(new URL('signin', document.baseURI).href);
  }

  /** Back out to the dashboard, keeping the currently-active session (Change Role). */
  cancel(): void {
    this.router.navigateByUrl('/home');
  }
}
