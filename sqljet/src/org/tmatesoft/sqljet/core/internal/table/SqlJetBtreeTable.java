/**
 * SqlJetTableWrapper.java
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
package org.tmatesoft.sqljet.core.internal.table;

import java.nio.ByteBuffer;
import java.util.List;

import org.tmatesoft.sqljet.core.SqlJetEncoding;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetValueType;
import org.tmatesoft.sqljet.core.internal.ISqlJetBtree;
import org.tmatesoft.sqljet.core.internal.ISqlJetBtreeCursor;
import org.tmatesoft.sqljet.core.internal.ISqlJetDbHandle;
import org.tmatesoft.sqljet.core.internal.ISqlJetVdbeMem;
import org.tmatesoft.sqljet.core.internal.SqlJetUtility;
import org.tmatesoft.sqljet.core.internal.vdbe.SqlJetBtreeRecord;
import org.tmatesoft.sqljet.core.internal.vdbe.SqlJetKeyInfo;

/**
 * @author TMate Software Ltd.
 * @author Sergey Scherbina (sergey.scherbina@gmail.com)
 * 
 */
public class SqlJetBtreeTable implements ISqlJetBtreeTable {

    protected ISqlJetDbHandle db;
    protected ISqlJetBtree btree;
    protected int rootPage;

    protected boolean write;
    protected boolean index;

    protected ISqlJetBtreeCursor cursor;

    protected SqlJetKeyInfo keyInfo;

    protected ISqlJetBtreeRecord cachedRecord;

    /**
     * @param db
     * @param btree
     * @param rootPage
     * @param write
     * @param index
     * @throws SqlJetException
     */
    public SqlJetBtreeTable(ISqlJetDbHandle db, ISqlJetBtree btree, int rootPage, boolean write, boolean index)
            throws SqlJetException {

        this.db = db;
        this.btree = btree;
        this.rootPage = rootPage;
        this.write = write;
        this.index = index;

        if (index) {
            this.keyInfo = new SqlJetKeyInfo();
            this.keyInfo.setEnc(db.getEncoding());
        }

        this.cursor = btree.getCursor(rootPage, write, index ? keyInfo : null);

        this.cachedRecord = null;

        first();

    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.internal.btree.ISqlJetBtreeTable#close()
     */
    public void close() throws SqlJetException {
        clearCachedRecord();
        cursor.closeCursor();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetBtreeTable#unlock()
     */
    public void unlock() {
        cursor.leaveCursor();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetBtreeTable#lock()
     */
    public void lock() throws SqlJetException {
        verifySchemaCookie(true);
        cursor.enterCursor();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.internal.btree.ISqlJetBtreeTable#eof()
     */
    public boolean eof() {
        return cursor.eof();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetBtreeTable#first()
     */
    public boolean first() throws SqlJetException {
        lock();
        try {
            clearCachedRecord();
            return !cursor.first();
        } finally {
            unlock();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetBtreeTable#last()
     */
    public boolean last() throws SqlJetException {
        lock();
        try {
            clearCachedRecord();
            return !cursor.last();
        } finally {
            unlock();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.internal.btree.ISqlJetBtreeTable#next()
     */
    public boolean next() throws SqlJetException {
        lock();
        try {
            clearCachedRecord();
            return !cursor.next();
        } finally {
            unlock();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetBtreeTable#previous()
     */
    public boolean previous() throws SqlJetException {
        lock();
        try {
            clearCachedRecord();
            return !cursor.previous();
        } finally {
            unlock();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.internal.btree.ISqlJetBtreeTable#getRecord
     */
    public ISqlJetBtreeRecord getRecord() throws SqlJetException {
        if (eof())
            return null;
        lock();
        try {
            return new SqlJetBtreeRecord(cursor, index);
        } finally {
            unlock();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeTable#lockTable(
     * boolean)
     */
    public void lockTable(boolean write) {
        btree.lockTable(rootPage, write);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeTable#getEncoding()
     */
    public SqlJetEncoding getEncoding() throws SqlJetException {
        return cursor.getCursorDb().getEncoding();
    }

    protected void clearCachedRecord() {
        cachedRecord = null;
    }

    protected ISqlJetBtreeRecord getCachedRecord() throws SqlJetException {
        if (null == cachedRecord) {
            cachedRecord = getRecord();
        }
        return cachedRecord;
    }

    protected boolean checkField(int field) throws SqlJetException {
        return (field >= 0 && field < getFieldsCount());
    }

    protected ISqlJetVdbeMem getValueMem(int field) throws SqlJetException {
        if (!checkField(field))
            return null;
        final ISqlJetBtreeRecord r = getCachedRecord();
        if (null == r)
            return null;
        final List<ISqlJetVdbeMem> fields = r.getFields();
        if (null == fields)
            return null;
        return fields.get(field);
    }

    public Object getValue(int field) throws SqlJetException {
        if (isNull(field))
            return null;
        switch (getFieldType(field)) {
        case INTEGER:
            return getInteger(field);
        case FLOAT:
            return getFloat(field);
        case TEXT:
            return getString(field);
        case BLOB:
            return getBlob(field);
        case NULL:
            break;
        default:
            break;
        }
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeTable#getFieldsCount
     * ()
     */
    public int getFieldsCount() throws SqlJetException {
        final ISqlJetBtreeRecord r = getCachedRecord();
        if (null == r)
            return 0;
        return r.getFieldsCount();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeTable#isNull(int)
     */
    public boolean isNull(int field) throws SqlJetException {
        final ISqlJetVdbeMem value = getValueMem(field);
        if (null == value)
            return true;
        return value.isNull();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeTable#getString(int)
     */
    public String getString(int field) throws SqlJetException {
        if (isNull(field))
            return null;
        return SqlJetUtility
                .toString(getValueMem(field).valueText(getEncoding()), ISqlJetBtreeRecord.INTERNAL_ENCODING);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeTable#getInteger
     * (int)
     */
    public long getInteger(int field) throws SqlJetException {
        if (isNull(field))
            return 0;
        return getValueMem(field).intValue();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeTable#getReal(int)
     */
    public double getFloat(int field) throws SqlJetException {
        if (isNull(field))
            return 0;
        return getValueMem(field).realValue();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeTable#getFieldType
     * (int)
     */
    public SqlJetValueType getFieldType(int field) throws SqlJetException {
        if (isNull(field))
            return SqlJetValueType.NULL;
        return getValueMem(field).getType();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeTable#getBlob(int)
     */
    public ByteBuffer getBlob(int field) throws SqlJetException {
        if (isNull(field))
            return null;
        return getValueMem(field).valueBlob();
    }

    /* (non-Javadoc)
     * @see org.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeTable#getValues()
     */
    public Object[] getValues() throws SqlJetException {
        final int fieldsCount = getFieldsCount();
        final Object[] values = new Object[fieldsCount]; 
        for(int i=0; i<fieldsCount; i++) {
            values[i] = getValue(i);
        }
        return values;
    }
    
    protected boolean verifySchemaCookie(boolean throwIfStale) throws SqlJetException {
        return db.getMeta().verifySchemaCookie(throwIfStale);
    }

}
