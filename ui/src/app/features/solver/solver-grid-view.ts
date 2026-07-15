import { Component, computed, input } from '@angular/core';
import { TimetableGridCell, TimetableGridModel } from '../../core/models';

const PX_PER_SLOT = 44 / 12;
const DAY_NAMES = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

interface Block {
  key: string;
  label: string;
  title: string;
  bg: string;
  day: number;
  startSlot: number;
  endSlot: number;
  top: number;
  height: number;
  leftPct: number;
  widthPct: number;
}
interface DayCol { index: number; name: string; blocks: Block[] }
interface Layout { empty: boolean; height: number; days: DayCol[]; hours: { label: string; top: number }[] }

/**
 * Renders one TimetableGridModel as a weekly grid. Cells are keyed by
 * (day, slot, length, name) — 5-min slots (min = 5*slot), day 0=Mon..6=Sun,
 * same convention as the GWT grid — coloured by the cell's `background`
 * (a preference colour). Overlaps in a day are lane-packed.
 */
@Component({
  selector: 'app-solver-grid-view',
  imports: [],
  templateUrl: './solver-grid-view.html',
})
export class SolverGridView {
  readonly model = input.required<TimetableGridModel>();

  protected readonly layout = computed<Layout>(() => this.build(this.model().cells ?? []));

  private build(cells: TimetableGridCell[]): Layout {
    const map = new Map<string, Block>();
    for (const c of cells) {
      if (c.day == null || c.slot == null || !c.length) continue;
      const name = c.names?.[0] ?? '';
      const key = `${c.day}|${c.slot}|${c.length}|${name}`;
      if (map.has(key)) continue;
      map.set(key, {
        key,
        label: name,
        title: [name, c.time, (c.rooms ?? []).join(', ')].filter(Boolean).join(' · '),
        bg: c.background || '#c7d2fe',
        day: c.day,
        startSlot: c.slot,
        endSlot: c.slot + c.length,
        top: 0,
        height: 0,
        leftPct: 0,
        widthPct: 100,
      });
    }

    const all = [...map.values()];
    if (!all.length) return { empty: true, height: 0, days: [], hours: [] };

    const minSlot = Math.floor(Math.min(...all.map((b) => b.startSlot)) / 12) * 12;
    const maxSlot = Math.ceil(Math.max(...all.map((b) => b.endSlot)) / 12) * 12;
    const height = (maxSlot - minSlot) * PX_PER_SLOT;

    const daysPresent = [...new Set(all.map((b) => b.day))].sort((a, b) => a - b);
    const days: DayCol[] = daysPresent.map((index) => {
      const blocks = all.filter((b) => b.day === index).sort((a, b) => a.startSlot - b.startSlot || a.endSlot - b.endSlot);
      const lanes = this.packLanes(blocks);
      for (const b of blocks) {
        b.top = (b.startSlot - minSlot) * PX_PER_SLOT;
        b.height = Math.max((b.endSlot - b.startSlot) * PX_PER_SLOT - 2, 12);
        b.widthPct = 100 / lanes.count;
        b.leftPct = lanes.of.get(b.key)! * b.widthPct;
      }
      return { index, name: DAY_NAMES[index] ?? `Day ${index}`, blocks };
    });

    const hours: { label: string; top: number }[] = [];
    for (let slot = minSlot; slot <= maxSlot; slot += 12) hours.push({ label: this.slotTime(slot), top: (slot - minSlot) * PX_PER_SLOT });

    return { empty: false, height, days, hours };
  }

  private packLanes(blocks: Block[]): { of: Map<string, number>; count: number } {
    const laneEnds: number[] = [];
    const of = new Map<string, number>();
    for (const b of blocks) {
      let lane = laneEnds.findIndex((end) => end <= b.startSlot);
      if (lane === -1) { lane = laneEnds.length; laneEnds.push(b.endSlot); } else { laneEnds[lane] = b.endSlot; }
      of.set(b.key, lane);
    }
    return { of, count: Math.max(1, laneEnds.length) };
  }

  private slotTime(slot: number): string {
    const min = 5 * slot;
    const h = Math.floor(min / 60) % 24;
    const m = min % 60;
    return `${h}:${m < 10 ? '0' : ''}${m}`;
  }
}
