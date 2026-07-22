import { Component, computed, input } from '@angular/core';
import { EventInterface, MeetingInterface } from '../../core/models';

const PX_PER_SLOT = 44 / 12; // ~44px per hour (12 five-minute slots)
const DAY_NAMES = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

interface Block {
  key: string;
  eventName: string;
  eventType: string;
  day: number;
  startSlot: number;
  endSlot: number;
  count: number; // number of dated meetings collapsed into this weekly block
  time: string;
  top: number;
  height: number;
  leftPct: number;
  widthPct: number;
}
interface DayColumn {
  index: number;
  name: string;
  blocks: Block[];
}
interface Layout {
  empty: boolean;
  height: number;
  days: DayColumn[];
  hours: { label: string; top: number }[];
}

/**
 * Weekly-pattern timetable grid for a set of events. Each event's meetings are
 * collapsed by (day-of-week, start/end slot) into positioned blocks — the
 * standard UniTime "representative week" view. 5-minute slots (min = 5*slot),
 * dayOfWeek 0=Monday..6=Sunday. Overlapping blocks in a day share the width.
 */
@Component({
  selector: 'app-event-grid',
  imports: [],
  templateUrl: './event-grid.html',
})
export class EventGrid {
  readonly events = input.required<EventInterface[]>();

  protected readonly layout = computed<Layout>(() => this.build(this.events()));

  private build(events: EventInterface[]): Layout {
    // Collapse meetings into weekly blocks keyed by event + day + time.
    const map = new Map<string, Block>();
    for (const e of events) {
      for (const m of e.meetings ?? []) {
        if (!this.usable(m)) continue;
        const day = m.dayOfWeek!;
        const key = `${e.eventId}|${day}|${m.startSlot}|${m.endSlot}`;
        const existing = map.get(key);
        if (existing) {
          existing.count++;
        } else {
          map.set(key, {
            key,
            eventName: e.eventName ?? '',
            eventType: e.eventType ?? 'Special',
            day,
            startSlot: m.startSlot!,
            endSlot: m.endSlot!,
            count: 1,
            time: `${this.slotTime(m.startSlot!)}–${this.slotTime(m.endSlot!)}`,
            top: 0,
            height: 0,
            leftPct: 0,
            widthPct: 100,
          });
        }
      }
    }

    const all = [...map.values()];
    if (!all.length) return { empty: true, height: 0, days: [], hours: [] };

    // Grid vertical range, rounded to whole hours.
    const minSlot = Math.floor(Math.min(...all.map((b) => b.startSlot)) / 12) * 12;
    const maxSlot = Math.ceil(Math.max(...all.map((b) => b.endSlot)) / 12) * 12;
    const height = (maxSlot - minSlot) * PX_PER_SLOT;

    const daysPresent = [...new Set(all.map((b) => b.day))].sort((a, b) => a - b);
    const days: DayColumn[] = daysPresent.map((index) => {
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
    for (let slot = minSlot; slot <= maxSlot; slot += 12) {
      hours.push({ label: this.slotTime(slot), top: (slot - minSlot) * PX_PER_SLOT });
    }

    return { empty: false, height, days, hours };
  }

  /** Greedy interval partitioning; returns each block's lane and total lanes. */
  private packLanes(blocks: Block[]): { of: Map<string, number>; count: number } {
    const laneEnds: number[] = [];
    const of = new Map<string, number>();
    for (const b of blocks) {
      let lane = laneEnds.findIndex((end) => end <= b.startSlot);
      if (lane === -1) {
        lane = laneEnds.length;
        laneEnds.push(b.endSlot);
      } else {
        laneEnds[lane] = b.endSlot;
      }
      of.set(b.key, lane);
    }
    return { of, count: Math.max(1, laneEnds.length) };
  }

  private usable(m: MeetingInterface): boolean {
    return (
      m.dayOfWeek != null &&
      m.startSlot != null &&
      m.endSlot != null &&
      m.endSlot > m.startSlot &&
      !(m.startSlot === 0 && m.endSlot === 288) // whole-day / arrange-hours: skip
    );
  }

  private slotTime(slot: number): string {
    const min = 5 * slot;
    const h = Math.floor(min / 60) % 24;
    const m = min % 60;
    return `${h}:${m < 10 ? '0' : ''}${m}`;
  }

  /** Stable CSS class per event type for colour coding. */
  typeClass(type: string): string {
    return 'ev-' + (type || 'Special').toLowerCase();
  }
}
