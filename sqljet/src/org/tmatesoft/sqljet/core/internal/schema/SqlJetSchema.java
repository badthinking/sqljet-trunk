/**
 * SqlJetSchema.java
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
 *
 * For information on how to redistribute this software under
 * the terms of a license other than GNU General Public License
 * contact TMate Software at support@sqljet.com
 */
package org.tmatesoft.sqljet.core.internal.schema;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CharStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.tree.CommonTree;
import org.tmatesoft.sqljet.core.SqlJetErrorCode;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.internal.ISqlJetBtree;
import org.tmatesoft.sqljet.core.internal.ISqlJetBtreeCursor;
import org.tmatesoft.sqljet.core.internal.ISqlJetDbHandle;
import org.tmatesoft.sqljet.core.internal.SqlJetBtreeTableCreateFlags;
import org.tmatesoft.sqljet.core.internal.SqlJetUtility;
import org.tmatesoft.sqljet.core.internal.lang.SqlLexer;
import org.tmatesoft.sqljet.core.internal.lang.SqlParser;
import org.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeRecord;
import org.tmatesoft.sqljet.core.internal.table.SqlJetBtreeIndexTable;
import org.tmatesoft.sqljet.core.internal.table.SqlJetBtreeTable;
import org.tmatesoft.sqljet.core.internal.vdbe.SqlJetBtreeRecord;
import org.tmatesoft.sqljet.core.schema.ISqlJetColumnConstraint;
import org.tmatesoft.sqljet.core.schema.ISqlJetColumnDef;
import org.tmatesoft.sqljet.core.schema.ISqlJetColumnPrimaryKey;
import org.tmatesoft.sqljet.core.schema.ISqlJetColumnUnique;
import org.tmatesoft.sqljet.core.schema.ISqlJetIndexDef;
import org.tmatesoft.sqljet.core.schema.ISqlJetIndexedColumn;
import org.tmatesoft.sqljet.core.schema.ISqlJetSchema;
import org.tmatesoft.sqljet.core.schema.ISqlJetTableConstraint;
import org.tmatesoft.sqljet.core.schema.ISqlJetTableDef;
import org.tmatesoft.sqljet.core.schema.ISqlJetTablePrimaryKey;
import org.tmatesoft.sqljet.core.schema.ISqlJetTableUnique;

/**
 * @author TMate Software Ltd.
 * @author Sergey Scherbina (sergey.scherbina@gmail.com)
 * @author Dmitry Stadnik (dtrace@seznam.cz)
 */
public class SqlJetSchema implements ISqlJetSchema {

    /**
     * 
     */
    private static final String CREATE_TABLE_SQLITE_SEQUENCE = "CREATE TABLE sqlite_sequence(name,seq)";

    /**
     * 
     */
    private static final String SQLITE_SEQUENCE = "SQLITE_SEQUENCE";

    private static final Set<SqlJetBtreeTableCreateFlags> BTREE_CREATE_TABLE_FLAGS = SqlJetUtility.of(
            SqlJetBtreeTableCreateFlags.INTKEY, SqlJetBtreeTableCreateFlags.LEAFDATA);

    private static final Set<SqlJetBtreeTableCreateFlags> BTREE_CREATE_INDEX_FLAGS = SqlJetUtility
            .of(SqlJetBtreeTableCreateFlags.ZERODATA);

    private static final int TYPE_FIELD = 0;
    private static final int NAME_FIELD = 1;
    private static final int TABLE_FIELD = 2;
    private static final int PAGE_FIELD = 3;
    private static final int SQL_FIELD = 4;

    private static final String TABLE_TYPE = "table";
    private static final String INDEX_TYPE = "index";

    private final ISqlJetDbHandle db;
    private final ISqlJetBtree btree;

    private final Map<String, ISqlJetTableDef> tableDefs = new TreeMap<String, ISqlJetTableDef>(
            String.CASE_INSENSITIVE_ORDER);
    private final Map<String, ISqlJetIndexDef> indexDefs = new TreeMap<String, ISqlJetIndexDef>(
            String.CASE_INSENSITIVE_ORDER);

