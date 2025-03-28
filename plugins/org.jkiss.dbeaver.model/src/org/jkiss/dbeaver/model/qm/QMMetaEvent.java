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

package org.jkiss.dbeaver.model.qm;

import org.jkiss.dbeaver.model.qm.meta.QMMObject;

/**
 * QM meta event
 */
public class QMMetaEvent {

    public enum Action {
        BEGIN(0),
        END(1),
        UPDATE(2),
        ;

        private final int id;

        Action(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public static Action getById(int id) {
            for (Action action : values()) {
                if (action.id == id) {
                    return action;
                }
            }
            return BEGIN;
        }
    }

    protected final QMMObject object;
    protected final Action action;
    protected final String qmAppSessionId;

    public QMMetaEvent(QMMObject object, Action action, String qmAppSessionId) {
        this.object = object;
        this.action = action;
        this.qmAppSessionId = qmAppSessionId;
    }

    public QMMObject getObject() {
        return object;
    }

    public Action getAction() {
        return action;
    }

    public String getQmAppSessionId() {
        return qmAppSessionId;
    }

    @Override
    public String toString() {
        return action + " " + object;
    }
}
