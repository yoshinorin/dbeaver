/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2022 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.runtime.qm;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.app.DBPProject;
import org.jkiss.dbeaver.model.app.DBPWorkspace;
import org.jkiss.dbeaver.model.auth.DBASession;
import org.jkiss.dbeaver.model.auth.DBASessionPersistent;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.exec.DBCResultSet;
import org.jkiss.dbeaver.model.exec.DBCSavepoint;
import org.jkiss.dbeaver.model.exec.DBCStatement;
import org.jkiss.dbeaver.model.qm.QMConstants;
import org.jkiss.dbeaver.model.qm.QMMCollector;
import org.jkiss.dbeaver.model.qm.QMMetaEvent;
import org.jkiss.dbeaver.model.qm.QMMetaListener;
import org.jkiss.dbeaver.model.qm.meta.*;
import org.jkiss.dbeaver.model.runtime.AbstractJob;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.runtime.LoggingProgressMonitor;
import org.jkiss.utils.CommonUtils;
import org.jkiss.utils.LongKeyMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Query manager execution handler implementation
 */
public class QMMCollectorImpl extends DefaultExecutionHandler implements QMMCollector {

    private static final Log log = Log.getLog(QMMCollectorImpl.class);

    private static final long EVENT_DISPATCH_PERIOD = 250;
    private static final int MAX_HISTORY_EVENTS = 10000;

    // Session map
    private LongKeyMap<QMMConnectionInfo> connectionMap = new LongKeyMap<>();
    private List<Long> closedConnections = new ArrayList<>();

    // External listeners
    private final List<QMMetaListener> listeners = new ArrayList<>();

    // Temporary event pool
    private List<QMMetaEvent> eventPool = new ArrayList<>();
    // Sync object
    private final Object historySync = new Object();
    // History (may be purged when limit reached)
    private List<QMMetaEvent> pastEvents = new ArrayList<>();
    private boolean running = true;

    public QMMCollectorImpl()
    {
        new EventDispatcher().schedule(EVENT_DISPATCH_PERIOD);
    }

    public synchronized void dispose()
    {
        if (!connectionMap.isEmpty()) {
            List<QMMConnectionInfo> openSessions = new ArrayList<>();
            for (QMMConnectionInfo connection : connectionMap.values()) {
                if (!connection.isClosed()) {
                    openSessions.add(connection);
                }
            }
            if (!openSessions.isEmpty()) {
                log.warn("Some sessions are still open: " + openSessions);
            }
        }
        synchronized (listeners) {
            if (!listeners.isEmpty()) {
                log.warn("Some QM meta collector listeners are still open: " + listeners);
                listeners.clear();
            }
        }
        running = false;
    }

    boolean isRunning()
    {
        return running;
    }

    @NotNull
    @Override
    public String getHandlerName()
    {
        return "Meta info collector";
    }

