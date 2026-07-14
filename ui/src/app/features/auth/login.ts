import { Component, OnInit, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { AuthService } from '../../core/auth.service';
import { PageService } from '../../core/page.service';

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
      this.submitting.set(false);
      if (ok) this.goBack();
      else this.failed.set(true);
    });
  }

  private goBack(): void {
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') || '/home';
    this.router.navigateByUrl(returnUrl);
  }
}
