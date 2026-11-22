import { Component, computed, forwardRef, input, output, signal } from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR } from '@angular/forms';
import { DatePickerModule } from 'primeng/datepicker';

/**
 * Date field shown as dd/mm/yyyy everywhere, whatever the browser's locale (a native
 * <input type="date"> follows the browser locale and cannot be forced to dd/MM/yyyy).
 *
 * The VALUE stays an ISO date string "yyyy-MM-dd" (or null), so callers and back-ends that already
 * work with ISO dates are unchanged. Works with [(ngModel)] and formControlName.
 * `changed` fires after the bound value has been updated (use it instead of a native (change)).
 */
@Component({
  selector: 'app-date-input',
  imports: [FormsModule, DatePickerModule],
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => DateInput), multi: true }],
  template: `
    <p-datepicker
      [ngModel]="date()"
      (ngModelChange)="onPick($event)"
      dateFormat="dd/mm/yy"
      placeholder="dd/mm/yyyy"
      [showIcon]="showIcon()"
      [showButtonBar]="true"
      [minDate]="minDate()"
      [maxDate]="maxDate()"
      [disabled]="isDisabled()"
      [inputId]="inputId()"
      [styleClass]="styleClass()"
      appendTo="body"
      (onBlur)="touched()"
    />
  `,
})
export class DateInput implements ControlValueAccessor {
  readonly min = input<string | null | undefined>(null);
  readonly max = input<string | null | undefined>(null);
  readonly inputId = input<string | undefined>(undefined);
  readonly styleClass = input<string | undefined>(undefined);
  readonly showIcon = input<boolean>(true);
  readonly disabled = input<boolean>(false);
  /** Emitted with the new ISO value, after the bound model has been updated. */
  readonly changed = output<string | null>();

  protected readonly date = signal<Date | null>(null);
  private readonly cvaDisabled = signal(false);
  protected readonly isDisabled = computed(() => this.disabled() || this.cvaDisabled());
  protected readonly minDate = computed(() => fromIso(this.min()));
  protected readonly maxDate = computed(() => fromIso(this.max()));

  private onChange: (v: string | null) => void = () => {};
  private onTouched: () => void = () => {};

  writeValue(value: string | null | undefined): void {
    this.date.set(fromIso(value));
  }
  registerOnChange(fn: (v: string | null) => void): void {
    this.onChange = fn;
  }
  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }
  setDisabledState(disabled: boolean): void {
    this.cvaDisabled.set(disabled);
  }

  protected onPick(d: Date | null): void {
    this.date.set(d);
    const iso = toIso(d);
    this.onChange(iso);
    this.changed.emit(iso);
  }

  protected touched(): void {
    this.onTouched();
  }
}

/** "yyyy-MM-dd" (time part ignored) -> local Date at midnight, or null. */
export function fromIso(value: string | null | undefined): Date | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(value ?? '');
  return m ? new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3])) : null;
}

/** Local Date -> "yyyy-MM-dd", or null. */
export function toIso(d: Date | null | undefined): string | null {
  if (!d || isNaN(d.getTime())) return null;
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
