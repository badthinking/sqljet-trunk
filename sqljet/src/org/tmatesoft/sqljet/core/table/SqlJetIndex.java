/**
 * SqlJetDataTableCursor.java
 * Copyright (C) 2009 TMate Software Ltd
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
package org.tmatesoft.sqljet.core.table;

import java.util.List;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.internal.ISqlJetVdbeMem;
import org.tmatesoft.sqljet.core.internal.SqlJetUtility;
import org.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeIndexTable;
import org.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeRecord;
import org.tmatesoft.sqljet.core.internal.vdbe.SqlJetBtreeRecord;

/**
 * @author TMate Software Ltd.
 * @author Sergey Scherbina (sergey.scherbina@gmail.com)
 * 
 */
public class SqlJetIndex extends SqlJetCursor implements ISqlJetIndex {

    private ISqlJetBtreeIndexTable indexTable;

    protected SqlJetIndex(ISqlJetBtreeIndexTable indexTable) {
        super(indexTable);
        this.indexTable = indexTable;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.table.ISqlJetIndex#lookup(java.lang.Object[])
     */
    public long lookup(boolean next, Object... values) throws SqlJetException {
        clearCachedRecord();
        return indexTable.getKeyRowId(indexTable.lookup(next, SqlJetBtreeRecord.getRecord(values)));
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.table.ISqlJetIndex#insert(long,
     * java.lang.Object[])
     */
    public void insert(long rowId, Object... key) throws SqlJetException {
        clearCachedRecord();
        indexTable.insert(SqlJetBtreeRecord.getRecord(SqlJetUtility.addValues(key, rowId)), true);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.table.ISqlJetIndex#delete(long,java.lang.Object[])
     */
    public boolean delete(long rowId,Object... key) throws SqlJetException {
        clearCachedRecord();
        return indexTable.delete(rowId, SqlJetBtreeRecord.getRecord(key));
    }

}
