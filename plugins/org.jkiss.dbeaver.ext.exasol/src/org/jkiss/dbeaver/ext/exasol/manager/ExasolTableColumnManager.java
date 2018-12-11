/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2016-2016 Karl Griesser (fullref@gmail.com)
 * Copyright (C) 2010-2017 Serge Rider (serge@jkiss.org)
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
package org.jkiss.dbeaver.ext.exasol.manager;

import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ext.exasol.model.ExasolTable;
import org.jkiss.dbeaver.ext.exasol.model.ExasolTableBase;
import org.jkiss.dbeaver.ext.exasol.model.ExasolTableColumn;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.edit.DBEObjectRenamer;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.impl.DBSObjectCache;
import org.jkiss.dbeaver.model.impl.edit.SQLDatabasePersistAction;
import org.jkiss.dbeaver.model.impl.sql.edit.struct.SQLTableColumnManager;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.utils.CommonUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Exasol Table Column Manager
 *
 * @author Karl Griesser
 */
public class ExasolTableColumnManager extends SQLTableColumnManager<ExasolTableColumn, ExasolTableBase> implements DBEObjectRenamer<ExasolTableColumn> {

    private static final String SQL_ALTER = "ALTER TABLE %s MODIFY COLUMN %s ";
    private static final String SQL_COMMENT = "COMMENT ON COLUMN %s.%s IS '%s'";
    private static final Log log = Log.getLog(ExasolTableColumnManager.class);
    


    private static final String CMD_ALTER = "Alter Column";
    private static final String CMD_COMMENT = "Comment on Column";
    private static final String DROP_DIST_KEY = "ALTER TABLE %s DROP DISTRIBUTION KEYS";
    private static final String CREATE_DIST_KEY = "ALTER TABLE %s DISTRIBUTE BY %s";


    // -----------------
    // Business Contract
    // -----------------
    @Nullable
    @Override
    public DBSObjectCache<? extends DBSObject, ExasolTableColumn> getObjectsCache(ExasolTableColumn object) {
        return object.getParentObject().getContainer().getTableCache().getChildrenCache((ExasolTable) object.getParentObject());
    }

    @Override
    public boolean canEditObject(ExasolTableColumn object) {
        // Edit is only availabe for ExasolTable and not for other kinds of tables (View, MQTs, Nicknames..)
        ExasolTableBase exasolTableBase = object.getParentObject();
        if ((exasolTableBase != null) & (exasolTableBase.getClass().equals(ExasolTable.class))) {
            return true;
        } else {
            return false;
        }
    }

    // ------
    // Create
    // ------

    @Override
    protected ExasolTableColumn createDatabaseObject(DBRProgressMonitor monitor, DBECommandContext context, ExasolTableBase parent,
                                                     Object copyFrom) {
        ExasolTableColumn column = new ExasolTableColumn(parent);
        column.setName(getNewColumnName(monitor, context, parent));
        return column;
    }
    

    // -----
    // Alter
    // -----
    @Override
    protected void addObjectModifyActions(DBRProgressMonitor monitor, List<DBEPersistAction> actionList, ObjectChangeCommand command, Map<String, Object> options) {
        ExasolTableColumn exasolColumn = command.getObject();
        Map<Object,Object> props = command.getProperties();

        if ( props.containsKey("defaultValue") ||
        		props.containsKey("dataType") ||
        		props.containsKey("scale") ||
        		props.containsKey("maxLength") ||
        		props.containsKey("autoGenerated") ||
        		props.containsKey("identityValue") ||
        		props.containsKey("required")
           ) {
        	
        	// build nullability string
        	String nullability = "";
        	if (exasolColumn.isOriRequired() != null &&  exasolColumn.isOriRequired() != exasolColumn.isRequired())
        		nullability = exasolColumn.isRequired() ? "NOT NULL" : "NULL";
        		
        	
            final String deltaSQL = DBUtils.getQuotedIdentifier(exasolColumn) + " " + exasolColumn.getFormatType()
                + " " + (exasolColumn.getDefaultValue() == null ? "" : " DEFAULT " + exasolColumn.getDefaultValue())
                + " " + formatIdentiy(exasolColumn.isAutoGenerated(), exasolColumn.getIdentityValue())
                + " " + nullability;
            if (!deltaSQL.isEmpty()) {
                String sqlAlterColumn = String.format(SQL_ALTER, exasolColumn.getTable().getFullyQualifiedName(DBPEvaluationContext.DDL), deltaSQL);
                actionList.add(new SQLDatabasePersistAction(CMD_ALTER, sqlAlterColumn));
            }
        }
        // Comment
        DBEPersistAction commentAction = buildCommentAction(exasolColumn);
        if (commentAction != null) {
            actionList.add(commentAction);
        }
        
        if (command.getProperties().containsKey("distKey"))
        {
        	try {
				actionList.addAll(modifyDistKey(exasolColumn));
			} catch (DBException e) {
				log.error("Failed to modify distkey settings",e);
			}
        }

    }
    
    private String formatIdentiy(Boolean isAutoGenerated,BigDecimal identityValue)
    {
    	String ret = "";
    	if (isAutoGenerated)
    	{
    		ret = "IDENTITY ";
    		
    		if (identityValue != null)
    		{
    			ret = ret + identityValue.toString() + " ";
    		}
    	}
    	
    	return ret;
    }

