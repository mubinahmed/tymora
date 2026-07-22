/*
 * Angular migration - Wave 0 facade: async execution manager.
 *
 * Runs long-running GWT-RPC commands (e.g. the solver) on background daemon
 * threads and exposes a submit / poll / cancel lifecycle for a REST client to
 * drive. This mirrors the executeAsync / waitForResults / cancelExecution
 * mechanics already in GwtRpcServlet, but with a non-blocking poll (idiomatic
 * for an Angular client) and no dependency on GwtRpcServlet internals.
 *
 * The actual command work is supplied as a Callable by the servlet, which wraps
 * GwtRpcServlet.execute(...) together with the same thread-local setup/teardown
 * the original Execution performs (locale, session id, Hibernate cleanup).
 */
package org.unitime.timetable.rest;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.unitime.timetable.gwt.command.client.GwtRpcResponse;

public class AsyncRpcExecutor {

	public enum State { RUNNING, DONE, ERROR, CANCELLED }

	/** Immutable snapshot returned to a poller. */
	public static class Status {
		private final State iState;
		private final GwtRpcResponse iResult;
		private final String iError;
		Status(State state, GwtRpcResponse result, String error) {
			iState = state; iResult = result; iError = error;
		}
		public State getState() { return iState; }
		public GwtRpcResponse getResult() { return iResult; }
		public String getError() { return iError; }
		public boolean isRunning() { return iState == State.RUNNING; }
	}

	private final Map<Long, Execution> iExecutions = new ConcurrentHashMap<Long, Execution>();
	private final AtomicLong iIds = new AtomicLong(0);
	private final long iRetentionMs;

	/** @param retentionMs how long a finished execution is kept for retrieval before GC */
	public AsyncRpcExecutor(long retentionMs) { iRetentionMs = retentionMs; }

	/** Submit work; returns the execution id to poll. */
	public long submit(Callable<GwtRpcResponse> task) {
		sweep();
		long id = iIds.incrementAndGet();
		Execution ex = new Execution(id, task);
		iExecutions.put(id, ex);
		ex.start();
		return id;
	}

	/** Non-blocking status. Returns null if the id is unknown or expired. */
	public Status poll(long id) {
		Execution ex = iExecutions.get(id);
		if (ex == null) return null;
		synchronized (ex) {
			if (ex.iState == State.RUNNING) return new Status(State.RUNNING, null, null);
			ex.iRetrievedAt = System.currentTimeMillis();
			return new Status(ex.iState, ex.iResult, ex.iError);
		}
	}

	/** Request cancellation. Returns false if the id is unknown or expired. */
	public boolean cancel(long id) {
		Execution ex = iExecutions.get(id);
		if (ex == null) return false;
		ex.cancel();
		return true;
	}

	public int size() { return iExecutions.size(); }

	/** Drop finished executions older than the retention window. */
	private void sweep() {
		long now = System.currentTimeMillis();
		for (Iterator<Execution> it = iExecutions.values().iterator(); it.hasNext(); ) {
			Execution ex = it.next();
			if (ex.iState != State.RUNNING && ex.iDoneAt > 0 && now - ex.iDoneAt > iRetentionMs)
				it.remove();
		}
	}

	// NB: extends Thread, whose nested Thread.State would shadow our enum here,
	// so State references inside this class are qualified as AsyncRpcExecutor.State.
	private class Execution extends Thread {
		private final Callable<GwtRpcResponse> iTask;
		private volatile AsyncRpcExecutor.State iState = AsyncRpcExecutor.State.RUNNING;
		private volatile GwtRpcResponse iResult;
		private volatile String iError;
		private volatile long iDoneAt;
		private volatile long iRetrievedAt;

		Execution(long id, Callable<GwtRpcResponse> task) {
			iTask = task;
			setDaemon(true);
			setName("AsyncRpc:" + id);
		}

		@Override
		public void run() {
			try {
				GwtRpcResponse r = iTask.call();
				synchronized (this) {
					if (iState == AsyncRpcExecutor.State.RUNNING) { iResult = r; iState = AsyncRpcExecutor.State.DONE; }
				}
			} catch (Throwable t) {
				String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
				synchronized (this) {
					if (iState == AsyncRpcExecutor.State.RUNNING) { iError = msg; iState = AsyncRpcExecutor.State.ERROR; }
				}
			} finally {
				iDoneAt = System.currentTimeMillis();
			}
		}

		void cancel() {
			synchronized (this) {
				if (iState == AsyncRpcExecutor.State.RUNNING) { iState = AsyncRpcExecutor.State.CANCELLED; iDoneAt = System.currentTimeMillis(); }
			}
			interrupt();
		}
	}
}