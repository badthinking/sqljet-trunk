/**
 * SqlJetFileSystemManagerTest.java
 * Copyright (C) 2008 TMate Software Ltd
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package org.tmatesoft.sqljet.core;

import org.tmatesoft.sqljet.core.internal.fs.SqlJetFileSystemsManager;

/**
 * @author TMate Software Ltd.
 * @author Sergey Scherbina (sergey.scherbina@gmail.com)
 *
 */
public class SqlJetFileSystemManagerTest extends SqlJetFileSystemsManagerMockTest {

    /* (non-Javadoc)
     * @see org.tmatesoft.sqljet.core.SqlJetAbstractFileSystemMockTest#setUpInstances()
     */
    @Override
    protected void setUpInstances() throws Exception {
        fileSystemsManager = new SqlJetFileSystemsManager();
        super.setUpInstances();
    }
    
}