    public SqlJetSchema(ISqlJetDbHandle db, ISqlJetBtree btree) throws SqlJetException {
        this.db = db;
        this.btree = btree;
        init();
    }

    private void init() throws SqlJetException {
        if (db.getOptions().getSchemaVersion() == 0)
            return;
        final SqlJetBtreeTable table = new SqlJetBtreeTable(db, btree, ISqlJetDbHandle.MASTER_ROOT, false, false);
        try {
            table.lock();
            try {
                readShema(table);
            } finally {
                table.unlock();
            }
        } finally {
            table.close();
        }
    }

    public ISqlJetDbHandle getDb() {
        return db;
    }

    public ISqlJetBtree getBtree() {
        return btree;
    }

    public Set<String> getTableNames() throws SqlJetException {
        db.getMutex().enter();
        try {
            return tableDefs.keySet();
        } finally {
            db.getMutex().leave();
        }
    }

    public ISqlJetTableDef getTable(String name) throws SqlJetException {
        db.getMutex().enter();
        try {
            return tableDefs.get(name);
        } finally {
            db.getMutex().leave();
        }
    }

    public Set<String> getIndexNames() throws SqlJetException {
        db.getMutex().enter();
        try {
            return indexDefs.keySet();
        } finally {
            db.getMutex().leave();
        }
    }

    public ISqlJetIndexDef getIndex(String name) throws SqlJetException {
        db.getMutex().enter();
        try {
            return indexDefs.get(name);
        } finally {
            db.getMutex().leave();
        }
    }

    public Set<ISqlJetIndexDef> getIndexes(String tableName) throws SqlJetException {
        db.getMutex().enter();
        try {
            Set<ISqlJetIndexDef> result = new HashSet<ISqlJetIndexDef>();
            for (ISqlJetIndexDef index : indexDefs.values()) {
                if (index.getTableName().equals(tableName)) {
                    result.add(index);
                }
            }
            return Collections.unmodifiableSet(result);
        } finally {
            db.getMutex().leave();
        }
    }

    private void readShema(SqlJetBtreeTable table) throws SqlJetException {
        for (ISqlJetBtreeRecord record = table.getRecord(); !table.eof(); table.next(), record = table.getRecord()) {
            final String type = SqlJetUtility.trim(record.getStringField(TYPE_FIELD, db.getOptions().getEncoding()));
            if (null == type) {
                continue;
            }
            final String name = SqlJetUtility.trim(record.getStringField(NAME_FIELD, db.getOptions().getEncoding()));
            if (null == name) {
                continue;
            }
            final int page = (int) record.getIntField(PAGE_FIELD);
            if (0 == page) {
                continue;
            }

            if (TABLE_TYPE.equals(type)) {
                String sql = record.getStringField(SQL_FIELD, db.getOptions().getEncoding());
                // System.err.println(sql);
                final CommonTree ast = parseTable(sql);
                final SqlJetTableDef tableDef = new SqlJetTableDef(ast, page);
                if (!name.equals(tableDef.getName())) {
                    throw new SqlJetException(SqlJetErrorCode.CORRUPT);
                }
                tableDef.setRowId(table.getCursor().getKeySize());
                tableDefs.put(name, tableDef);
            } else if (INDEX_TYPE.equals(type)) {
                final String tableName = SqlJetUtility.trim(record.getStringField(TABLE_FIELD, db.getOptions()
                        .getEncoding()));
                if (null == type) {
                    throw new SqlJetException(SqlJetErrorCode.CORRUPT);
                }
                final String sql = record.getStringField(SQL_FIELD, db.getOptions().getEncoding());
                if (null != sql) {
                    // System.err.println(sql);
                    final CommonTree ast = parseIndex(sql);
                    final SqlJetIndexDef indexDef = new SqlJetIndexDef(ast, page);
                    if (!name.equals(indexDef.getName())) {
                        throw new SqlJetException(SqlJetErrorCode.CORRUPT);
                    }
                    if (!tableName.equals(indexDef.getTableName())) {
                        throw new SqlJetException(SqlJetErrorCode.CORRUPT);
                    }
                    indexDef.setRowId(table.getCursor().getKeySize());
                    indexDefs.put(name, indexDef);
                } else {
                    ISqlJetIndexDef indexDef = new SqlJetBaseIndexDef(name, tableName, page);
                    indexDefs.put(name, indexDef);
                }
            }
        }
    }

