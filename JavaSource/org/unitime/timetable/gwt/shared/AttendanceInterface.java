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
 * Attendance management (AttendanceRequest for staff, MyAttendanceRequest for students).
 * Plain public-field DTOs (serialised as-is by the JSON bridge).
 */
public class AttendanceInterface implements IsSerializable {

	/** Attendance policy and capture settings; every value is configurable per academic session. */
	public static class Config implements IsSerializable {
		/** minimum attendance % (below it a follow-up case is raised) */
		public int minPct = 85;
		/** % below which a student is at risk for examination eligibility (null = not used) */
		public Integer eligibilityPct = 75;
		/** QR check-ins later than this after check-in opened count as LATE */
		public int lateMinutes = 15;
		/** how long QR check-in stays open once the lecturer opens it */
		public int checkinMinutes = 20;
		public boolean rollcallEnabled = true, qrEnabled = true;
		public int qrRotationSec = 30;
		/** one device may check in only one student per session */
		public boolean oneDevice = true;
		public boolean lateCountsPresent = true, excusedCountsPresent = true;
		/** this many consecutive unexcused absences in a class raise a follow-up case */
		public int consecutiveAbsences = 3;
		/** rules apply once a student has this many finalised sessions in a class */
		public int minSessions = 4;
		/** LECTURER and/or COORDINATOR (department timetable managers) */
		public String alertRecipients = "LECTURER,COORDINATOR";
		/** LECTURER (any lecturer of the student) or OFFICE (attendance managers only) */
		public String excuseApprover = "OFFICE";
		public boolean notifyStudent = true;
		/** SIMS export columns, comma separated */
		public String exportColumns = "STUDENT_ID,STUDENT_NAME,COURSE,CLASS,DATE,START,END,ROOM,STATUS,SOURCE,EXCUSE";
	}

	/** A dated class meeting and its attendance state. */
	public static class SessionInfo implements IsSerializable {
		public Long meetingId, classId, attendanceSessionId;
		public String className, course, itype, date, time, room, instructors, takerName;
		/** NOT_TAKEN, OPEN, FINALISED */
		public String status = "NOT_TAKEN";
		public int enrolled, present, late, absent, excused;
		public boolean relief, canTake, checkinOpen;
		public Date checkinUntil;
	}

	public static class RosterEntry implements IsSerializable {
		public Long studentId;
		public String externalId, name, status, source, note;
		public Date markedTs;
		public String markedBy;
		/** an approved excuse covers the date */
		public boolean excused;
		/** the student's attendance % in this class so far */
		public Integer ratePct;
	}

	public static class Mark implements IsSerializable {
		public Long studentId;
		public String status, note;
		public Mark() {}
	}

	public static class Excuse implements IsSerializable {
		public Long id, studentId;
		public String studentName, studentExternalId, type, note, fileName, fileType, status, decidedBy, decisionNote;
		public Date fromDate, toDate, submittedTs, decidedTs;
		public boolean canDecide, hasFile;
		/** SUBMIT_EXCUSE: file content, base64; dates as yyyy-MM-dd */
		public String fileBase64, from, to;
	}

	public static class Followup implements IsSerializable {
		public Long id, studentId, classId;
		public String studentName, studentExternalId, className, rule, detail, status, recipients, notes, outcome, updatedBy;
		public Date createdTs, nextReview, updatedTs;
		public Integer ratePct;
	}

	public static class ReportRow implements IsSerializable {
		public String key, label;
		public int sessions, records, present, late, absent, excused;
		public double ratePct;
	}

	public static class ClassStat implements IsSerializable {
		public Long classId;
		public String className, course, instructors;
		public int sessions, present, late, absent, excused;
		public double ratePct;
		/** OK, BELOW_MIN, ELIGIBILITY_RISK */
		public String standing;
	}

	public static class RecordInfo implements IsSerializable {
		public String className, date, time, room, status, source;
	}

	public static class AttendanceRequest implements GwtRpcRequest<AttendanceResponse> {
		/** INIT, SESSIONS, ROLL, MARK, OPEN_CHECKIN, CLOSE_CHECKIN, QR, FINALISE, REOPEN, EXCUSES, DECIDE_EXCUSE, EXCUSE_FILE,
		 *  FOLLOWUPS, UPDATE_FOLLOWUP, EVALUATE, REPORT, CONFIG, SAVE_CONFIG, EXPORT, LOG, DASHBOARD, RUN_REPORT */
		public String operation = "INIT";
		public String date, fromDate, toDate;
		/** SESSIONS: MINE or ALL */
		public String scope;
		public Long meetingId, classId, id;
		public List<Mark> marks;
		public String reason, note, status, outcome, decision;
		/** UPDATE_FOLLOWUP: yyyy-MM-dd or empty */
		public String nextReview;
		/** REPORT: SCHOOL, COURSE, CLASS, STUDENT, INSTRUCTOR, ITYPE, WEEK, WEEKDAY, TIME, ROOM, DATE */
		public String dimension;
		public Config config;
		/** RUN_REPORT: the report id (see Reports); DASHBOARD: DAY, WEEK or TERM for the by-group chart */
		public String report, period;
	}

