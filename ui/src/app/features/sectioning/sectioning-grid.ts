import { Component, computed, input } from '@angular/core';

/** One meeting to place on the weekly grid. days = 0(Mon)..6(Sun); start/length in 5-min slots. */
export interface GridItem {
  name: string;
  detail?: string;
  days: number[];
  start: number;
  length: number;
  breakTime?: number;
  free?: boolean;
  colorIndex?: number;
}

interface Block {
  key: string;
  name: string;
  detail: string;
  time: string;
  top: number;
  height: number;
  leftPct: number;
  widthPct: number;
  colorIndex: number;
}
interface DayColumn {
  index: number;
  name: string;
  blocks: Block[];
}
interface Layout {
  empty: boolean;
  startHour: number;
  endHour: number;
  height: number;
  days: DayColumn[];
  hours: { label: string; top: number }[];
}

const DAY_NAMES = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const PX_PER_HOUR = 46;
const PX_PER_SLOT = PX_PER_HOUR / 12; // 12 five-minute slots per hour

/**
 * Compact weekly "representative week" grid for a proposed/current schedule.
 * Each GridItem is expanded across its day set into positioned blocks.
 * Overlapping blocks within a day share the column width. 5-minute slots
 * (minute = 5 * slot); dayOfWeek 0 = Monday.
 */
@Component({
  selector: 'app-sectioning-grid',
  imports: [],
  template: `
    @if (layout().empty) {
      <div class="sg-empty">No timetabled meetings to display.</div>
    } @else {
      <div class="sg-grid" [style.height.px]="layout().height + 24">
        <div class="sg-times">
          @for (h of layout().hours; track h.top) {
            <div class="sg-hour" [style.top.px]="h.top">{{ h.label }}</div>
          }
        </div>
        <div class="sg-days">
          @for (d of layout().days; track d.index) {
            <div class="sg-day">
              <div class="sg-day-head">{{ d.name }}</div>
              <div class="sg-day-body" [style.height.px]="layout().height">
                @for (h of layout().hours; track h.top) {
                  <div class="sg-line" [style.top.px]="h.top"></div>
                }
                @for (b of d.blocks; track b.key) {
                  <div
                    class="sg-block"
                    [class.sg-free]="b.colorIndex < 0"
                    [style.top.px]="b.top"
                    [style.height.px]="b.height"
                    [style.left.%]="b.leftPct"
                    [style.width.%]="b.widthPct"
                    [attr.data-c]="b.colorIndex"
                    [title]="b.name + ' ' + b.time + (b.detail ? ' ' + b.detail : '')"
                  >
                    <div class="sg-block-name">{{ b.name }}</div>
                    <div class="sg-block-time">{{ b.time }}</div>
                    @if (b.detail) {
                      <div class="sg-block-detail">{{ b.detail }}</div>
                    }
                  </div>
                }
              </div>
            </div>
          }
        </div>
      </div>
    }
  `,
  styles: [
    `
      .sg-empty {
        padding: 1rem;
        color: var(--p-text-muted-color, #888);
        font-style: italic;
      }
      .sg-grid {
        display: flex;
        overflow-x: auto;
        border: 1px solid var(--p-content-border-color, #ddd);
        border-radius: 6px;
        font-size: 0.72rem;
      }
      .sg-times {
        position: relative;
        width: 46px;
        flex: 0 0 46px;
        margin-top: 22px;
      }
      .sg-hour {
        position: absolute;
        right: 4px;
        transform: translateY(-50%);
        color: var(--p-text-muted-color, #888);
        white-space: nowrap;
      }
      .sg-days {
        display: flex;
        flex: 1 1 auto;
        min-width: 640px;
      }
      .sg-day {
        flex: 1 1 0;
        border-left: 1px solid var(--p-content-border-color, #eee);
      }
      .sg-day-head {
        height: 22px;
        text-align: center;
        font-weight: 600;
        line-height: 22px;
      }
      .sg-day-body {
        position: relative;
      }
      .sg-line {
        position: absolute;
        left: 0;
        right: 0;
        border-top: 1px dashed var(--p-content-border-color, #eee);
      }
      .sg-block {
        position: absolute;
        box-sizing: border-box;
        overflow: hidden;
        border-radius: 4px;
        padding: 1px 3px;
        color: #fff;
        background: #3b82f6;
        border: 1px solid rgba(0, 0, 0, 0.15);
        line-height: 1.05;
      }
      .sg-block[data-c='1'] { background: #16a34a; }
      .sg-block[data-c='2'] { background: #db2777; }
      .sg-block[data-c='3'] { background: #d97706; }
      .sg-block[data-c='4'] { background: #7c3aed; }
      .sg-block[data-c='5'] { background: #0891b2; }
      .sg-block[data-c='6'] { background: #dc2626; }
      .sg-block[data-c='7'] { background: #4b5563; }
      .sg-free {
        background: repeating-linear-gradient(45deg, #9ca3af, #9ca3af 6px, #6b7280 6px, #6b7280 12px) !important;
      }
      .sg-block-name { font-weight: 600; }
      .sg-block-detail { opacity: 0.9; }
    `,
  ],
})
export class SectioningGrid {
  readonly items = input.required<GridItem[]>();

