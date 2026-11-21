import { Component, ElementRef, effect, inject, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DrawerModule } from 'primeng/drawer';
import { TextareaModule } from 'primeng/textarea';
import { TooltipModule } from 'primeng/tooltip';
import { AssistantService } from '../../core/assistant.service';
import { renderMarkdown } from './markdown';

/**
 * The AI assistant chat panel (right-hand drawer, opened from the header). Shows Claude's replies, the tools it
 * ran, page buttons it suggests, and changes awaiting the user's confirmation.
 */
@Component({
  selector: 'app-assistant-panel',
  imports: [FormsModule, ButtonModule, DrawerModule, TextareaModule, TooltipModule],
  template: `
    <p-drawer [visible]="ai.open()" (visibleChange)="ai.open.set($event)" position="right" [modal]="false" styleClass="as-drawer" [showCloseIcon]="true">
      <ng-template pTemplate="header">
        <div class="as-head">
          <i class="pi pi-sparkles"></i>
          <div>
            <div class="as-title">Assistant</div>
            <div class="as-sub">@if (ai.status()?.enabled) { Claude · {{ ai.servedBy() ?? ai.status()?.model }}{{ ai.status()?.backend === 'claude-cli' ? ' · Claude Code' : '' }} } @else { not available }</div>
          </div>
        </div>
      </ng-template>
      <div class="as-body">
        <div class="as-log" #log (click)="onClick($event)">
          @if (ai.status() && !ai.status()!.enabled) {
            <div class="as-notice">{{ ai.status()!.unavailableReason }}</div>
          } @else if (!ai.entries().length) {
            <div class="as-intro">
              <p>Ask about timetables, examinations, relief cover or attendance. I look things up with your permissions, and I ask before changing anything.</p>
              @for (s of ai.status()?.suggestions ?? []; track s) {
                <button type="button" class="as-suggest" (click)="ai.send(s)">{{ s }}</button>
              }
            </div>
          }
          @for (e of ai.entries(); track $index) {
            @switch (e.role) {
              @case ('user') { <div class="as-msg user">{{ e.text }}</div> }
              @case ('assistant') { <div class="as-msg bot" [innerHTML]="md(e.text)"></div> }
              @case ('tool') {
                <div class="as-tool" [class.err]="e.error"><i [class]="e.error ? 'pi pi-exclamation-triangle' : 'pi pi-search'"></i> {{ e.detail ?? e.tool }}</div>
                @if (e.linkPath) { <p-button [label]="e.linkLabel ?? e.linkPath" icon="pi pi-arrow-right" iconPos="right" size="small" [outlined]="true" (onClick)="go(e.linkPath)" styleClass="as-link" /> }
              }
              @default { <div class="as-notice">{{ e.text }}</div> }
            }
          }
          @if (ai.pending().length) {
            <div class="as-confirm" role="group" aria-label="Confirm changes">
              <div class="as-confirm-title"><i class="pi pi-shield"></i> Confirm {{ ai.pending().length === 1 ? 'this change' : 'these changes' }}</div>
              @for (p of ai.pending(); track $index) { <div class="as-action">{{ p.title }}</div> }
              <div class="as-confirm-btns">
                <p-button label="Confirm" icon="pi pi-check" size="small" (onClick)="ai.confirm(true)" [loading]="ai.busy()" />
                <p-button label="Decline" icon="pi pi-times" size="small" severity="secondary" [outlined]="true" (onClick)="ai.confirm(false)" [disabled]="ai.busy()" />
              </div>
            </div>
          }
          @if (ai.busy()) { <div class="as-typing"><span></span><span></span><span></span> Working…</div> }
        </div>
        <div class="as-input">
          <textarea pTextarea [(ngModel)]="draft" rows="2" [autoResize]="true" maxlength="4000" placeholder="Ask the assistant…" aria-label="Message to the assistant"
            (keydown.enter)="onEnter($any($event))" [disabled]="!ai.status()?.enabled"></textarea>
          <div class="as-actions">
            <p-button icon="pi pi-refresh" [text]="true" size="small" severity="secondary" pTooltip="New conversation" (onClick)="ai.reset()" [disabled]="ai.busy()" ariaLabel="New conversation" />
            <p-button icon="pi pi-send" size="small" (onClick)="submit()" [disabled]="!draft.trim() || ai.busy() || !ai.status()?.enabled" ariaLabel="Send" />
          </div>
          <div class="as-foot">Answers come from Claude and can be wrong; check important figures on the page.</div>
        </div>
      </div>
    </p-drawer>
  `,
  styles: `
    :host ::ng-deep .as-drawer { width: min(30rem, 100vw); }
    :host ::ng-deep .as-drawer .p-drawer-content { padding: 0; display: flex; flex-direction: column; }
    .as-head { display: flex; align-items: center; gap: 0.6rem; i { font-size: 1.2rem; color: var(--ut-blue); } }
    .as-title { font-weight: 600; color: var(--ut-ink); } .as-sub { font-size: 0.75rem; color: var(--ut-muted); }
    .as-body { display: flex; flex-direction: column; height: 100%; min-height: 0; }
    .as-log { flex: 1; overflow-y: auto; padding: 0.8rem 1rem; display: flex; flex-direction: column; gap: 0.6rem; }
    .as-msg { padding: 0.55rem 0.8rem; border-radius: 12px; font-size: 0.88rem; line-height: 1.45; max-width: 92%; overflow-wrap: anywhere; }
    .as-msg.user { align-self: flex-end; background: var(--ut-blue); color: #fff; white-space: pre-wrap; border-bottom-right-radius: 4px; }
    .as-msg.bot { align-self: flex-start; background: var(--ut-surface); color: var(--ut-text); border: 1px solid var(--ut-border); border-bottom-left-radius: 4px; }
    .as-msg.bot ::ng-deep {
      p { margin: 0 0 0.45rem; } p:last-child { margin-bottom: 0; }
      ul, ol { margin: 0.2rem 0 0.45rem; padding-left: 1.2rem; } h4 { margin: 0.4rem 0 0.3rem; font-size: 0.9rem; color: var(--ut-ink); }
      code { font-family: var(--ut-mono); font-size: 0.8rem; background: var(--ut-card); padding: 0 0.25rem; border-radius: 4px; }
      table { border-collapse: collapse; font-size: 0.8rem; margin: 0.3rem 0; display: block; overflow-x: auto; max-width: 100%; }
      th, td { border-bottom: 1px solid var(--ut-border); padding: 0.2rem 0.45rem; text-align: left; white-space: nowrap; } th { color: var(--ut-muted); font-weight: 600; }
      a.app-link { color: var(--ut-blue); }
    }
    .as-tool { align-self: flex-start; font-size: 0.75rem; color: var(--ut-muted); display: inline-flex; gap: 0.35rem; align-items: center; i { font-size: 0.75rem; } }
    .as-tool.err { color: #c81e14; }
    :host ::ng-deep .as-link { align-self: flex-start; }
    .as-notice { align-self: stretch; font-size: 0.8rem; color: var(--ut-text); background: var(--ut-surface); border-left: 3px solid #d97706; padding: 0.45rem 0.6rem; border-radius: 4px; }
    .as-intro { color: var(--ut-text); font-size: 0.86rem; display: grid; gap: 0.45rem; p { margin: 0 0 0.3rem; } }
    .as-suggest { all: unset; cursor: pointer; border: 1px solid var(--ut-border-strong); border-radius: 10px; padding: 0.45rem 0.7rem; font-size: 0.82rem; color: var(--ut-ink); background: var(--ut-card); }
    .as-suggest:hover, .as-suggest:focus-visible { background: var(--ut-surface); }
    .as-confirm { border: 1px solid #d97706; border-radius: 10px; padding: 0.7rem 0.8rem; background: var(--ut-card); display: grid; gap: 0.4rem; }
    .as-confirm-title { font-weight: 600; font-size: 0.86rem; color: var(--ut-ink); display: flex; gap: 0.4rem; align-items: center; i { color: #d97706; } }
    .as-action { font-size: 0.84rem; color: var(--ut-text); padding-left: 1.4rem; }
    .as-confirm-btns { display: flex; gap: 0.5rem; padding-left: 1.4rem; margin-top: 0.2rem; }
    .as-typing { font-size: 0.78rem; color: var(--ut-muted); display: flex; align-items: center; gap: 0.25rem;
      span { width: 6px; height: 6px; border-radius: 50%; background: var(--ut-muted); animation: blink 1.2s infinite; }
      span:nth-child(2) { animation-delay: 0.2s; } span:nth-child(3) { animation-delay: 0.4s; margin-right: 0.3rem; } }
    @keyframes blink { 0%, 80%, 100% { opacity: 0.25; } 40% { opacity: 1; } }
    @media (prefers-reduced-motion: reduce) { .as-typing span { animation: none; } }
    .as-input { border-top: 1px solid var(--ut-border); padding: 0.6rem 0.8rem 0.5rem; display: grid; gap: 0.3rem;
      textarea { width: 100%; resize: none; font-size: 0.88rem; max-height: 9rem; } }
    .as-actions { display: flex; justify-content: space-between; align-items: center; }
    .as-foot { font-size: 0.7rem; color: var(--ut-muted); }
  `,
})
export class AssistantPanel {
  protected ai = inject(AssistantService);
  private router = inject(Router);
  private log = viewChild<ElementRef<HTMLDivElement>>('log');
  protected draft = '';

  constructor() {
    effect(() => {
      this.ai.entries(); this.ai.busy(); this.ai.pending();
      setTimeout(() => { const el = this.log()?.nativeElement; if (el) el.scrollTop = el.scrollHeight; });
    });
  }

  protected md(text?: string): string {
    return renderMarkdown(text ?? '');
  }

  protected onEnter(ev: KeyboardEvent): void {
    if (ev.shiftKey) return;
    ev.preventDefault();
    this.submit();
  }

  protected submit(): void {
    const t = this.draft;
    if (!t.trim() || this.ai.busy()) return;
    this.draft = '';
    this.ai.send(t);
  }

  protected go(path: string): void {
    this.router.navigateByUrl(path);
  }

  /** in-app links inside replies navigate in the app */
  protected onClick(ev: MouseEvent): void {
    const a = (ev.target as HTMLElement).closest('a.app-link') as HTMLAnchorElement | null;
    if (!a) return;
    ev.preventDefault();
    this.router.navigateByUrl(a.getAttribute('href') ?? '/');
  }
}