    private CommonTree parseTable(String sql) throws SqlJetException {
        try {
            CharStream chars = new ANTLRStringStream(sql);
            SqlLexer lexer = new SqlLexer(chars);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            SqlParser parser = new SqlParser(tokens);
            return (CommonTree) parser.create_table_stmt().getTree();
        } catch (RecognitionException re) {
            throw new SqlJetException(SqlJetErrorCode.ERROR, "Invalid sql statement: " + sql);
        }
    }

    private CommonTree parseIndex(String sql) throws SqlJetException {
        try {
            CharStream chars = new ANTLRStringStream(sql);
            SqlLexer lexer = new SqlLexer(chars);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            SqlParser parser = new SqlParser(tokens);
            return (CommonTree) parser.create_index_stmt().getTree();
        } catch (RecognitionException re) {
            throw new SqlJetException(SqlJetErrorCode.ERROR, "Invalid sql statement: " + sql);
        }
    }

    @Override
    public String toString() {
        db.getMutex().enter();
        try {
            StringBuffer buffer = new StringBuffer();
            buffer.append("Tables:\n");
            for (ISqlJetTableDef tableDef : tableDefs.values()) {
                buffer.append(tableDef.toString());
                buffer.append('\n');
            }
            buffer.append("Indexes:\n");
            for (ISqlJetIndexDef indexDef : indexDefs.values()) {
                buffer.append(indexDef.toString());
                buffer.append('\n');
            }
            return buffer.toString();
        } finally {
            db.getMutex().leave();
        }
    }

    public ISqlJetTableDef createTable(String sql) throws SqlJetException {
        db.getMutex().enter();
        try {
            return createTableSafe(sql);
        } finally {
            db.getMutex().leave();
        }
    }

    private ISqlJetTableDef createTableSafe(String sql) throws SqlJetException {

        final CommonTree ast = parseTable(sql);

        final SqlJetTableDef tableDef = new SqlJetTableDef(ast, 0);
        if (null == tableDef.getName())
            throw new SqlJetException(SqlJetErrorCode.ERROR);
        final String tableName = tableDef.getName().trim();
        if ("".equals(tableName))
            throw new SqlJetException(SqlJetErrorCode.ERROR);

        if (tableDefs.containsKey(tableName))
            throw new SqlJetException(SqlJetErrorCode.ERROR, "Table \"" + tableName + "\" exists already");

        final List<ISqlJetColumnDef> columns = tableDef.getColumns();
        if (null == columns || 0 == columns.size())
            throw new SqlJetException(SqlJetErrorCode.ERROR);

        final SqlJetBtreeTable schemaTable = new SqlJetBtreeTable(db, btree, ISqlJetDbHandle.MASTER_ROOT, true, false);

        try {

            schemaTable.lock();

            try {

                db.getOptions().changeSchemaVersion();

                final int page = btree.createTable(BTREE_CREATE_TABLE_FLAGS);
                final ISqlJetBtreeRecord record = SqlJetBtreeRecord.getRecord(db.getOptions().getEncoding(),
                        TABLE_TYPE, tableName, tableName, page, tableDef.toSQL());
                final ByteBuffer pData = record.getRawRecord();
                schemaTable.getCursor().insert(null, schemaTable.newRowId(), pData, pData.remaining(), 0, false);

                addConstraints(schemaTable, tableDef);

                tableDef.setPage(page);
                tableDef.setRowId(schemaTable.getCursor().getKeySize());
                tableDefs.put(tableName, tableDef);
                return tableDef;

            } finally {
                schemaTable.unlock();
            }

        } finally {
            schemaTable.close();
        }

    }

