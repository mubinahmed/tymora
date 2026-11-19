import { assignedSelection, daysName, minutesToTime, sameTime, selection, timeName } from './class-assignment.util';
import { int2prolog, prefColor } from '../../shared/preference';

describe('class assignment helpers', () => {
  const mwf830 = { days: 84, startSlot: 102, min: 50, patternId: 1539, datePattern: { datePatternId: 853, datePatternName: 'Full Term' } };

  it('formats day masks like UniTime (MON=64 .. SUN=1)', () => {
    expect(daysName(84)).toBe('MWF');
    expect(daysName(40)).toBe('TTh');
    expect(daysName(127)).toBe('MTWThFSSu');
  });

  it('formats 5-minute slots as 12-hour times', () => {
    expect(minutesToTime(510)).toBe('8:30a');
    expect(minutesToTime(720)).toBe('12:00p');
    expect(minutesToTime(0)).toBe('12:00a');
    expect(timeName(mwf830)).toBe('MWF 8:30a - 9:20a');
    expect(timeName(mwf830, true)).toBe('MWF 8:30a - 9:20a (Full Term)');
  });

  it('compares times by days, slot, time pattern and date pattern', () => {
    expect(sameTime(mwf830, { ...mwf830 })).toBe(true);
    expect(sameTime(mwf830, { ...mwf830, startSlot: 114 })).toBe(false);
    expect(sameTime(mwf830, { ...mwf830, datePattern: { datePatternId: 1 } })).toBe(false);
  });

  it('builds SelectedAssignment payloads', () => {
    expect(selection(5, mwf830, [7])).toEqual({ classId: 5, days: 84, startSlot: 102, patternId: 1539, datePatternId: 853, roomIds: [7] });
    expect(assignedSelection({ class: { classId: 5 }, assignedTime: mwf830, assignedRoom: [{ roomId: 7 }] })).toEqual(
      selection(5, mwf830, [7]),
    );
    expect(assignedSelection({ class: { classId: 5 } })).toBeNull();
  });

  it('maps solver preference values to UniTime levels', () => {
    expect(int2prolog(-100)).toBe('R');
    expect(int2prolog(-4)).toBe('-2');
    expect(int2prolog(-1)).toBe('-1');
    expect(int2prolog(0)).toBe('0');
    expect(int2prolog(1)).toBe('1');
    expect(int2prolog(4)).toBe('2');
    expect(int2prolog(100)).toBe('P');
    expect(prefColor('0')).toBe('');
    expect(prefColor('P')).toBe('#c81e14');
  });
});
