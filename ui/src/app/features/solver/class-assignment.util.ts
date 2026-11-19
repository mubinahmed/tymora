import {
  ClassAssignmentDetails,
  RoomInfo,
  SelectedAssignment,
  TimeInfo,
} from '../../core/models';

/**
 * Formatting helpers for the class assignment dialog, mirroring
 * SuggestionsInterface.TimeInfo (days bit mask MON=64 .. SUN=1, 5-minute slots).
 */
const DAY_CODES = [64, 32, 16, 8, 4, 2, 1];
const DAY_NAMES = ['M', 'T', 'W', 'Th', 'F', 'S', 'Su'];

export function daysName(days: number | undefined): string {
  const d = days ?? 0;
  return DAY_CODES.map((c, i) => ((c & d) !== 0 ? DAY_NAMES[i] : '')).join('');
}

/** minutes since midnight -> "8:30a" (UniTime 12-hour style) */
export function minutesToTime(min: number): string {
  const hour = Math.floor(min / 60);
  const m = min % 60;
  const h12 = hour === 0 ? 12 : hour > 12 ? hour - 12 : hour;
  return `${h12}:${m < 10 ? '0' : ''}${m}${hour < 24 && hour >= 12 ? 'p' : 'a'}`;
}

export function timeName(t: TimeInfo | null | undefined, withDatePattern = false): string {
  if (!t) return '';
  const start = 5 * (t.startSlot ?? 0);
  const s = `${daysName(t.days)} ${minutesToTime(start)} - ${minutesToTime(start + (t.min ?? 0))}`;
  const dp = t.datePattern?.datePatternName;
  return withDatePattern && dp ? `${s} (${dp})` : s;
}

export function roomsName(rooms: RoomInfo[] | null | undefined): string {
  return (rooms ?? []).map((r) => r.name ?? '').join(', ');
}

export function sameTime(a: TimeInfo | null | undefined, b: TimeInfo | null | undefined): boolean {
  if (!a || !b) return false;
  return (
    a.days === b.days &&
    a.startSlot === b.startSlot &&
    a.patternId === b.patternId &&
    a.datePattern?.datePatternId === b.datePattern?.datePatternId
  );
}

export function timeKey(t: TimeInfo | null | undefined): string {
  return t ? `${t.days}|${t.startSlot}|${t.patternId}|${t.datePattern?.datePatternId}` : '';
}

/** SelectedAssignment for a class from a time and a list of room ids. */
export function selection(classId: number, time: TimeInfo, roomIds: number[]): SelectedAssignment {
  return {
    classId,
    days: time.days,
    startSlot: time.startSlot,
    patternId: time.patternId,
    datePatternId: time.datePattern?.datePatternId,
    roomIds,
  };
}

/** The assignment a suggestion/preview gives this class (its "assigned" side). */
export function assignedSelection(d: ClassAssignmentDetails): SelectedAssignment | null {
  if (!d.assignedTime || d.class?.classId == null) return null;
  return selection(
    d.class.classId,
    d.assignedTime,
    (d.assignedRoom ?? []).map((r) => r.roomId!).filter((x) => x != null),
  );
}

/** Solver criteria (TimetableModel criterion names) shown first in the impact summary, in this order. */
export const KEY_CRITERIA = [
  'Student Conflict',
  'Student Hard Conflict',
  'Student Distance Conflict',
  'Student Committed Conflict',
  'Time Preferences',
  'Room Preferences',
  'Distribution Preferences',
  'Back To Back Instructor Preferences',
  'Too Big Rooms',
  'Useless Half Hours',
  'Perturbations',
];
