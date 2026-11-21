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
package org.unitime.timetable.gwt.shared;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.unitime.timetable.gwt.command.client.GwtRpcRequest;
import org.unitime.timetable.gwt.command.client.GwtRpcResponse;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Examination seat allocation and invigilation (ExamSeatingRequest, InvigilationRequest, MyExamsRequest).
 * Plain public-field DTOs (serialised as-is by the JSON bridge).
 */
public class ExamSeatingInterface implements IsSerializable {

	// ------------------------------------------------------------------ shared

	public static class IdName implements IsSerializable {
		public Long id;
		public String name;
		public String detail;
		public IdName() {}
		public IdName(Long id, String name, String detail) { this.id = id; this.name = name; this.detail = detail; }
	}

	/** Review / approval / publication state of the seat allocation or invigilation plan of one exam type. */
	public static class AllocationStatus implements IsSerializable {
		/** DRAFT, APPROVED, PUBLISHED */
		public String status = "DRAFT";
		public String approvedBy, publishedBy, note;
		public Date approvedTs, publishedTs;
		public int version;
		/** number of changes logged since the last publication */
		public int changesSincePublication;
		public boolean canApprove, canEdit;
	}

	/** A validation problem / conflict. */
	public static class Issue implements IsSerializable {
		/** ERROR (mandatory, blocks approval) or WARNING */
		public String severity;
		public String type;
		public String message;
		public Long periodId, roomId, studentId, examId, invigilatorId;
		public String period, room, student, exam, invigilator, seat;
		/** recommended corrective action */
		public String action;
	}

	public static class LogEntry implements IsSerializable {
		public Date timeStamp;
		public String action, detail, userName, period;
	}

	public static class Period implements IsSerializable {
		public Long id;
		public String name, date;
		public int nrExams, nrStudents, nrRooms, nrSeated;
	}

	// ------------------------------------------------------------------ seating

	public static class SeatLayout implements IsSerializable {
		public Long roomId;
		public String room;
		public int capacity, examCapacity;
		public int rows, columns, frontRows;
		/** seat labels, comma separated */
		public String blocked, accessible;
		/** true when no layout is saved and the grid is generated from the capacity */
		public boolean generated;
		public int usableSeats;
	}

	public static class Arrangement implements IsSerializable {
		public Long id, studentId, roomId;
		public String studentName, studentExternalId, room, note, approvedBy;
		public boolean separateRoom, accessibleSeat, frontSeat;
		public Integer extraTimePct;
	}

	/** One seat of a room grid. */
	public static class Seat implements IsSerializable {
		public String label;
		public int row, column;
		public boolean blocked, accessible, front, usable;
		public Long studentId, examId;
		public String studentName, studentExternalId, exam;
		public boolean locked, manual, special;
	}

	public static class RoomPlan implements IsSerializable {
		public Long roomId;
		public String room;
		public int rows, columns, usableSeats, nrSeated;
		public List<Seat> seats = new ArrayList<Seat>();
		/** exams in this room with their number of seated students */
		public List<IdName> exams = new ArrayList<IdName>();
	}

	public static class SeatRow implements IsSerializable {
		public Long studentId, examId, roomId, periodId;
		public String studentName, studentExternalId, exam, room, seat, period, date, time, school;
		public boolean locked, manual, special;
		public Integer extraTimePct;
	}

	public static class ExamSeatingRequest implements GwtRpcRequest<ExamSeatingResponse> {
		/** INIT, ROOM, ALLOCATE, MOVE, SWAP, LOCK, UNLOCK, UNSEAT, LAYOUTS, SAVE_LAYOUT, DELETE_LAYOUT,
		 *  ARRANGEMENTS, SAVE_ARRANGEMENT, DELETE_ARRANGEMENT, VALIDATE, LIST, APPROVE, PUBLISH, REOPEN, LOG */
		public String operation = "INIT";
		public Long examTypeId, periodId, roomId, studentId, examId, toRoomId;
		public String seat, toSeat;
		/** ALLOCATE: sort keys (SCHOOL, EXAM, LAST_NAME, FIRST_NAME, STUDENT_ID), pattern BLOCK or INTERLEAVE */
		public List<String> sort;
		public String pattern;
		public Boolean keepLocked;
		public SeatLayout layout;
		public Arrangement arrangement;
		public Long id;
		/** APPROVE / PUBLISH despite errors (approvers only) */
		public Boolean override;
		public String note;
		/** LIST filter: PERIOD, ROOM, EXAM, STUDENT, SPECIAL, UNSEATED */
		public String filter;
	}