    public void addListener(QMMetaListener listener)
    {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void removeListener(QMMetaListener listener)
    {
        synchronized (listeners) {
            if (!listeners.remove(listener)) {
                log.warn("Listener '" + listener + "' is not registered in QM meta collector");
            }
        }
    }

    private List<QMMetaListener> getListeners()
    {
        synchronized (listeners) {
            if (listeners.isEmpty()) {
                return Collections.emptyList();
            }
            if (listeners.size() == 1) {
                return Collections.singletonList(listeners.get(0));
            }
            return new ArrayList<>(listeners);
        }
    }

    private synchronized void tryFireMetaEvent(final QMMObject object, final QMMetaEvent.Action action, DBCExecutionContext context) {
        try {
            DBRProgressMonitor monitor = new LoggingProgressMonitor();
            DBPProject project = context.getDataSource().getContainer().getProject();
            DBASession session = project.getSessionContext().getSpaceSession(monitor, project, false);
            if (session == null) {
                DBPWorkspace workspace = project.getWorkspace();
                session = workspace.getAuthContext().getSpaceSession(monitor, workspace, false);
            }
            DBASessionPersistent sessionPersistent = DBUtils.getAdapter(DBASessionPersistent.class, session);
            if (sessionPersistent == null) {
                log.warn("Session persistent not found");
                return;
            }

            fireMetaEvent(object, action, sessionPersistent);
        } catch (DBException e) {
            log.error("Failed to fire qm meta event", e);
        }
    }

    private synchronized void fireMetaEvent(final QMMObject object, final QMMetaEvent.Action action, DBASessionPersistent session) {
        String qmSessionId = session.getAttribute(QMConstants.QM_SESSION_ID_ATTR);
        if (CommonUtils.isEmpty(qmSessionId)) {
            log.warn("QM session not found");
            return;
        }
        eventPool.add(new QMMetaEvent(object, action, qmSessionId));
    }

    private synchronized List<QMMetaEvent> obtainEvents() {
        if (eventPool.isEmpty()) {
            return Collections.emptyList();
        }
        List<QMMetaEvent> events = eventPool;
        eventPool = new ArrayList<>();
        return events;
    }

    public QMMConnectionInfo getConnectionInfo(DBCExecutionContext context) {
        QMMConnectionInfo connectionInfo = connectionMap.get(context.getContextId());
        if (connectionInfo == null) {
            log.debug("Can't find connectionInfo meta information: " + context.getContextId() + " (" + context.getContextName() + ")");
        }
        return connectionInfo;
    }

    public List<QMMetaEvent> getPastEvents() {
        synchronized (historySync) {
            return new ArrayList<>(pastEvents);
        }
    }

    @Override
    public synchronized void handleContextOpen(@NotNull DBCExecutionContext context, boolean transactional) {
        final long contextId = context.getContextId();
        QMMConnectionInfo connection = connectionMap.get(contextId);
        if (connection == null) {
            connection = new QMMConnectionInfo(
                context,
                transactional);
            connectionMap.put(contextId, connection);
        } else {
            // This session may already be in cache in case of reconnect/invalidate
            // (when context closed and reopened without new context object creation)
            connection.reopen(context);
        }

        // Remove from closed sessions (in case of re-opened connection)
        closedConnections.remove(contextId);
        tryFireMetaEvent(connection, QMMetaEvent.Action.BEGIN, context);
        // Notify
    }

    @Override
    public synchronized void handleContextClose(@NotNull DBCExecutionContext context)
    {
        QMMConnectionInfo session = getConnectionInfo(context);
        if (session != null) {
            session.close();
            tryFireMetaEvent(session, QMMetaEvent.Action.END, context);
        }
        closedConnections.add(context.getContextId());
    }

    @Override
    public synchronized void handleTransactionAutocommit(@NotNull DBCExecutionContext context, boolean autoCommit)
    {
        QMMConnectionInfo sessionInfo = getConnectionInfo(context);
        if (sessionInfo != null) {
            QMMTransactionInfo oldTxn = sessionInfo.changeTransactional(!autoCommit);
            if (oldTxn != null) {
                tryFireMetaEvent(oldTxn, QMMetaEvent.Action.END, context);
            }
            tryFireMetaEvent(sessionInfo, QMMetaEvent.Action.UPDATE, context);
        }
    }

    @Override
    public synchronized void handleTransactionCommit(@NotNull DBCExecutionContext context)
    {
        QMMConnectionInfo sessionInfo = getConnectionInfo(context);
        if (sessionInfo != null) {
            QMMTransactionInfo oldTxn = sessionInfo.commit();
            if (oldTxn != null) {
                tryFireMetaEvent(oldTxn, QMMetaEvent.Action.END, context);
            }
        }
    }

    @Override
    public synchronized void handleTransactionRollback(@NotNull DBCExecutionContext context, DBCSavepoint savepoint)
    {
        QMMConnectionInfo sessionInfo = getConnectionInfo(context);
        if (sessionInfo != null) {
            QMMObject oldTxn = sessionInfo.rollback(savepoint);
            if (oldTxn != null) {
                tryFireMetaEvent(oldTxn, QMMetaEvent.Action.END, context);
            }
        }
    }

    @Override
    public synchronized void handleStatementOpen(@NotNull DBCStatement statement)
    {
        QMMConnectionInfo session = getConnectionInfo(statement.getSession().getExecutionContext());
        if (session != null) {
            QMMStatementInfo stat = session.openStatement(statement);
            tryFireMetaEvent(stat, QMMetaEvent.Action.BEGIN, statement.getSession().getExecutionContext());
        }
    }

    @Override
    public synchronized void handleStatementClose(@NotNull DBCStatement statement, long rows)
    {
        QMMConnectionInfo session = getConnectionInfo(statement.getSession().getExecutionContext());
        if (session != null) {
            QMMStatementInfo stat = session.closeStatement(statement, rows);
            if (stat == null) {
                log.warn("Can't properly handle statement close");
            } else {
                tryFireMetaEvent(stat, QMMetaEvent.Action.END, statement.getSession().getExecutionContext());
            }
        }
    }

    @Override
    public synchronized void handleStatementExecuteBegin(@NotNull DBCStatement statement)
    {
        QMMConnectionInfo session = getConnectionInfo(statement.getSession().getExecutionContext());
        if (session != null) {
            QMMStatementExecuteInfo exec = session.beginExecution(statement);
            if (exec != null) {
                tryFireMetaEvent(exec, QMMetaEvent.Action.BEGIN, statement.getSession().getExecutionContext());
            }
        }
    }

    @Override
    public synchronized void handleStatementExecuteEnd(@NotNull DBCStatement statement, long rows, Throwable error)
    {
        QMMConnectionInfo session = getConnectionInfo(statement.getSession().getExecutionContext());
        if (session != null) {
            QMMStatementExecuteInfo exec = session.endExecution(statement, rows, error);
            if (exec != null) {
                tryFireMetaEvent(exec, QMMetaEvent.Action.END, statement.getSession().getExecutionContext());
            }
        }
    }

    @Override
    public synchronized void handleResultSetOpen(@NotNull DBCResultSet resultSet)
    {
        QMMConnectionInfo session = getConnectionInfo(resultSet.getSession().getExecutionContext());
        if (session != null) {
            QMMStatementExecuteInfo exec = session.beginFetch(resultSet);
            if (exec != null) {
                tryFireMetaEvent(exec, QMMetaEvent.Action.UPDATE, resultSet.getSession().getExecutionContext());
            }
        }
    }

    @Override
    public synchronized void handleResultSetClose(@NotNull DBCResultSet resultSet, long rowCount)
    {
        QMMConnectionInfo session = getConnectionInfo(resultSet.getSession().getExecutionContext());
        if (session != null) {
            QMMStatementExecuteInfo exec = session.endFetch(resultSet, rowCount);
            if (exec != null) {
                tryFireMetaEvent(exec, QMMetaEvent.Action.UPDATE, resultSet.getSession().getExecutionContext());
            }
        }
    }

    private class EventDispatcher extends AbstractJob {

        protected EventDispatcher()
        {
            super("QM meta events dispatcher");
            setUser(false);
            setSystem(true);
        }

        @Override
        protected IStatus run(DBRProgressMonitor monitor)
        {
            final List<QMMetaEvent> events;
            List<Long> sessionsToClose;
            synchronized (QMMCollectorImpl.this) {
                events = obtainEvents();
                sessionsToClose = closedConnections;
                closedConnections.clear();
            }
            if (!events.isEmpty()) {
                final List<QMMetaListener> listeners = getListeners();
                if (!listeners.isEmpty() && !events.isEmpty()) {
                    // Reverse collection. Fresh events must come first.
                    Collections.reverse(events);
                    // Dispatch all events
                    for (QMMetaListener listener : listeners) {
                        try {
                            listener.metaInfoChanged(monitor, events);
                        } catch (Throwable e) {
                            log.error("Error notifying event listener", e);
                        }
                    }
                }
                synchronized (historySync) {
                    pastEvents.addAll(events);
                    int size = pastEvents.size();
                    if (size > MAX_HISTORY_EVENTS) {
                        pastEvents = new ArrayList<>(pastEvents.subList(
                            size - MAX_HISTORY_EVENTS,
                            size));
                    }
                }
            }
            // Cleanup closed sessions
            synchronized (QMMCollectorImpl.this) {
                for (Long sessionId : sessionsToClose) {
                    final QMMConnectionInfo session = connectionMap.get(sessionId);
                    if (session != null && !session.isClosed()) {
                        // It is possible (rarely) that session was reopened before event dispatcher run
                        // In that case just ignore it
                        connectionMap.remove(sessionId);
                    }
                }
            }
            if (isRunning()) {
                this.schedule(EVENT_DISPATCH_PERIOD);
            }
            return Status.OK_STATUS;
        }
    }

}
