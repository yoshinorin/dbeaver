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
package org.jkiss.dbeaver.ui.dialogs.driver;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.connection.DBPDriver;
import org.jkiss.dbeaver.model.connection.DBPDriverLibrary;
import org.jkiss.dbeaver.registry.driver.DriverDependencies;
import org.jkiss.dbeaver.ui.DBeaverIcons;
import org.jkiss.dbeaver.ui.IHelpContextIds;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.dialogs.HelpEnabledDialog;
import org.jkiss.dbeaver.ui.internal.UIConnectionMessages;
import org.jkiss.utils.CommonUtils;

import java.io.File;
import java.util.Collections;
import java.util.Set;

/**
 * DriverEditDialog
 */
public class DriverLibraryDetailsDialog extends HelpEnabledDialog
{
    private static final String DIALOG_ID = "DBeaver.DriverLibraryDetailsDialog";//$NON-NLS-1$

    private DBPDriver driver;
    private DBPDriverLibrary library;

    public DriverLibraryDetailsDialog(Shell shell, DBPDriver driver, DBPDriverLibrary library)
    {
        super(shell, IHelpContextIds.CTX_DRIVER_EDITOR);
        this.driver = driver;
        this.library = library;
    }

    @Override
    protected IDialogSettings getDialogBoundsSettings()
    {
        return UIUtils.getDialogSettings(DIALOG_ID);
    }

    @Override
    protected Control createDialogArea(Composite parent)
    {
        getShell().setText(NLS.bind(UIConnectionMessages.dialog_edit_driver_text_driver_library, driver.getName(), library.getDisplayName())); //$NON-NLS-2$
        getShell().setImage(DBeaverIcons.getImage(library.getIcon()));

        Composite group = (Composite) super.createDialogArea(parent);
        GridData gd = new GridData(GridData.FILL_BOTH);
        gd.widthHint = 500;
        group.setLayoutData(gd);

        Group propsGroup = UIUtils.createControlGroup(group, UIConnectionMessages.dialog_edit_driver_info, 2, -1, -1);
        propsGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        UIUtils.createLabelText(propsGroup, UIConnectionMessages.dialog_edit_driver_driver, driver.getName(), SWT.BORDER | SWT.READ_ONLY);
        UIUtils.createLabelText(propsGroup, UIConnectionMessages.dialog_edit_driver_library, library.getDisplayName(), SWT.BORDER | SWT.READ_ONLY);
        UIUtils.createLabelText(propsGroup, UIConnectionMessages.dialog_edit_driver_path, library.getPath(), SWT.BORDER | SWT.READ_ONLY);
        UIUtils.createLabelText(propsGroup, UIConnectionMessages.dialog_edit_driver_version, library.getVersion(), SWT.BORDER | SWT.READ_ONLY);
        Text fileText = UIUtils.createLabelText(propsGroup, UIConnectionMessages.dialog_edit_driver_file, "", SWT.BORDER | SWT.READ_ONLY);

        TabFolder tabs = new TabFolder(group, SWT.HORIZONTAL | SWT.FLAT);
        tabs.setLayoutData(new GridData(GridData.FILL_BOTH));

        createDependenciesTab(tabs);
        createLicenseTab(tabs);
        createDetailsTab(tabs);

        final File localFile = library.getLocalFile();
        if (localFile != null) {
            fileText.setText(localFile.getAbsolutePath());
        }

        return group;
    }

    private void createDependenciesTab(TabFolder tabs) {
        Composite paramsGroup = new Composite(tabs, SWT.NONE);
        paramsGroup.setLayout(new GridLayout(1, false));

        final Set<DBPDriverLibrary> libList = Collections.singleton(library);
        DriverDependencies dependencies = new DriverDependencies(libList);
        final DriverDependenciesTree depsTree = new DriverDependenciesTree(
            paramsGroup,
            UIUtils.getDefaultRunnableContext(),
            dependencies,
            driver,
            libList,
            false);
        try {
            depsTree.loadLibDependencies();
        } catch (DBException e) {
            depsTree.handleDownloadError(e);
        }
        UIUtils.asyncExec(depsTree::resizeTree);

        TabItem depsTab = new TabItem(tabs, SWT.NONE);
        depsTab.setText(UIConnectionMessages.dialog_edit_driver_tab_depencencies);
        depsTab.setToolTipText(UIConnectionMessages.dialog_edit_driver_tab_depencencies_tooltip);
        depsTab.setControl(paramsGroup);
    }

    private void createDetailsTab(TabFolder tabs) {
        Composite detailsGroup = new Composite(tabs, SWT.NONE);
        detailsGroup.setLayout(new GridLayout(1, false));

        UIUtils.createControlLabel(detailsGroup, UIConnectionMessages.dialog_edit_driver_label_description);
        Text descriptionText = new Text(detailsGroup, SWT.READ_ONLY | SWT.BORDER);
        descriptionText.setText(CommonUtils.notEmpty(library.getDescription()));
        GridData gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.heightHint = 40;
        descriptionText.setLayoutData(gd);

        TabItem detailsTab = new TabItem(tabs, SWT.NONE);
        detailsTab.setText(UIConnectionMessages.dialog_edit_driver_tab_detail);
        detailsTab.setToolTipText(UIConnectionMessages.dialog_edit_driver_tab_detail_tooltip);
        detailsTab.setControl(detailsGroup);
    }

    private void createLicenseTab(TabFolder group)
    {
        Composite paramsGroup = new Composite(group, SWT.NONE);
        paramsGroup.setLayout(new GridLayout(1, false));

        Text licenseText = new Text(paramsGroup, SWT.BORDER | SWT.WRAP | SWT.MULTI | SWT.V_SCROLL);
        licenseText.setText(UIConnectionMessages.dialog_edit_driver_text_license);
        licenseText.setEditable(false);
        licenseText.setMessage(UIConnectionMessages.dialog_edit_driver_text_driver_license);
        final GridData gd = new GridData(GridData.FILL_BOTH);
        gd.heightHint = 200;
        //gd.grabExcessVerticalSpace = true;
        licenseText.setLayoutData(gd);

        TabItem paramsTab = new TabItem(group, SWT.NONE);
        paramsTab.setText(UIConnectionMessages.dialog_edit_driver_tab_name_license);
        paramsTab.setToolTipText(UIConnectionMessages.dialog_edit_driver_tab_tooltip_license);
        paramsTab.setControl(paramsGroup);
    }


    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.CLOSE_ID, IDialogConstants.CLOSE_LABEL, true);
    }

    @Override
    protected void buttonPressed(int buttonId) {
        super.okPressed();
    }
}
