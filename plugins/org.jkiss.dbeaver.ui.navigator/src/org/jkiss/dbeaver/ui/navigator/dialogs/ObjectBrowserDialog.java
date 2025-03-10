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
package org.jkiss.dbeaver.ui.navigator.dialogs;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.navigator.*;
import org.jkiss.dbeaver.model.navigator.meta.DBXTreeNode;
import org.jkiss.dbeaver.model.struct.*;
import org.jkiss.dbeaver.ui.internal.UINavigatorMessages;
import org.jkiss.dbeaver.ui.navigator.database.DatabaseNavigatorTree;
import org.jkiss.dbeaver.ui.navigator.database.DatabaseNavigatorTreeFilter;
import org.jkiss.dbeaver.ui.navigator.database.DatabaseNavigatorTreeFilterObjectType;
import org.jkiss.dbeaver.ui.navigator.database.load.TreeNodeSpecial;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * ObjectBrowserDialog
 *
 * @author Serge Rider
 */
public class ObjectBrowserDialog extends Dialog {
    /** Expands first level of each node */
    private static final int TREE_EXPANSION_DEPTH = 2;

    private String title;
    private DBNNode rootNode;
    private DBNNode selectedNode;
    private boolean singleSelection;
    private Class<?>[] allowedTypes;
    private Class<?>[] resultTypes;
    private Class<?>[] leafTypes;
    private List<DBNNode> selectedObjects = new ArrayList<>();
    private TreeNodeSpecial specialNode;
    private DatabaseNavigatorTree navigatorTree;

    private static boolean showConnected;

    private ObjectBrowserDialog(
        Shell parentShell,
        String title,
        DBNNode rootNode,
        DBNNode selectedNode,
        boolean singleSelection,
        Class<?>[] allowedTypes,
        Class<?>[] resultTypes,
        Class<?>[] leafTypes)
    {
        super(parentShell);
        this.title = title;
        this.rootNode = rootNode;
        this.selectedNode = selectedNode;
        this.singleSelection = singleSelection;
        this.allowedTypes = allowedTypes;
        this.resultTypes = resultTypes == null ? allowedTypes : resultTypes;
        this.leafTypes = leafTypes;
    }

    @Override
    protected boolean isResizable()
    {
        return true;
    }