    // -------
    // Helpers
    // -------
    private DBEPersistAction buildCommentAction(ExasolTableColumn exasolColumn) {
        if (CommonUtils.isNotEmpty(exasolColumn.getDescription())) {
            String tableName = exasolColumn.getTable().getFullyQualifiedName(DBPEvaluationContext.DDL);
            String columnName = exasolColumn.getName();
            String comment = exasolColumn.getDescription();
            String commentSQL = String.format(SQL_COMMENT, tableName, columnName, comment);
            return new SQLDatabasePersistAction(CMD_COMMENT, commentSQL);
        } else {
            return null;
        }
    }


    @Override
    public void renameObject(DBECommandContext commandContext, ExasolTableColumn object, String newName) throws DBException {
        processObjectRename(commandContext, object, newName);
    }

    @Override
    protected void addObjectRenameActions(DBRProgressMonitor monitor, List<DBEPersistAction> actions, ObjectRenameCommand command, Map<String, Object> options) {
        final ExasolTableColumn column = command.getObject();

        actions.add(
            new SQLDatabasePersistAction(
                "Rename column",
                "ALTER TABLE " + column.getTable().getFullyQualifiedName(DBPEvaluationContext.DDL) + " RENAME COLUMN " +
                    DBUtils.getQuotedIdentifier(column.getDataSource(), command.getOldName()) + " TO " +
                    DBUtils.getQuotedIdentifier(column.getDataSource(), command.getNewName()))
        );
    }
    
    @Override
    protected void addObjectCreateActions(DBRProgressMonitor monitor, List<DBEPersistAction> actions,
                                          ObjectCreateCommand command,
                                          Map<String, Object> options) {
    	final ExasolTableColumn exasolColumn = command.getObject();
    	
    	// build nullability string
    	String nullability = "";
		nullability = exasolColumn.isRequired() ? "NOT NULL" : "NULL";
    		
        final String addSQL = DBUtils.getQuotedIdentifier(exasolColumn) + " " + exasolColumn.getFormatType()
            + " " + (exasolColumn.getDefaultValue() == null ? "" : " DEFAULT " + exasolColumn.getDefaultValue())
            + " " + formatIdentiy(exasolColumn.isAutoGenerated(), exasolColumn.getIdentityValue())
            + " " + nullability;
    	
        actions.add(
                new SQLDatabasePersistAction(
                    "Add column",
                    "ALTER TABLE " + exasolColumn.getTable().getFullyQualifiedName(DBPEvaluationContext.DDL) + " ADD COLUMN " + addSQL + " "
                		)
                );
        
        if (exasolColumn.isDistKey())
			try {
				modifyDistKey(exasolColumn);
			} catch (DBException e) {
				log.error("Failed to generate distribution key",e);
			}
        
    }
    

    /*
     * handling for Distribution key
     */
    
    private Collection<String> removeColumnFromDistKey(ExasolTableColumn exasolColumn) throws DBException
    {
    	ExasolTable table = (ExasolTable) exasolColumn.getParentObject();
    	Collection<ExasolTableColumn> distKey = table.getDistributionKey(new VoidProgressMonitor());
    	
    	if (distKey.size() == 1)
    	{
    		
    	}
    	
		return null;
    }
    
    private SQLDatabasePersistAction generateDropDist(ExasolTableColumn exasolColumn)
    {
    	
    	return new SQLDatabasePersistAction(
    			"Drop Distribution Key",
    			String.format(
    					DROP_DIST_KEY, 
    					((ExasolTable) exasolColumn.getParentObject()).getFullyQualifiedName(DBPEvaluationContext.DDL)
    					)
    			);
    }
    
    private SQLDatabasePersistAction generateCreateDist(Collection<ExasolTableColumn> distKey)
    {
    	ExasolTable table = null;
    	Collection<String> names = new ArrayList<String>();
    	
    	for(ExasolTableColumn c: distKey)
    	{
    		if (table == null)
    			table = (ExasolTable) c.getParentObject();
    		names.add(c.getName());
    	}
    	
    	return new SQLDatabasePersistAction(
    			"Create Distribution Key",
    			String.format(CREATE_DIST_KEY, table.getFullyQualifiedName(DBPEvaluationContext.DDL), CommonUtils.joinStrings(",", names))
    	);
    	
    	
    }
    private Collection<SQLDatabasePersistAction> modifyDistKey(ExasolTableColumn exasolColumn) throws DBException
    {
    	ExasolTable table = (ExasolTable) exasolColumn.getParentObject();
    	Collection<ExasolTableColumn> distKey = table.getDistributionKey(new VoidProgressMonitor());
    	Collection<SQLDatabasePersistAction> commands = new ArrayList<SQLDatabasePersistAction>();
    	
    	if (table.getHasDistKey(new VoidProgressMonitor()))
    	{
    		commands.add(generateDropDist(exasolColumn));
    	}
    	
    	if (!distKey.isEmpty())
    		commands.add(generateCreateDist(distKey));
    	return commands;
    }
    
    
}