	public static class ExamSeatingResponse implements GwtRpcResponse {
		public List<IdName> examTypes = new ArrayList<IdName>();
		public Long examTypeId;
		public List<Period> periods = new ArrayList<Period>();
		/** rooms of the selected period */
		public List<IdName> rooms = new ArrayList<IdName>();
		public RoomPlan plan;
		/** students of the period without a seat */
		public List<SeatRow> unseated;
		public List<SeatLayout> layouts;
		public List<Arrangement> arrangements;
		public List<IdName> allRooms;
		public List<Issue> issues;
		public List<SeatRow> rows;
		public List<LogEntry> log;
		public AllocationStatus status;
		public String message;
		public boolean canEdit;
		public int nrStudents, nrSeated, nrErrors, nrWarnings;
	}

	// ------------------------------------------------------------------ invigilation

	public static class InvigilatorInfo implements IsSerializable {
		public Long id, departmentId, instructorId;
		public String externalId, name, email, department, dutyTypes, note;
		public Integer maxPerDay, maxTotal;
		public boolean active;
		public int nrDuties;
		/** period ids: UNAVAILABLE / PREFERRED (stored), ABSENT (approved staff absence) */
		public List<IdName> availability = new ArrayList<IdName>();
	}

	public static class InvigilationConfig implements IsSerializable {
		public int studentsPerInvigilator = 30, minPerRoom = 1, extraSpecialRoom = 1, reservesPerPeriod = 0, defaultMaxPerDay = 2, defaultMaxTotal = 10;
		public boolean chiefPerRoom = true, avoidOwnExam = true;
	}

	public static class Duty implements IsSerializable {
		public Long id, invigilatorId, periodId, roomId;
		public String invigilator, dutyType, period, date, time, room;
		public boolean locked, manual;
		/** problems of this duty */
		public List<String> problems = new ArrayList<String>();
	}

	/** One room (or the reserve pool, roomId null) of one period on the duty board. */
	public static class BoardCell implements IsSerializable {
		public Long periodId, roomId;
		public String room;
		public int students, required, assigned;
		public boolean chiefRequired, special;
		public List<Duty> duties = new ArrayList<Duty>();
	}

	public static class Candidate implements IsSerializable {
		public Long invigilatorId;
		public String name, department, reason;
		public int load, dutiesThatDay;
		public boolean preferred, suitable;
	}

	public static class InvigilationRequest implements GwtRpcRequest<InvigilationResponse> {
		/** INIT, BOARD, ALLOCATE, ASSIGN, UNASSIGN, LOCK, UNLOCK, CANDIDATES, INVIGILATORS, SAVE_INVIGILATOR,
		 *  DELETE_INVIGILATOR, IMPORT, SET_AVAILABILITY, CONFIG, SAVE_CONFIG, VALIDATE, DUTIES, APPROVE, PUBLISH, REOPEN, LOG */
		public String operation = "INIT";
		public Long examTypeId, periodId, roomId, invigilatorId, dutyId;
		public String dutyType;
		public Boolean keepLocked;
		public InvigilatorInfo invigilator;
		public InvigilationConfig config;
		/** IMPORT: CSV text (name, email, external id, department code, duty types, max per day, max total) */
		public String csv;
		/** SET_AVAILABILITY: UNAVAILABLE, PREFERRED or AVAILABLE (clears) */
		public String availability;
		public Boolean override;
		public String note;
	}

	public static class InvigilationResponse implements GwtRpcResponse {
		public List<IdName> examTypes = new ArrayList<IdName>();
		public Long examTypeId;
		public List<Period> periods = new ArrayList<Period>();
		public List<BoardCell> board;
		public List<InvigilatorInfo> invigilators;
		public List<Candidate> candidates;
		public InvigilationConfig config;
		public List<Issue> issues;
		public List<Duty> duties;
		public List<LogEntry> log;
		public List<IdName> departments;
		public AllocationStatus status;
		public String message;
		public boolean canEdit;
		public int required, assigned, nrErrors, nrWarnings;
	}

	// ------------------------------------------------------------------ self-service

	public static class MyExamsRequest implements GwtRpcRequest<MyExamsResponse> {
		/** LOAD, SET_AVAILABILITY */
		public String operation = "LOAD";
		public Long periodId;
		public String availability, note;
	}

	public static class MyExamsResponse implements GwtRpcResponse {
		public boolean student, invigilator;
		public String name;
		/** published seats of the student */
		public List<SeatRow> seats;
		/** whether the seat allocation is published */
		public boolean seatsPublished;
		/** published duties of the invigilator */
		public List<Duty> duties;
		public boolean dutiesPublished;
		/** the invigilator's availability per period (UNAVAILABLE, PREFERRED, ABSENT or empty) */
		public List<Period> periods;
		public List<IdName> availability;
		public String message;
	}
}
