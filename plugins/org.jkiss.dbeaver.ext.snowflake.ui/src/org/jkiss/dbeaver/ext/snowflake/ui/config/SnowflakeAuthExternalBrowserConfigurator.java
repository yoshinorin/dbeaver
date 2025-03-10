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
package org.jkiss.dbeaver.ext.snowflake.ui.config;

import org.eclipse.swt.widgets.Composite;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;

/**
 * Snowflake external browser auth model config
 */
public class SnowflakeAuthExternalBrowserConfigurator extends SnowflakeAuthSnowflakeConfigurator {

    @Override
    public void createControl(Composite parent, Runnable propertyChangeListener) {
        super.createControl(parent, propertyChangeListener);
    }

    @Override
    public void loadSettings(DBPDataSourceContainer dataSource) {
        super.loadSettings(dataSource);
    }

    @Override
    public void saveSettings(DBPDataSourceContainer dataSource) {
        super.saveSettings(dataSource);
    }

    @Override
    protected boolean supportsPassword() {
        return false;
    }


}