    /**
     * @param schemaTable
     * @param tableDef
     * @throws SqlJetException
     */
    private void addConstraints(SqlJetBtreeTable schemaTable, final SqlJetTableDef tableDef) throws SqlJetException {

        final String tableName = tableDef.getName().trim();
        final List<ISqlJetColumnDef> columns = tableDef.getColumns();
        int i = 0;

        for (final ISqlJetColumnDef column : columns) {
            final List<ISqlJetColumnConstraint> constraints = column.getConstraints();
            if (null == constraints)
                continue;
            for (final ISqlJetColumnConstraint constraint : constraints) {
                if (constraint instanceof ISqlJetColumnPrimaryKey) {
                    final ISqlJetColumnPrimaryKey pk = (ISqlJetColumnPrimaryKey) constraint;
                    if (!column.hasExactlyIntegerType()) {
                        if (pk.isAutoincremented()) {
                            throw new SqlJetException(SqlJetErrorCode.ERROR,
                                    "AUTOINCREMENT is allowed only for INTEGER PRIMARY KEY fields");
                        }
                        createAutoIndex(schemaTable, tableName, SqlJetBtreeTable.generateAutoIndexName(tableName, ++i));
                    } else if (pk.isAutoincremented()) {
                        checkSequenceTable(schemaTable);
                    }
                } else if (constraint instanceof ISqlJetColumnUnique) {
                    createAutoIndex(schemaTable, tableName, SqlJetBtreeTable.generateAutoIndexName(tableName, ++i));
                }
            }
        }

        final List<ISqlJetTableConstraint> constraints = tableDef.getConstraints();
        if (null != constraints) {
            for (final ISqlJetTableConstraint constraint : constraints) {
                if (constraint instanceof ISqlJetTablePrimaryKey) {
                    boolean b = false;
                    final ISqlJetTablePrimaryKey pk = (ISqlJetTablePrimaryKey) constraint;
                    if (pk.getColumns().size() == 1) {
                        final String n = pk.getColumns().get(0);
                        final ISqlJetColumnDef c = tableDef.getColumn(n);
                        b = c != null && c.hasExactlyIntegerType();
                    }
                    if (!b) {
                        createAutoIndex(schemaTable, tableName, SqlJetBtreeTable.generateAutoIndexName(tableName, ++i));
                    }
                } else if (constraint instanceof ISqlJetTableUnique) {
                    createAutoIndex(schemaTable, tableName, SqlJetBtreeTable.generateAutoIndexName(tableName, ++i));
                }
            }
        }
    }

    /**
     * @param schemaTable
     * @throws SqlJetException
     */
    private void checkSequenceTable(SqlJetBtreeTable schemaTable) throws SqlJetException {
        if (getTableNames().contains(SQLITE_SEQUENCE)) {
            return;
        }
        createTableSafe(CREATE_TABLE_SQLITE_SEQUENCE);
    }

    /**
     * @param schemaTable
     * @param generateAutoIndexName
     * 
     * @throws SqlJetException
     */
    private void createAutoIndex(SqlJetBtreeTable schemaTable, String tableName, String autoIndexName)
            throws SqlJetException {
        final int page = btree.createTable(BTREE_CREATE_INDEX_FLAGS);
        final ISqlJetBtreeRecord record = SqlJetBtreeRecord.getRecord(db.getOptions().getEncoding(), INDEX_TYPE,
                autoIndexName, tableName, page, null);
        final ByteBuffer pData = record.getRawRecord();
        schemaTable.getCursor().insert(null, schemaTable.newRowId(), pData, pData.remaining(), 0, false);
        final SqlJetBaseIndexDef indexDef = new SqlJetBaseIndexDef(autoIndexName, tableName, page);
        indexDef.setRowId(schemaTable.getCursor().getKeySize());
        indexDefs.put(autoIndexName, indexDef);
    }

    public ISqlJetIndexDef createIndex(String sql) throws SqlJetException {
        db.getMutex().enter();
        try {
            return createIndexSafe(sql);
        } finally {
            db.getMutex().leave();
        }
    }

