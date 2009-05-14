/**
 * SqlJetDataBase.java
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

import java.io.File;
import java.util.EnumSet;
import java.util.Set;

import org.tmatesoft.sqljet.core.SqlJetEncoding;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.internal.ISqlJetBtree;
import org.tmatesoft.sqljet.core.internal.ISqlJetDbHandle;
import org.tmatesoft.sqljet.core.internal.SqlJetBtreeFlags;
import org.tmatesoft.sqljet.core.internal.SqlJetFileOpenPermission;
import org.tmatesoft.sqljet.core.internal.SqlJetFileType;
import org.tmatesoft.sqljet.core.internal.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.internal.btree.SqlJetBtree;
import org.tmatesoft.sqljet.core.internal.db.SqlJetDbHandle;
import org.tmatesoft.sqljet.core.internal.schema.ISqlJetSchema;
import org.tmatesoft.sqljet.core.internal.schema.SqlJetSchema;
import org.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeDataTable;
import org.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeIndexTable;
import org.tmatesoft.sqljet.core.internal.table.SqlJetBtreeDataTable;
import org.tmatesoft.sqljet.core.internal.table.SqlJetBtreeIndexTable;

/**
 * Connection to database.
 * 
 * @author TMate Software Ltd.
 * @author Sergey Scherbina (sergey.scherbina@gmail.com)
 * @author Dmitry Stadnik (dtrace@seznam.cz)
 */
public class SqlJetDb {

    private static final EnumSet<SqlJetBtreeFlags> READ_FLAGS = EnumSet.of(SqlJetBtreeFlags.READONLY);
    private static final EnumSet<SqlJetFileOpenPermission> READ_PERMISSIONS = EnumSet
            .of(SqlJetFileOpenPermission.READONLY);
    private static final EnumSet<SqlJetBtreeFlags> WRITE_FLAGS = EnumSet.of(SqlJetBtreeFlags.READWRITE,
            SqlJetBtreeFlags.CREATE);
    private static final EnumSet<SqlJetFileOpenPermission> WRITE_PREMISSIONS = EnumSet.of(
            SqlJetFileOpenPermission.READWRITE, SqlJetFileOpenPermission.CREATE);

    private final boolean write;
    private ISqlJetDbHandle db;
    private ISqlJetBtree btree;
    private ISqlJetSchema schema;

    private boolean transaction = false;

    public static class SqlJetDataTable extends SqlJetTable {
        SqlJetDataTable(ISqlJetBtreeDataTable dataTable) {
            super(dataTable);
        }
    }

    public static class SqlJetIndexTable extends SqlJetIndex {
        SqlJetIndexTable(ISqlJetBtreeIndexTable indexTable) {
            super(indexTable);
        }
    }

    /**
     * Create connection to data base.
     * 
     * @param file path to data base
     * @param write if true then allow data modification
     * @throws SqlJetException
     */
    protected SqlJetDb(File file, boolean write) throws SqlJetException {
        this.write = write;
        db = new SqlJetDbHandle();
        btree = new SqlJetBtree();
        btree.open(file, db, write ? WRITE_FLAGS : READ_FLAGS, SqlJetFileType.MAIN_DB, write ? WRITE_PREMISSIONS
                : READ_PERMISSIONS);
        runWithLock(new ISqlJetRunnableWithLock() {

            public Object runWithLock() throws SqlJetException {
                btree.enter();
                try {
                    schema = new SqlJetSchema(btree);
                    db.setEnc(schema.getMeta().getEncoding());
                } finally {
                    btree.leave();
                }
                return null;
            }
        });
    }

    /**
     * Open connection to data base.
     * 
     * @param file path to data base
     * @param write if true then allow data modification
     * @throws SqlJetException
     */
    public static SqlJetDb open(File file, boolean write) throws SqlJetException {
        return new SqlJetDb(file, write);
    }

    /**
     * Close connection to data base.
     * 
     * @throws SqlJetException
     */
    public void close() throws SqlJetException {
        runWithLock(new ISqlJetRunnableWithLock() {

            public Object runWithLock() throws SqlJetException {
                btree.close();
                return null;
            }
        });
    }

    /**
     * Do some actions with locking data base.
     * 
     * @param op
     * @return
     * @throws SqlJetException
     */
    public Object runWithLock(ISqlJetRunnableWithLock op) throws SqlJetException {
        Object result = null;
        db.getMutex().enter();
        try {
            result = op.runWithLock();
        } finally {
            db.getMutex().leave();
        }
        return result;
    }

    /**
     * Check write access to data base.
     * 
     * @return true if modification is allowed
     */
    public boolean isWrite() {
        return write;
    }

    /**
     * Get data base's encoding.
     * 
     * @return data base's encoding
     */
    public SqlJetEncoding getEncoding() {
        return db.getEnc();
    }

    /**
     * Get all tables names from data base.
     * 
     * @return set with all tables names
     */
    public Set<String> getTableNames() {
        return schema.getTableNames();
    }

    /**
     * Get all indexes names which are linked with given table.
     * 
     * @param tableName name of table
     * @return set of indexes names wich are linked with table
     */
    public Set<String> getIndexNames(String tableName) {
        return schema.getIndexNames(tableName);
    }

    /**
     * Open table.
     * 
     * @param tableName table name
     * @return opened table
     * @throws SqlJetException
     */
    public SqlJetTable openTable(final String tableName) throws SqlJetException {
        return (SqlJetTable) runWithLock(new ISqlJetRunnableWithLock() {

            public Object runWithLock() throws SqlJetException {
                return new SqlJetDataTable(new SqlJetBtreeDataTable(schema, tableName, write));
            }
        });
    }

    /**
     * Open index.
     * 
     * @param indexName index name
     * @return opened index
     * @throws SqlJetException
     */
    public SqlJetIndex openIndex(final String indexName) throws SqlJetException {
        return (SqlJetIndexTable) runWithLock(new ISqlJetRunnableWithLock() {

            public Object runWithLock() throws SqlJetException {
                return new SqlJetIndexTable(new SqlJetBtreeIndexTable(schema, indexName, write));
            }
        });
    }

    /**
     * Begin transaction if write access is allowed.
     * 
     * @throws SqlJetException
     */
    public void beginTransaction() throws SqlJetException {
        if (write) {
            btree.beginTrans(SqlJetTransactionMode.WRITE);
            transaction = true;
        }
    }

    /**
     * Commit transaction.
     * 
     * @throws SqlJetException
     */
    public void commit() throws SqlJetException {
        if (write && transaction) {
            btree.commit();
            transaction = false;
        }
    }

    /**
     * Rollback transaction.
     * 
     * @throws SqlJetException
     */
    public void rollback() throws SqlJetException {
        if (write && transaction) {
            btree.rollback();
            transaction = false;
        }
    }

}
