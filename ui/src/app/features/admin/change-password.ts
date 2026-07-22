import { Component, computed, inject, input, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError, PasswordChangeRequest, PasswordChangeResponse } from '../../core/models';

/**
 * Change / Reset Password — port of the legacy GWT PasswordPage
 * (org.unitime.timetable.gwt.client.admin.PasswordPage), backed by the
 * PasswordChangeRequest command bean (PasswordChangeBackend).
 *
 * Two modes, selected by query params (bound via withComponentInputBinding):
 *  - reset=1            -> ask for e-mail, send a reset link (reset password).
 *  - default            -> change the current user's password. If a `user`
 *                          query param is present the username is editable,
 *                          and a `key` param pre-fills the old password
 *                          (the temp key from the reset e-mail link).
 *
 * The backend returns an empty PasswordChangeResponse on success and throws a
 * GwtRpcException (surfaced as an ApiError message) on any validation failure.
 */
@Component({
  selector: 'app-change-password',
  imports: [ReactiveFormsModule, CardModule, ButtonModule, InputTextModule, MessageModule],
  templateUrl: './change-password.html',
})
export class ChangePassword {
  private fb = inject(FormBuilder);
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);
  private router = inject(Router);

  /** Query params (component input binding). */
  readonly reset = input<string | undefined>(undefined);
  readonly user = input<string | undefined>(undefined);
  readonly key = input<string | undefined>(undefined);

  protected readonly isReset = computed(() => this.reset() === '1');
  protected readonly hasUser = computed(() => this.user() != null);

  protected readonly saving = signal(false);
  protected readonly done = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly resetForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
  });

  protected readonly changeForm = this.fb.group({
    username: [''],
    oldPassword: ['', [Validators.required]],
    newPassword: ['', [Validators.required]],
    retypePassword: ['', [Validators.required]],
  });

  private initialized = false;

  constructor() {
    // Seed the page title and pre-fill values from the reset e-mail link once
    // the input bindings have resolved.
    queueMicrotask(() => this.init());
  }

  private init(): void {
    if (this.initialized) return;
    this.initialized = true;
    this.page.set(this.isReset() ? 'Reset Password' : 'Change Password');
    if (this.hasUser()) this.changeForm.patchValue({ username: this.user() ?? '' });
    if (this.key()) this.changeForm.patchValue({ oldPassword: this.key() ?? '' });
  }

  submitReset(): void {
    this.error.set(null);
    if (this.resetForm.invalid) {
      this.resetForm.markAllAsTouched();
      this.error.set('Please enter a valid e-mail address.');
      return;
    }
    const request: PasswordChangeRequest = {
      email: this.resetForm.getRawValue().email ?? '',
      reset: true,
    };
    this.saving.set(true);
    this.rpc.execute<PasswordChangeResponse>('PasswordChangeRequest', request).subscribe({
      next: () => {
        this.saving.set(false);
        this.done.set(true);
        this.messages.add({
          severity: 'success',
          summary: 'Reset e-mail sent',
          detail: 'Check your inbox for a link to reset your password.',
        });
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.error.set(e.message);
      },
    });
  }

  submitChange(): void {
    this.error.set(null);
    const v = this.changeForm.getRawValue();
    if (!v.oldPassword) {
      this.error.set('Please enter your current password.');
      return;
    }
    if (!v.newPassword) {
      this.error.set('Please enter a new password.');
      return;
    }
    if (v.newPassword !== v.retypePassword) {
      this.error.set('The new passwords do not match.');
      return;
    }
    const request: PasswordChangeRequest = {
      username: this.hasUser() ? v.username || undefined : undefined,
      oldPassword: v.oldPassword,
      newPassword: v.newPassword,
    };
    this.saving.set(true);
    this.rpc.execute<PasswordChangeResponse>('PasswordChangeRequest', request).subscribe({
      next: () => {
        this.saving.set(false);
        this.done.set(true);
        this.changeForm.reset({ username: this.hasUser() ? v.username ?? '' : '' });
        this.messages.add({ severity: 'success', summary: 'Password changed' });
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.error.set(e.message);
      },
    });
  }

  back(): void {
    this.router.navigate(['/home']);
  }
}