    private ISqlJetIndexDef createIndexSafe(String sql) throws SqlJetException {

        final CommonTree ast = parseIndex(sql);

        final SqlJetIndexDef indexDef = new SqlJetIndexDef(ast, 0);

        if (null == indexDef.getName())
            throw new SqlJetException(SqlJetErrorCode.ERROR);
        final String indexName = indexDef.getName().trim();
        if ("".equals(indexName))
            throw new SqlJetException(SqlJetErrorCode.ERROR);

        if (indexDefs.containsKey(indexName))
            throw new SqlJetException(SqlJetErrorCode.ERROR, "Index \"" + indexName + "\" exists already");

        if (null == indexDef.getTableName())
            throw new SqlJetException(SqlJetErrorCode.ERROR);
        final String tableName = indexDef.getTableName().trim();
        if ("".equals(tableName))
            throw new SqlJetException(SqlJetErrorCode.ERROR);

        final List<ISqlJetIndexedColumn> columns = indexDef.getColumns();
        if (null == columns)
            throw new SqlJetException(SqlJetErrorCode.ERROR);

        final ISqlJetTableDef tableDef = getTable(tableName);
        if (null == tableDef)
            throw new SqlJetException(SqlJetErrorCode.ERROR);

        for (final ISqlJetIndexedColumn column : columns) {
            if (null == column.getName())
                throw new SqlJetException(SqlJetErrorCode.ERROR);
            final String columnName = column.getName().trim();
            if ("".equals(columnName))
                throw new SqlJetException(SqlJetErrorCode.ERROR);
            if (null == tableDef.getColumn(columnName))
                throw new SqlJetException(SqlJetErrorCode.ERROR, "Column \"" + columnName + "\" not found in table \""
                        + tableName + "\"");
        }

        final SqlJetBtreeTable schemaTable = new SqlJetBtreeTable(db, btree, ISqlJetDbHandle.MASTER_ROOT, true, false);

        try {

            schemaTable.lock();

            try {

                db.getOptions().changeSchemaVersion();

                final int page = btree.createTable(BTREE_CREATE_INDEX_FLAGS);
                final ISqlJetBtreeRecord record = SqlJetBtreeRecord.getRecord(db.getOptions().getEncoding(),
                        INDEX_TYPE, indexName, tableName, page, indexDef.toSQL());
                final ByteBuffer pData = record.getRawRecord();
                schemaTable.getCursor().insert(null, schemaTable.newRowId(), pData, pData.remaining(), 0, false);

                indexDef.setPage(page);
                indexDef.setRowId(schemaTable.getCursor().getKeySize());
                indexDefs.put(indexName, indexDef);

                final SqlJetBtreeIndexTable indexTable = new SqlJetBtreeIndexTable(this, indexDef.getName(), true);
                try {
                    indexTable.reindex(this);
                } finally {
                    indexTable.close();
                }
                return indexDef;

            } finally {
                schemaTable.unlock();
            }

        } finally {
            schemaTable.close();
        }
    }

    public void dropTable(String tableName) throws SqlJetException {
        db.getMutex().enter();
        try {
            dropTableSafe(tableName);
        } finally {
            db.getMutex().leave();
        }
    }

    private void dropTableSafe(String tableName) throws SqlJetException {

        if (null == tableName || "".equals(tableName.trim()))
            throw new SqlJetException(SqlJetErrorCode.MISUSE, "Table name must be not empty");

        if (!tableDefs.containsKey(tableName))
            throw new SqlJetException(SqlJetErrorCode.MISUSE, "Table not found: " + tableName);
        final SqlJetTableDef tableDef = (SqlJetTableDef) tableDefs.get(tableName);

        dropTableIndexes(tableDef);

        final SqlJetBtreeTable schemaTable = new SqlJetBtreeTable(db, btree, ISqlJetDbHandle.MASTER_ROOT, true, false);

        try {

            schemaTable.lock();

            try {

                db.getOptions().changeSchemaVersion();

                final ISqlJetBtreeCursor cursor = schemaTable.getCursor();
                if (cursor.moveTo(null, tableDef.getRowId(), false) == -1)
                    schemaTable.next();
                if (!TABLE_TYPE.equals(schemaTable.getString(TYPE_FIELD)))
                    throw new SqlJetException(SqlJetErrorCode.CORRUPT);
                final String n = schemaTable.getString(NAME_FIELD);
                if (null == n || !tableName.equals(n))
                    throw new SqlJetException(SqlJetErrorCode.CORRUPT);
                cursor.delete();

            } finally {
                schemaTable.unlock();
            }

        } finally {
            schemaTable.close();
        }

        btree.dropTable(tableDef.getPage());

        tableDefs.remove(tableName);

    }

