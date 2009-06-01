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
 */
package org.tmatesoft.sqljet.core.internal.schema;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CharStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.tree.CommonTree;
import org.tmatesoft.sqljet.core.SqlJetErrorCode;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.internal.ISqlJetBtree;
import org.tmatesoft.sqljet.core.internal.ISqlJetDbHandle;
import org.tmatesoft.sqljet.core.internal.SqlJetBtreeTableCreateFlags;
import org.tmatesoft.sqljet.core.internal.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.internal.SqlJetUtility;
import org.tmatesoft.sqljet.core.internal.lang.SqlLexer;
import org.tmatesoft.sqljet.core.internal.lang.SqlParser;
import org.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeRecord;
import org.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeTable;
import org.tmatesoft.sqljet.core.internal.table.SqlJetBtreeTable;
import org.tmatesoft.sqljet.core.internal.vdbe.SqlJetBtreeRecord;
import org.tmatesoft.sqljet.core.schema.ISqlJetIndexDef;
import org.tmatesoft.sqljet.core.schema.ISqlJetSchema;
import org.tmatesoft.sqljet.core.schema.ISqlJetTableDef;

/**
 * @author TMate Software Ltd.
 * @author Sergey Scherbina (sergey.scherbina@gmail.com)
 * @author Dmitry Stadnik (dtrace@seznam.cz)
 */
public class SqlJetSchema implements ISqlJetSchema {

    private static final EnumSet<SqlJetBtreeTableCreateFlags> BTREE_CREATE_TABLE_FLAGS = EnumSet.of(
            SqlJetBtreeTableCreateFlags.INTKEY, SqlJetBtreeTableCreateFlags.LEAFDATA);

    private static final int TYPE_FIELD = 0;
    private static final int NAME_FIELD = 1;
    private static final int TABLE_FIELD = 2;
    private static final int PAGE_FIELD = 3;
    private static final int SQL_FIELD = 4;

    private static final String TABLE_TYPE = "table";
    private static final String INDEX_TYPE = "index";

    private final ISqlJetDbHandle db;
    private final ISqlJetBtree btree;

    private final Map<String, ISqlJetTableDef> tableDefs = new HashMap<String, ISqlJetTableDef>();
    private final Map<String, ISqlJetIndexDef> indexDefs = new HashMap<String, ISqlJetIndexDef>();

    public SqlJetSchema(ISqlJetDbHandle db, ISqlJetBtree btree) throws SqlJetException {
        this.db = db;
        this.btree = btree;
        init();
    }

    private void init() throws SqlJetException {
        ISqlJetBtreeTable table = new SqlJetBtreeTable(db, btree, ISqlJetDbHandle.MASTER_ROOT, false, false);
        try {
            readShema(table);
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

    public Set<String> getTableNames() {
        return tableDefs.keySet();
    }

    public ISqlJetTableDef getTable(String name) {
        return tableDefs.get(name);
    }

    public Set<String> getIndexNames() {
        return indexDefs.keySet();
    }

    public ISqlJetIndexDef getIndex(String name) {
        return indexDefs.get(name);
    }

    public Set<ISqlJetIndexDef> getIndexes(String tableName) {
        Set<ISqlJetIndexDef> result = new HashSet<ISqlJetIndexDef>();
        for (ISqlJetIndexDef index : indexDefs.values()) {
            if (index.getTableName().equals(tableName)) {
                result.add(index);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private void readShema(ISqlJetBtreeTable table) throws SqlJetException {
        for (ISqlJetBtreeRecord record = table.getRecord(); !table.eof(); table.next(), record = table.getRecord()) {
            final String type = SqlJetUtility.trim(record.getStringField(TYPE_FIELD, db.getEncoding()));
            if (null == type) {
                continue;
            }
            final String name = SqlJetUtility.trim(record.getStringField(NAME_FIELD, db.getEncoding()));
            if (null == name) {
                continue;
            }
            final int page = (int) record.getIntField(PAGE_FIELD);
            if (0 == page) {
                continue;
            }

            if (TABLE_TYPE.equals(type)) {
                String sql = record.getStringField(SQL_FIELD, db.getEncoding());
                // System.err.println(sql);
                CommonTree ast = parseTable(sql);
                ISqlJetTableDef tableDef = new SqlJetTableDef(ast, page);
                if (!name.equals(tableDef.getName())) {
                    throw new SqlJetException(SqlJetErrorCode.CORRUPT);
                }
                tableDefs.put(name, tableDef);
            } else if (INDEX_TYPE.equals(type)) {
                final String tableName = SqlJetUtility.trim(record.getStringField(TABLE_FIELD, db.getEncoding()));
                if (null == type) {
                    throw new SqlJetException(SqlJetErrorCode.CORRUPT);
                }
                if (record.getFieldsCount() < SQL_FIELD) {
                    String sql = record.getStringField(SQL_FIELD, db.getEncoding());
                    // System.err.println(sql);
                    CommonTree ast = parseIndex(sql);
                    ISqlJetIndexDef indexDef = new SqlJetIndexDef(ast, page);
                    if (!name.equals(indexDef.getName())) {
                        throw new SqlJetException(SqlJetErrorCode.CORRUPT);
                    }
                    if (!tableName.equals(indexDef.getTableName())) {
                        throw new SqlJetException(SqlJetErrorCode.CORRUPT);
                    }
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
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.schema.ISqlJetSchema#createTable(java.lang.
     * String)
     */
    public ISqlJetTableDef createTable(String sql) throws SqlJetException {

        final CommonTree ast = parseTable(sql);

        final SqlJetTableDef tableDef = new SqlJetTableDef(ast, 0);
        if (null == tableDef.getName())
            throw new SqlJetException(SqlJetErrorCode.ERROR);
        final String name = tableDef.getName().trim();
        if ("".equals(name))
            throw new SqlJetException(SqlJetErrorCode.ERROR);

        SqlJetBtreeTable table = new SqlJetBtreeTable(db, btree, ISqlJetDbHandle.MASTER_ROOT, true, false);

        try {

            table.lock();

            try {

                for (table.first(); !table.eof(); table.next()) {
                    if (TABLE_TYPE.equals(table.getString(TYPE_FIELD))) {
                        final String n = table.getString(NAME_FIELD);
                        if (null == n)
                            throw new SqlJetException(SqlJetErrorCode.CORRUPT);
                        if (name.equals(n))
                            throw new SqlJetException(SqlJetErrorCode.ERROR, "Table \"" + name + "\" exists already");
                    }
                }

                final int page = btree.createTable(BTREE_CREATE_TABLE_FLAGS);
                final ISqlJetBtreeRecord record = SqlJetBtreeRecord.getRecord(TABLE_TYPE, name, name, page, tableDef
                        .toSQL());
                final ByteBuffer pData = record.getRawRecord();
                table.getCursor().insert(null, table.newRowId(0), pData, pData.remaining(), 0, false);
                db.getMeta().changeSchemaCookie();

                tableDef.setPage(page);
                tableDefs.put(name, tableDef);
                return tableDef;

            } finally {
                table.unlock();
            }

        } finally {
            table.close();
        }

    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.schema.ISqlJetSchema#createIndex(java.lang.
     * String)
     */
    public ISqlJetIndexDef createIndex(String sql) throws SqlJetException {
        // TODO Auto-generated method stub
        return null;
    }

}
