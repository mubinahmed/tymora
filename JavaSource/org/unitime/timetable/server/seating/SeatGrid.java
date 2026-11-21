/*
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 *
 * The Apereo Foundation licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
*/
package org.unitime.timetable.server.seating;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.unitime.timetable.model.ExamSeatLayout;
import org.unitime.timetable.model.Location;

/**
 * Seat grid of a venue: from its saved layout, or generated from the venue capacity (10 seats a row).
 * Seats are labelled by row letter and column number (A1, A2, ... B1, ...). With alternate (exam)
 * seating only every other seat is used, in a checkerboard pattern.
 */
public class SeatGrid {
	public static class Cell {
		public String label;
		public int row, column;
		public boolean blocked, accessible, front;
		public boolean usable;
	}

	public final int rows, columns, frontRows;
	public final boolean generated;
	public final List<Cell> cells = new ArrayList<Cell>();

	public SeatGrid(Location room, ExamSeatLayout layout, boolean alternateSeating) {
		int capacity = capacity(room, alternateSeating);
		Set<String> blocked = labels(layout == null ? null : layout.getBlockedSeats());
		Set<String> accessible = labels(layout == null ? null : layout.getAccessibleSeats());
		if (layout != null && layout.getNrRows() != null && layout.getNrColumns() != null && layout.getNrRows() > 0 && layout.getNrColumns() > 0) {
			rows = layout.getNrRows(); columns = layout.getNrColumns(); generated = false;
			frontRows = (layout.getFrontRows() == null ? 1 : layout.getFrontRows());
		} else {
			int seats = Math.max(1, alternateSeating ? capacity * 2 : capacity);
			columns = Math.min(10, seats); rows = (seats + columns - 1) / columns; generated = true; frontRows = 1;
			if (accessible.isEmpty()) accessible.add(label(rows - 1, 0)); // a default accessible seat at the back by the door
		}
		for (int r = 0; r < rows; r++)
			for (int c = 0; c < columns; c++) {
				Cell cell = new Cell();
				cell.row = r; cell.column = c; cell.label = label(r, c);
				cell.blocked = blocked.contains(cell.label);
				cell.accessible = accessible.contains(cell.label);
				cell.front = r < frontRows;
				cells.add(cell);
			}
		// accessible seats count first (they are often at the back), then the rest front to back up to the capacity
		int usable = 0;
		for (Cell cell: cells)
			if (cell.accessible && !cell.blocked && usable < capacity) { cell.usable = true; usable++; }
		for (Cell cell: cells) {
			if (cell.usable || cell.blocked) continue;
			boolean pattern = !alternateSeating || (cell.row + cell.column) % 2 == 0;
			if (pattern && usable < capacity) { cell.usable = true; usable++; }
		}
	}

	/** Seats usable for the exam(s), capped by the venue capacity. */
	public List<Cell> usable() {
		List<Cell> ret = new ArrayList<Cell>();
		for (Cell c: cells) if (c.usable) ret.add(c);
		return ret;
	}

	public Cell cell(String label) {
		if (label == null) return null;
		for (Cell c: cells) if (c.label.equals(label)) return c;
		return null;
	}

	public static int capacity(Location room, boolean alternateSeating) {
		Integer cap = alternateSeating ? room.getExamCapacity() : room.getCapacity();
		if (cap == null || cap <= 0) cap = room.getCapacity();
		return cap == null ? 0 : cap;
	}

	public static String label(int row, int column) {
		StringBuffer r = new StringBuffer();
		int n = row;
		do { r.insert(0, (char)('A' + (n % 26))); n = n / 26 - 1; } while (n >= 0);
		return r.toString() + (column + 1);
	}

	public static Set<String> labels(String list) {
		Set<String> ret = new HashSet<String>();
		if (list != null)
			for (String s: list.split("[,;\\s]+")) if (!s.trim().isEmpty()) ret.add(s.trim().toUpperCase());
		return ret;
	}
}