    /**
     * @param schemaTable
     * @param tableDef
     * @throws SqlJetException
     */
    private void dropTableIndexes(SqlJetTableDef tableDef) throws SqlJetException {
        final String tableName = tableDef.getName().trim();
        final Iterator<Map.Entry<String, ISqlJetIndexDef>> iterator = indexDefs.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<String, ISqlJetIndexDef> indexDefEntry = iterator.next();
            final String indexName = indexDefEntry.getKey();
            final ISqlJetIndexDef indexDef = indexDefEntry.getValue();
            if (indexDef.getTableName().trim().equals(tableName)) {
                if(doDropIndex(indexName, true, false)){
                    iterator.remove();
                }
            }
        }
    }

    /**
     * @param schemaTable
     * @param name
     * @param generateAutoIndexName
     * @throws SqlJetException
     */
    private boolean doDropIndex(String indexName, boolean allowAutoIndex, boolean throwIfFial) throws SqlJetException {

        if (!indexDefs.containsKey(indexName)) {
            if (throwIfFial)
                throw new SqlJetException(SqlJetErrorCode.MISUSE);
            return false;
        }
        final SqlJetBaseIndexDef indexDef = (SqlJetBaseIndexDef) indexDefs.get(indexName);

        final SqlJetBtreeTable schemaTable = new SqlJetBtreeTable(db, btree, ISqlJetDbHandle.MASTER_ROOT, true, false);

        try {

            schemaTable.lock();

            try {

                final ISqlJetBtreeCursor cursor = schemaTable.getCursor();
                if (cursor.moveTo(null, indexDef.getRowId(), false) == -1)
                    schemaTable.next();
                if (!INDEX_TYPE.equals(schemaTable.getString(TYPE_FIELD))) {
                    if (throwIfFial)
                        throw new SqlJetException(SqlJetErrorCode.INTERNAL);
                    return false;
                }
                final String n = schemaTable.getString(NAME_FIELD);
                if (null == n || !indexName.equals(n)) {
                    if (throwIfFial)
                        throw new SqlJetException(SqlJetErrorCode.INTERNAL);
                    return false;
                }

                if (!allowAutoIndex && schemaTable.isNull(SQL_FIELD)) {
                    if (throwIfFial)
                        throw new SqlJetException(SqlJetErrorCode.MISUSE);
                    return false;
                }

                cursor.delete();

            } finally {
                schemaTable.unlock();
            }

        } finally {
            schemaTable.close();
        }

        btree.dropTable(indexDef.getPage());

        return true;

    }

    public void dropIndex(String indexName) throws SqlJetException {
        db.getMutex().enter();
        try {
            dropIndexSafe(indexName);
        } finally {
            db.getMutex().leave();
        }
    }

    private void dropIndexSafe(String indexName) throws SqlJetException {

        if (null == indexName || "".equals(indexName.trim()))
            throw new SqlJetException(SqlJetErrorCode.MISUSE, "Index name must be not empty");

        if (!indexDefs.containsKey(indexName))
            throw new SqlJetException(SqlJetErrorCode.MISUSE, "Index not found: " + indexName);

        if (doDropIndex(indexName, false, true)) {
            db.getOptions().changeSchemaVersion();
            indexDefs.remove(indexName);
        }

    }
}