  protected readonly layout = computed<Layout>(() => this.build(this.items()));

  private build(items: GridItem[]): Layout {
    const usable = (items ?? []).filter((i) => i.days?.length && i.start != null && (i.length ?? 0) > 0);
    if (!usable.length) {
      return { empty: true, startHour: 7, endHour: 18, height: 0, days: [], hours: [] };
    }
    let minSlot = Infinity;
    let maxSlot = -Infinity;
    for (const i of usable) {
      minSlot = Math.min(minSlot, i.start);
      maxSlot = Math.max(maxSlot, i.start + i.length);
    }
    let startHour = Math.max(0, Math.floor(minSlot / 12));
    let endHour = Math.min(24, Math.ceil(maxSlot / 12));
    if (endHour - startHour < 4) endHour = Math.min(24, startHour + 4);
    const baseSlot = startHour * 12;
    const height = (endHour - startHour) * PX_PER_HOUR;

    const cols: DayColumn[] = [];
    let anyDay = false;
    for (let d = 0; d < 7; d++) {
      const dayItems = usable.filter((i) => i.days.includes(d));
      if (d >= 5 && dayItems.length === 0) continue; // hide Sat/Sun when empty
      anyDay ||= dayItems.length > 0;
      const raw: Block[] = dayItems.map((i, idx) => {
        const top = (i.start - baseSlot) * PX_PER_SLOT;
        const h = Math.max(14, i.length * PX_PER_SLOT - 1);
        return {
          key: `${d}-${idx}-${i.name}-${i.start}`,
          name: i.name,
          detail: i.detail ?? '',
          time: this.timeRange(i.start, i.length, i.breakTime ?? 0),
          top,
          height: h,
          leftPct: 0,
          widthPct: 100,
          colorIndex: i.free ? -1 : (i.colorIndex ?? 0),
        };
      });
      this.layoutOverlaps(raw);
      cols.push({ index: d, name: DAY_NAMES[d], blocks: raw });
    }

    const hours: { label: string; top: number }[] = [];
    for (let h = startHour; h <= endHour; h++) {
      hours.push({ label: this.hourLabel(h), top: (h - startHour) * PX_PER_HOUR });
    }
    return { empty: !anyDay, startHour, endHour, height, days: cols, hours };
  }

  /** Assign side-by-side columns to overlapping blocks (simple greedy packing). */
  private layoutOverlaps(blocks: Block[]): void {
    const sorted = [...blocks].sort((a, b) => a.top - b.top);
    const ends: number[] = [];
    const colOf = new Map<Block, number>();
    for (const b of sorted) {
      let col = 0;
      while (col < ends.length && ends[col] > b.top + 0.5) col++;
      colOf.set(b, col);
      ends[col] = b.top + b.height;
    }
    // Determine max concurrency per cluster; simple: use total columns used.
    const total = Math.max(1, ends.length);
    for (const b of blocks) {
      const col = colOf.get(b) ?? 0;
      b.widthPct = 100 / total;
      b.leftPct = (100 / total) * col;
    }
  }

  private timeRange(start: number, length: number, breakTime: number): string {
    const s = this.slotTime(start);
    const endMin = 5 * (start + length) - breakTime;
    const e = this.minTime(endMin);
    return `${s}-${e}`;
  }
  private slotTime(slot: number): string {
    return this.minTime(5 * slot);
  }
  private minTime(total: number): string {
    const h = Math.floor(total / 60);
    const m = total % 60;
    const ampm = h >= 12 && h < 24 ? 'p' : 'a';
    const hh = h > 12 ? h - 12 : h === 0 ? 12 : h;
    return `${hh}:${m < 10 ? '0' : ''}${m}${ampm}`;
  }
  private hourLabel(h: number): string {
    const ampm = h >= 12 && h < 24 ? 'pm' : 'am';
    const hh = h > 12 ? h - 12 : h === 0 ? 12 : h;
    return `${hh}${ampm}`;
  }
}
