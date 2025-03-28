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

package org.jkiss.dbeaver.ext.snowflake.model.auth;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.snowflake.SnowflakeConstants;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.impl.auth.AuthModelDatabaseNative;
import org.jkiss.dbeaver.model.impl.auth.AuthModelDatabaseNativeCredentials;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.utils.CommonUtils;

import java.util.Properties;

/**
 * Oracle database native auth model.
 */
public class SnowflakeAuthModelSnowflake extends AuthModelDatabaseNative<AuthModelDatabaseNativeCredentials> {

    public static final String ID = "snowflake_snowflake";

    @Override
    public Object initAuthentication(@NotNull DBRProgressMonitor monitor, @NotNull DBPDataSource dataSource, AuthModelDatabaseNativeCredentials credentials, DBPConnectionConfiguration configuration, @NotNull Properties connProperties) throws DBException {
        if (connProperties.getProperty("authenticator") == null) {
            // If "authenticator" is already set by user then do not change it
            String authenticator = getAuthenticator();
            if (authenticator != null) {
                connProperties.put("authenticator", authenticator);
            }
        }
        String roleName = configuration.getAuthProperty(SnowflakeConstants.PROP_AUTH_ROLE);
        if (!CommonUtils.isEmpty(roleName)) {
            connProperties.put("role", roleName);
        }

        return super.initAuthentication(monitor, dataSource, credentials, configuration, connProperties);
    }

    @Override
    public void endAuthentication(@NotNull DBPDataSourceContainer dataSource, @NotNull DBPConnectionConfiguration configuration, @NotNull Properties connProperties) {
        super.endAuthentication(dataSource, configuration, connProperties);
    }

    protected String getAuthenticator() {
        return "snowflake";
    }

}