	/** A named report of the Reports tab and which parameters it takes (DATE or RANGE, plus DIMENSION for CUSTOM). */
	public static class ReportInfo implements IsSerializable {
		public String id, title, description, params;
		public ReportInfo() {}
		public ReportInfo(String id, String title, String description, String params) { this.id = id; this.title = title; this.description = description; this.params = params; }
	}

	/** A report column: text, num, pct (rendered as a bar against the thresholds), status (a tag) or date. */
	public static class ReportColumn implements IsSerializable {
		public String name, type;
		public ReportColumn() {}
		public ReportColumn(String name, String type) { this.name = name; this.type = type; }
	}

	public static class ReportTable implements IsSerializable {
		public String id, title, description, note;
		public List<ReportColumn> columns = new ArrayList<ReportColumn>();
		public List<List<String>> rows = new ArrayList<List<String>>();
		/** summary figures shown above the table (label -> value) */
		public List<List<String>> summary = new ArrayList<List<String>>();
	}

	public static class TrendPoint implements IsSerializable {
		public String date;
		public double ratePct;
		public int records;
	}

	/** An operational alert on the dashboard; target names the tab or report that resolves it. */
	public static class Alert implements IsSerializable {
		public String key, label, detail, severity, target;
		public int count;
	}

	/**
	 * The attendance dashboard for one day. Student-day statuses: present (attended every recorded session on time),
	 * late (attended every recorded session, late in at least one), partial (absent without excuse from some sessions),
	 * absent (absent without excuse from every recorded session), excused (all recorded sessions excused), not recorded
	 * (none of the student's sessions that day has been finalised).
	 */
	public static class Dashboard implements IsSerializable {
		public String date, period, groupBy, periodFrom, periodTo;
		public int expected, present, late, partial, absent, excused, notRecorded;
		public int sessionsScheduled, sessionsFinalised, sessionsOpen, sessionsNotTaken;
		public Double dayRatePct, weekRatePct, termRatePct;
		public int studentsBelowMin, studentsAtRisk;
		public List<ReportRow> byGroup = new ArrayList<ReportRow>();
		public List<ReportRow> lowestClasses = new ArrayList<ReportRow>();
		public List<TrendPoint> trend = new ArrayList<TrendPoint>();
		public List<Alert> alerts = new ArrayList<Alert>();
	}

	public static class AttendanceResponse implements GwtRpcResponse {
		public boolean canManage, canTake;
		public Config config;
		public String termStart, termEnd, today;
		public List<SessionInfo> sessions;
		public SessionInfo session;
		public List<RosterEntry> roster;
		/** QR: the check-in URL token, seconds until it changes */
		public String qrToken;
		public int qrSecondsLeft;
		public List<Excuse> excuses;
		public Excuse excuse;
		public List<Followup> followups;
		public List<ReportRow> report;
		public List<String> exportColumns;
		public List<List<String>> exportRows;
		public List<ExamSeatingInterface.LogEntry> log;
		public int pendingExcuses, openFollowups;
		public Dashboard dashboard;
		public ReportTable table;
		public List<ReportInfo> reports;
		/** INIT: the last day with finalised attendance (the dashboard's default day) */
		public String lastRecorded;
		public String message;
	}

	public static class MyAttendanceRequest implements GwtRpcRequest<MyAttendanceResponse> {
		/** LOAD, CHECKIN, SUBMIT_EXCUSE */
		public String operation = "LOAD";
		public Long meetingId;
		public String token, deviceId;
		public Excuse excuse;
	}

	public static class MyAttendanceResponse implements GwtRpcResponse {
		public boolean student;
		public String name;
		public Config config;
		public List<ClassStat> classes = new ArrayList<ClassStat>();
		public List<RecordInfo> records = new ArrayList<RecordInfo>();
		public List<Excuse> excuses = new ArrayList<Excuse>();
		public List<String> warnings = new ArrayList<String>();
		public double overallPct;
		/** CHECKIN: PRESENT / LATE, the class and time */
		public String checkinStatus, checkinClass;
		public String message;
	}
}
