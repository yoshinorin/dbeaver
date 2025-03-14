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
package org.jkiss.dbeaver.model.navigator.fs;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.DBConstants;
import org.jkiss.dbeaver.model.DBIcon;
import org.jkiss.dbeaver.model.DBPImage;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.fs.DBFFileSystemDescriptor;
import org.jkiss.dbeaver.model.fs.DBFVirtualFileSystem;
import org.jkiss.dbeaver.model.fs.DBFVirtualFileSystemRoot;
import org.jkiss.dbeaver.model.fs.nio.NIOListener;
import org.jkiss.dbeaver.model.fs.nio.NIOMonitor;
import org.jkiss.dbeaver.model.fs.nio.NIOResource;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.navigator.DBNNode;
import org.jkiss.dbeaver.model.navigator.DBNProject;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.utils.CommonUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * DBNFileSystems
 */
public class DBNFileSystems extends DBNNode implements NIOListener {

    private static final Log log = Log.getLog(DBNFileSystems.class);

    private DBNFileSystem[] children;

    public DBNFileSystems(DBNProject parentNode) {
        super(parentNode);

        NIOMonitor.addListener(this);
    }

    @Override
    protected void dispose(boolean reflect) {
        super.dispose(reflect);

        NIOMonitor.removeListener(this);
    }

    @Override
    public String getNodeType() {
        return "FileSystemRoot";
    }

    @Override
    @Property(id = DBConstants.PROP_ID_NAME, viewable = true, order = 1)
    public String getNodeName() {
        return "File Systems";
    }

    @Override
//    @Property(viewable = false, order = 100)
    public String getNodeDescription() {
        return "All virtual file systems";
    }

    @Override
    public DBPImage getNodeIcon() {
        return DBIcon.TREE_FILE;
    }

    @Override
    public boolean allowsChildren() {
        return true;
    }

    public DBNFileSystem getFileSystem(@NotNull String type, @NotNull String id) {
        if (children == null) {
            return null;
        }
        for (DBNFileSystem fsNode : children) {
            DBFVirtualFileSystem fs = fsNode.getFileSystem();
            if (fs.getType().equals(type) && fs.getId().equals(id)) {
                return fsNode;
            }
        }
        return null;
    }

    @Override
    public DBNFileSystem[] getChildren(DBRProgressMonitor monitor) throws DBException {
        if (children == null) {
            this.children = readChildNodes(monitor);
        }
        return children;
    }

    protected DBNFileSystem[] readChildNodes(DBRProgressMonitor monitor) throws DBException {
        monitor.beginTask("Read available file systems", 1);
        List<DBNFileSystem> result = new ArrayList<>();
        for (DBFFileSystemDescriptor fsProvider : DBWorkbench.getPlatform().getFileSystemRegistry().getFileSystemProviders()) {
            DBFVirtualFileSystem[] fsList = fsProvider.getInstance().getAvailableFileSystems(
                monitor, getModel().getModelAuthContext());
            for (DBFVirtualFileSystem fs : fsList) {
                DBNFileSystem newChild = new DBNFileSystem(this, fs);
                result.add(newChild);
            }
        }
        result.sort(DBUtils.nameComparatorIgnoreCase());
        monitor.done();
        return result.toArray(new DBNFileSystem[0]);
    }

    @Override
    public boolean isManagable() {
        return true;
    }

    @Override
    public DBNNode refreshNode(DBRProgressMonitor monitor, Object source) {
        children = null;
        return this;
    }

    @Override
    public String getNodeItemPath() {
        return NodePathType.dbvfs.getPrefix();
    }

    @Override
    public boolean supportsRename() {
        return false;
    }

    @Override
    public void resourceChanged(NIOResource resource, Action action) {
        if (!CommonUtils.equalObjects(getOwnerProject().getEclipseProject(), resource.getProject())) {
            return;
        }
        if (children == null) {
            return;
        }
        DBFVirtualFileSystemRoot dbfRoot = resource.getRoot().getRoot();

        for (DBNFileSystem fs : children) {
            if (CommonUtils.equalObjects(fs.getFileSystem(), dbfRoot.getFileSystem())) {
                DBNFileSystemRoot rootNode = fs.getRoot(dbfRoot);
                if (rootNode != null) {
                    String[] pathSegments = resource.getFullPath().segments();
                    DBNPathBase parentNode = rootNode;
                    for (int i = 1; i < pathSegments.length - 1; i++) {
                        String itemName = pathSegments[i];
                        parentNode = parentNode.getChild(itemName);
                        if (parentNode == null) {
                            return;
                        }
                    }

                    switch (action) {
                        case CREATE:
                            parentNode.addChildResource(resource.getNioPath());
                            break;
                        case DELETE:
                            parentNode.removeChildResource(resource.getNioPath());
                            break;
                        default:
                            break;
                    }
                }
                break;
            }
        }
    }

}