    @Override
    protected Control createDialogArea(Composite parent)
    {
        getShell().setText(title);

        Composite group = (Composite) super.createDialogArea(parent);
        GridData gd = new GridData(GridData.FILL_BOTH);
        group.setLayoutData(gd);

        DatabaseNavigatorTreeFilter filter = new DatabaseNavigatorTreeFilter() {
            @Override
            public boolean isLeafObject(Object object) {
                if (leafTypes != null && leafTypes.length > 0) {
                    if (object instanceof DBNDatabaseNode) {
                        DBNDatabaseNode node = (DBNDatabaseNode) object;
                        DBSObject dbObject = node.getObject();
                        DBXTreeNode meta = node.getMeta();
                        if (dbObject != null) {
                            for (Class<?> leafType : leafTypes) {
                                if (leafType.isAssignableFrom(dbObject.getClass())) {
                                    if (DBSObjectContainer.class.isAssignableFrom(leafType)) {
                                        return !DBNNode.nodeHasStructureContainers(node, meta); // Special case. Node has structure container inside if true (can be recursion)
                                    }
                                    return true;
                                }
                            }
                        }
                    }
                }
                return super.isLeafObject(object);
            }
        };
        navigatorTree = new DatabaseNavigatorTree(group, rootNode, (singleSelection ? SWT.SINGLE : SWT.MULTI) | SWT.BORDER, false, filter);
        gd = new GridData(GridData.FILL_BOTH);
        gd.widthHint = 500;
        gd.heightHint = 500;
        navigatorTree.setLayoutData(gd);

        navigatorTree.setFilterObjectType(DatabaseNavigatorTreeFilterObjectType.connection);
        if (resultTypes != null) {
            for (Class<?> rt : resultTypes) {
                if (DBSEntity.class.isAssignableFrom(rt) || DBSDataContainer.class.isAssignableFrom(rt)) {
                    navigatorTree.setFilterObjectType(DatabaseNavigatorTreeFilterObjectType.table);
                    break;
                } else if (DBSObjectContainer.class.isAssignableFrom(rt)) {
                    navigatorTree.setFilterObjectType(DatabaseNavigatorTreeFilterObjectType.container);
                    break;
                }
            }
        }

        final TreeViewer treeViewer = navigatorTree.getViewer();
        treeViewer.addFilter(new ViewerFilter() {
            @Override
            public boolean select(Viewer viewer, Object parentElement, Object element)
            {
                if (showConnected) {
                    if (element instanceof DBNDataSource) {
                        return ((DBNDataSource) element).getDataSource() != null;
                    }
                    if (element instanceof DBNLocalFolder) {
                        return ((DBNLocalFolder) element).hasConnected();
                    }
                }
                if (element instanceof TreeNodeSpecial || element instanceof DBNLocalFolder) {
                    return true;
                }
                if (element instanceof DBNNode) {
                    if (element instanceof DBNDatabaseFolder) {
                        DBNDatabaseFolder folder = (DBNDatabaseFolder) element;
                        Class<? extends DBSObject> folderItemsClass = folder.getChildrenClass();
                        return folderItemsClass != null && matchesType(folderItemsClass, false);
                    }
                    if (element instanceof DBNProject || element instanceof DBNProjectDatabases ||
                        element instanceof DBNDataSource ||
                        (element instanceof DBSWrapper && matchesType(((DBSWrapper) element).getObject().getClass(), false)))
                    {
                        return true;
                    }
                }
                return false;
            }
        });
        if (selectedNode != null) {
            treeViewer.setSelection(new StructuredSelection(selectedNode));
            if (!(selectedNode instanceof DBNDataSource) || ((DBNDataSource) selectedNode).getDataSourceContainer().isConnected()) {
                treeViewer.expandToLevel(selectedNode, 1);
            }
            selectedObjects.add(selectedNode);
        }
        treeViewer.addSelectionChangedListener(event -> {
            selectedObjects.clear();
            specialNode = null;
            IStructuredSelection selection = (IStructuredSelection) treeViewer.getSelection();
            for (Iterator iter = selection.iterator(); iter.hasNext(); ) {
                Object node = iter.next();
                if (node instanceof DBNNode && node instanceof DBSWrapper) {
                    DBSObject object = DBUtils.getAdapter(DBSObject.class, ((DBSWrapper) node).getObject());
                    if (object != null) {
                        if (!matchesType(object.getClass(), true)) {
                            selectedObjects.clear();
                            break;
                        }
                        selectedObjects.add((DBNNode)node);
                    }
                } else if (node instanceof TreeNodeSpecial) {
                    specialNode = (TreeNodeSpecial)node;
                }
            }
            getButton(IDialogConstants.OK_ID).setEnabled(!selectedObjects.isEmpty());
        });
        treeViewer.addDoubleClickListener(event -> {
            if (!selectedObjects.isEmpty()) {
                okPressed();
            } else if (specialNode != null) {
                specialNode.handleDefaultAction(navigatorTree);
            }
        });
        treeViewer.getTree().setFocus();

        if (rootNode instanceof DBNContainer && ((DBNContainer) rootNode).getChildrenClass() == DBPDataSourceContainer.class) {
            final Button showConnectedCheck = new Button(group, SWT.CHECK);
            showConnectedCheck.setText(UINavigatorMessages.label_show_connected);
            showConnectedCheck.setSelection(showConnected);
            showConnectedCheck.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    showConnected = showConnectedCheck.getSelection();
                    treeViewer.getControl().setRedraw(false);
                    try {
                        treeViewer.refresh();
                        if (showConnected) {
                            treeViewer.expandToLevel(TREE_EXPANSION_DEPTH, false);
                        }
                    } finally {
                        treeViewer.getControl().setRedraw(true);
                    }
                }
            });
        }

        return group;
    }

    private boolean matchesType(Class<?> nodeType, boolean result)
    {
        for (Class<?> ot : result ? resultTypes : allowedTypes) {
            if (ot.isAssignableFrom(nodeType)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected Control createContents(Composite parent)
    {
        Control contents = super.createContents(parent);
        getButton(IDialogConstants.OK_ID).setEnabled(!selectedObjects.isEmpty());
        return contents;
    }

    @Override
    protected void okPressed()
    {
        super.okPressed();
    }

    public List<DBNNode> getSelectedObjects()
    {
        return selectedObjects;
    }

/*
    public static List<DBNNode> selectObjects(Shell parentShell, String title, DBNNode rootNode, DBNNode selectedNode, Class ... allowedTypes)
    {
        ObjectBrowserDialog scDialog = new ObjectBrowserDialog(parentShell, title, rootNode, selectedNode, false, allowedTypes);
        if (scDialog.open() == IDialogConstants.OK_ID) {
            return scDialog.getSelectedObjects();
        } else {
            return null;
        }
    }
*/

    public static DBNNode selectObject(Shell parentShell, String title, DBNNode rootNode, DBNNode selectedNode, Class<?>[] allowedTypes, Class<?>[] resultTypes, Class<?>[] leafTypes)
    {
        ObjectBrowserDialog scDialog = new ObjectBrowserDialog(parentShell, title, rootNode, selectedNode, true, allowedTypes, resultTypes, leafTypes);
        if (scDialog.open() == IDialogConstants.OK_ID) {
            List<DBNNode> result = scDialog.getSelectedObjects();
            return result.isEmpty() ? null : result.get(0);
        } else {
            return null;
        }
    }

    public static List<DBNNode> selectObjects(Shell parentShell, String title, DBNNode rootNode, DBNNode selectedNode, Class<?>[] allowedTypes, Class<?>[] resultTypes, Class<?>[] leafTypes)
    {
        ObjectBrowserDialog scDialog = new ObjectBrowserDialog(parentShell, title, rootNode, selectedNode, false, allowedTypes, resultTypes, leafTypes);
        if (scDialog.open() == IDialogConstants.OK_ID) {
            return scDialog.getSelectedObjects();
        } else {
            return null;
        }
    }

}
