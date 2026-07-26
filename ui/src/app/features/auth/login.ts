import { Component, OnInit, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { AuthService } from '../../core/auth.service';
import { PageService } from '../../core/page.service';
import { RpcService } from '../../core/rpc.service';

/** Subset of SelectPrimaryRoleResponse used to decide the post-login landing. */
interface RoleList {
  authorities?: { id?: string; session?: string; initiative?: string; current?: boolean }[];
}

/**
 * Angular login screen. Posts to Spring Security's form-login endpoint via
 * AuthService.login and, on success, returns to the originally-requested route.
 * SSO deployments (CAS/OAuth2) would instead surface a "Sign in with…" link —
 * this covers the default / LDAP username-password configs.
 */
@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, CardModule, ButtonModule, InputTextModule, MessageModule],
  templateUrl: './login.html',
})
export class Login implements OnInit {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private page = inject(PageService);
  private rpc = inject(RpcService);

  protected readonly submitting = signal(false);
  protected readonly failed = signal(false);

  protected readonly form = this.fb.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  ngOnInit(): void {
    this.page.set('Sign In');
    // If already authenticated, don't sit on the login page.
    this.auth.ensureLoaded().subscribe(() => {
      if (this.auth.isAuthenticated()) this.goBack();
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { username, password } = this.form.getRawValue();
    this.submitting.set(true);
    this.failed.set(false);
    this.auth.login(username ?? '', password ?? '').subscribe((ok) => {
      // A session is only "selected" once a current authority is active. When one
      // is (ok), go straight to the dashboard. When none is, decide by how many
      // academic sessions the account spans:
      //   - exactly one  -> select it automatically (no picker), then enter the app;
      //   - more than one -> send them to the picker (the shell/menu stays hidden
      //     there until a session is chosen).
      if (ok) {
        this.submitting.set(false);
        this.goBack();
        return;
      }
      this.rpc
        .execute<RoleList>('SelectPrimaryRoleRequest', { operation: 'LOAD' })
        .subscribe({
          next: (r) => {
            const auths = r?.authorities ?? [];
            if (!auths.length) {
              this.submitting.set(false);
              this.failed.set(true);
              return;
            }
            const sessions = new Set(auths.map((a) => `${a.initiative ?? ''}|${a.session ?? ''}`)).size;
            if (sessions === 1) {
              const chosen = auths.find((a) => a.current) ?? auths[0];
              this.autoSelect(chosen.id);
            } else {
              this.submitting.set(false);
              const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
              this.router.navigate(['/select-role'], returnUrl ? { queryParams: { returnUrl } } : {});
            }
          },
          error: () => {
            this.submitting.set(false);
            this.failed.set(true);
          },
        });
    });
  }

  /**
   * Single-session accounts don't get a picker: make the (only) session's authority
   * current, then hard-reload into the app so the new identity applies everywhere.
   */
  private autoSelect(authorityId: string | undefined): void {
    if (!authorityId) {
      this.submitting.set(false);
      this.failed.set(true);
      return;
    }
    this.rpc
      .execute<RoleList>('SelectPrimaryRoleRequest', { operation: 'SELECT', authorityId })
      .subscribe({
        next: () => {
          const base = document.baseURI;
          const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
          const target = returnUrl ? new URL(returnUrl.replace(/^\//, ''), base).href : new URL('home', base).href;
          window.location.assign(target);
        },
        error: () => {
          this.submitting.set(false);
          this.failed.set(true);
        },
      });
  }

  private goBack(): void {
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') || '/home';
    this.router.navigateByUrl(returnUrl);
  }
}
