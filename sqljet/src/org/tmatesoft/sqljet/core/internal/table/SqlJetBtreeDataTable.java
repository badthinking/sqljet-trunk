/**
 * SqlJetBtreeDataTable.java
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
package org.tmatesoft.sqljet.core.internal.table;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.tmatesoft.sqljet.core.SqlJetErrorCode;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.internal.SqlJetUtility;
import org.tmatesoft.sqljet.core.internal.schema.SqlJetSchema;
import org.tmatesoft.sqljet.core.internal.schema.SqlJetTableDef;
import org.tmatesoft.sqljet.core.internal.vdbe.SqlJetBtreeRecord;
import org.tmatesoft.sqljet.core.schema.ISqlJetColumnConstraint;
import org.tmatesoft.sqljet.core.schema.ISqlJetColumnDef;
import org.tmatesoft.sqljet.core.schema.ISqlJetColumnNotNull;
import org.tmatesoft.sqljet.core.schema.ISqlJetIndexDef;
import org.tmatesoft.sqljet.core.schema.ISqlJetIndexedColumn;
import org.tmatesoft.sqljet.core.schema.ISqlJetSchema;
import org.tmatesoft.sqljet.core.schema.ISqlJetTableDef;

/**
 * @author TMate Software Ltd.
 * @author Sergey Scherbina (sergey.scherbina@gmail.com)
 * 
 */
public class SqlJetBtreeDataTable extends SqlJetBtreeTable implements ISqlJetBtreeDataTable {

    final static private String[] rowIdNames = { "ROWID", "_ROWID_", "OID" };

    private SqlJetTableDef tableDef;
    private Map<String, ISqlJetIndexDef> indexesDefs;

    private Map<String, ISqlJetBtreeIndexTable> indexesTables;

    private enum Action {
        INSERT, UPDATE, DELETE
    };

    /**
     * Open data table by name.
     * 
     * @throws SqlJetException
     */
    public SqlJetBtreeDataTable(ISqlJetSchema schema, String tableName, boolean write) throws SqlJetException {
        super(((SqlJetSchema) schema).getDb(), ((SqlJetSchema) schema).getBtree(), ((SqlJetTableDef) schema
                .getTable(tableName)).getPage(), write, false);
        this.tableDef = (SqlJetTableDef) schema.getTable(tableName);
        openIndexes(schema);
    }

    /**
     * @param dataTable
     * @throws SqlJetException
     */
    public SqlJetBtreeDataTable(SqlJetBtreeDataTable dataTable) throws SqlJetException {
        super((SqlJetBtreeTable) dataTable);

        this.tableDef = dataTable.tableDef;
        this.indexesDefs = dataTable.indexesDefs;

        indexesTables = new HashMap<String, ISqlJetBtreeIndexTable>();
        for (Map.Entry<String, ISqlJetBtreeIndexTable> entry : dataTable.indexesTables.entrySet()) {
            indexesTables.put(entry.getKey(), new SqlJetBtreeIndexTable((SqlJetBtreeIndexTable) entry.getValue()));
        }

    }

    @Override
    public void close() throws SqlJetException {
        if (indexesTables != null) {
            for (String key : indexesTables.keySet()) {
                ISqlJetBtreeIndexTable table = indexesTables.get(key);
                table.close();
            }
        }
        super.close();
    }

    /**
     * Open all indexes
     * 
     * @throws SqlJetException
     * 
     */
    private void openIndexes(ISqlJetSchema schema) throws SqlJetException {
        indexesDefs = new HashMap<String, ISqlJetIndexDef>();
        indexesTables = new HashMap<String, ISqlJetBtreeIndexTable>();
        for (final ISqlJetIndexDef indexDef : schema.getIndexes(tableDef.getName())) {
            indexesDefs.put(indexDef.getName(), indexDef);
            final SqlJetBtreeIndexTable indexTable;
            if (indexDef.getColumns().size() > 0) {
                indexTable = new SqlJetBtreeIndexTable(schema, indexDef.getName(), this.write);
            } else {
                List<String> columns;
                if (tableDef.getTableIndexConstraint(indexDef.getName()) != null) {
                    columns = tableDef.getTableIndexConstraint(indexDef.getName()).getColumns();
                } else {
                    columns = new ArrayList<String>();
                    columns.add(tableDef.getColumnIndexConstraint(indexDef.getName()).getColumn().getName());
                }
                indexTable = new SqlJetBtreeIndexTable(schema, indexDef.getName(), columns, this.write);
            }
            indexesTables.put(indexDef.getName(), indexTable);
        }
    }

    /**
     * @return the tableDef
     */
    public ISqlJetTableDef getDefinition() {
        return tableDef;
    }

    /**
     * @return the indexesDefs
     */
    public Map<String, ISqlJetIndexDef> getIndexDefinitions() {
        return Collections.unmodifiableMap(indexesDefs);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.internal.btree.table.ISqlJetBtreeDataTable#
     * goToRow (int)
     */
    public boolean goToRow(long rowId) throws SqlJetException {
        lock();
        try {
            if (getRowId() == rowId)
                return true;
            return cursor.moveTo(null, rowId, false) == 0;
        } finally {
            unlock();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeDataTable#getKey()
     */
    public long getRowId() throws SqlJetException {
        lock();
        try {
            return cursor.getKeySize();
        } finally {
            unlock();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeDataTable#insert
     * (java.lang.Object[])
     */
    public long insert(Object... values) throws SqlJetException {
        return insertWithRowId(0, values);
    }

    /*
     * (non-Javadoc)
     * 
     * @seeorg.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeDataTable#
     * insertWithRowId(java.lang.Long, java.lang.Object[])
     */
    public long insertWithRowId(long rowId, Object[] values) throws SqlJetException {
        lock();
        try {
            final Object[] row = getValuesRow(values);
            if (rowId < 1) {
                rowId = getRowIdForRow(row, true);
            }
            doInsert(rowId, row);
            return rowId;
        } finally {
            unlock();
        }
    }

    /**
     * @param values
     * @return
     * @throws SqlJetException
     */
    private Object[] getValuesRow(Object... values) throws SqlJetException {
        final int columnsCount = tableDef.getColumns().size();
        final Object[] row = new Object[columnsCount];
        if (null != values && values.length != 0) {
            if (values.length > columnsCount) {
                throw new SqlJetException(SqlJetErrorCode.MISUSE, "Values count is more than columns in table");
            }
            final Object[] a = SqlJetUtility.adjustNumberTypes(values);
            System.arraycopy(a, 0, row, 0, a.length);
        }
        return row;
    }

    /**
     * @param row
     * @return
     * @throws SqlJetException
     */
    private long getRowIdForRow(final Object[] row, boolean required) throws SqlJetException {
        if (tableDef.isRowIdPrimaryKey()) {
            final int primaryKeyColumnNumber = tableDef.getColumnNumber(tableDef.getRowIdPrimaryKeyColumnName());
            if (primaryKeyColumnNumber == -1 || primaryKeyColumnNumber >= row.length)
                throw new SqlJetException(SqlJetErrorCode.ERROR);
            final Object rowIdParam = row[primaryKeyColumnNumber];
            if (null != rowIdParam) {
                if (rowIdParam instanceof Long) {
                    long rowId = (Long) rowIdParam;
                    if (rowId > 0) {
                        return rowId;
                    } else {
                        throw new SqlJetException(SqlJetErrorCode.MISUSE,
                                "INTEGER PRIMARY KEY column must be more than zero");
                    }
                } else {
                    throw new SqlJetException(SqlJetErrorCode.MISUSE,
                            "INTEGER PRIMARY KEY column must have only integer value");
                }
            }
        }
        if (!required) {
            return 0;
        } else {
            return newRowId();
        }
    }

    /**
     * @param row
     * @return
     * @throws SqlJetException
     */
    private void doInsert(final long rowId, final Object[] row) throws SqlJetException {
        final ByteBuffer pData;
        if (!tableDef.isRowIdPrimaryKey()) {
            pData = SqlJetBtreeRecord.getRecord(db.getOptions().getEncoding(), row).getRawRecord();
        } else {
            final int primaryKeyColumnNumber = tableDef.getColumnNumber(tableDef.getRowIdPrimaryKeyColumnName());
            if (primaryKeyColumnNumber == -1 || primaryKeyColumnNumber >= row.length)
                throw new SqlJetException(SqlJetErrorCode.ERROR);
            row[primaryKeyColumnNumber] = null;
            pData = SqlJetBtreeRecord.getRecord(db.getOptions().getEncoding(), row).getRawRecord();
            row[primaryKeyColumnNumber] = rowId;
        }
        doActionWithIndexes(Action.INSERT, rowId, row);
        cursor.insert(null, rowId, pData, pData.remaining(), 0, true);
        goToRow(rowId);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeDataTable#update
     * (long, java.lang.Object[])
     */
    public void update(long rowId, Object... values) throws SqlJetException {
        lock();
        try {
            if (rowId <= 0 || !goToRow(rowId))
                throw new SqlJetException(SqlJetErrorCode.MISUSE, "Incorrect rowId value: " + rowId);
            final Object[] row = getValuesRow(values);
            if (rowId < 1) {
                rowId = getRowIdForRow(row, false);
            }
            doUpdate(rowId, row);
        } finally {
            unlock();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeDataTable#updateCurrent
     * (java.lang.Object[])
     */
    public void updateCurrent(Object... values) throws SqlJetException {
        lock();
        try {
            if (eof())
                throw new SqlJetException(SqlJetErrorCode.MISUSE, "No current record");
            final Object[] row = getValuesRow(values);
            final long rowId = getRowIdForRow(row, false);
            doUpdate(rowId, row);
        } finally {
            unlock();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @seeorg.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeDataTable#
     * updateWithRowId(long, long, java.lang.Object[])
     */
    public long updateWithRowId(long rowId, long newRowId, Object... values) throws SqlJetException {
        lock();
        try {
            if (rowId <= 0 || !goToRow(rowId))
                throw new SqlJetException(SqlJetErrorCode.MISUSE, "Incorrect rowId value: " + rowId);
            final Object[] row = getValuesRow(values);
            if (newRowId < 1) {
                newRowId = getRowIdForRow(row, false);
            }
            doUpdate(newRowId > 0 ? newRowId : rowId, row);
            return newRowId;
        } finally {
            unlock();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @seeorg.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeDataTable#
     * updateCurrentWithRowId(long, java.lang.Object[])
     */
    public long updateCurrentWithRowId(long newRowId, Object... values) throws SqlJetException {
        lock();
        try {
            if (eof())
                throw new SqlJetException(SqlJetErrorCode.MISUSE, "No current record");
            final Object[] row = getValuesRow(values);
            if (newRowId < 1) {
                newRowId = getRowIdForRow(row, false);
            }
            doUpdate(newRowId, getValuesRow(values));
            return newRowId;
        } finally {
            unlock();
        }
    }

    /**
     * @param values
     * @throws SqlJetException
     */
    private void doUpdate(final long rowId, final Object[] row) throws SqlJetException {

        final long currentRowId = getRowId();
        final Object[] currentRow = getValues();
        long newRowId = 0 < rowId ? rowId : currentRowId;

        if (newRowId == currentRowId && Arrays.equals(row, currentRow))
            return;

        final ByteBuffer pData;
        if (!tableDef.isRowIdPrimaryKey()) {
            pData = SqlJetBtreeRecord.getRecord(db.getOptions().getEncoding(), row).getRawRecord();
        } else {
            final int primaryKeyColumnNumber = tableDef.getColumnNumber(tableDef.getRowIdPrimaryKeyColumnName());
            if (primaryKeyColumnNumber == -1 || primaryKeyColumnNumber >= row.length)
                throw new SqlJetException(SqlJetErrorCode.ERROR);
            row[primaryKeyColumnNumber] = null;
            pData = SqlJetBtreeRecord.getRecord(db.getOptions().getEncoding(), row).getRawRecord();
            row[primaryKeyColumnNumber] = newRowId;
        }
        doActionWithIndexes(Action.UPDATE, newRowId, row);
        cursor.delete();
        cursor.insert(null, newRowId, pData, pData.remaining(), 0, true);
        goToRow(newRowId);

    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeDataTable#delete
     * (long)
     */
    public void delete(long rowId) throws SqlJetException {
        lock();
        try {
            if (rowId <= 0 || !goToRow(rowId))
                throw new SqlJetException(SqlJetErrorCode.MISUSE, "Incorrect rowId value: " + rowId);
            doDelete();
        } finally {
            unlock();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeDataTable#delete()
     */
    public void delete() throws SqlJetException {
        lock();
        try {
            if (eof())
                throw new SqlJetException(SqlJetErrorCode.MISUSE, "No current record");
            doDelete();
        } finally {
            unlock();
        }
    }

    /**
     * @throws SqlJetException
     */
    private void doDelete() throws SqlJetException {
        doActionWithIndexes(Action.DELETE, 0);
        cursor.delete();
    }

    private boolean isRowIdExists(final long rowId) throws SqlJetException {
        final long current = getRowId();
        if (rowId == current)
            return true;
        final boolean exists = goToRow(rowId);
        if (current > 0) {
            goToRow(current);
        } else {
            first();
        }
        return exists;
    }

    /**
     * @param row
     * @return
     * @throws SqlJetException
     */
    private void doActionWithIndexes(Action action, long rowId, Object... row) throws SqlJetException {

        boolean existsRowId = false;

        long currentRowId = 0;
        Object[] currentRow = null;

        if (Action.INSERT != action) {
            currentRowId = getRowId();
            currentRow = getValues();
        }

        if (Action.INSERT == action) {
            existsRowId = isRowIdExists(rowId);
        } else if (Action.UPDATE == action) {
            if (currentRowId != rowId) {
                existsRowId = isRowIdExists(rowId);
            }
        }

        if (existsRowId) {
            throw new SqlJetException(SqlJetErrorCode.MISUSE, "Record with given ROWID already exists");
        }

        final Map<String, Object> fields = Action.DELETE != action ? getAsNamedFields(row) : null;
        final Map<String, Object[]> indexKeys = new HashMap<String, Object[]>();

        // check unique indexes
        if (Action.DELETE != action) {
            for (final ISqlJetIndexDef indexDef : indexesDefs.values()) {
                if (indexDef.isUnique() || tableDef.getColumnIndexConstraint(indexDef.getName()) != null
                        || tableDef.getTableIndexConstraint(indexDef.getName()) != null) {
                    final Object[] indexKey = getKeyForIndex(fields, indexDef);
                    indexKeys.put(indexDef.getName(), indexKey);
                    final ISqlJetBtreeIndexTable indexTable = indexesTables.get(indexDef.getName());
                    final long lookup = indexTable.lookup(false, indexKey);
                    if (lookup != 0) {
                        if (Action.INSERT == action) {
                            throw new SqlJetException(SqlJetErrorCode.CONSTRAINT, "Insert fails: unique index "
                                    + indexDef.getName());
                        } else if (Action.UPDATE == action && lookup != currentRowId) {
                            throw new SqlJetException(SqlJetErrorCode.CONSTRAINT, "Update fails: unique index "
                                    + indexDef.getName());
                        }
                    }
                }
            }
        }

        // modify indexes
        for (final ISqlJetIndexDef indexDef : indexesDefs.values()) {
            final ISqlJetBtreeIndexTable indexTable = indexesTables.get(indexDef.getName());
            if (Action.INSERT != action) {
                indexTable.delete(currentRowId, getKeyForIndex(getAsNamedFields(currentRow), indexDef));
            }
            if (Action.DELETE != action) {
                final Object[] indexKey = indexDef.isUnique() ? indexKeys.get(indexDef.getName()) : getKeyForIndex(
                        fields, indexDef);
                indexTable.insert(rowId, true, indexKey);
            }
        }

    }

    /**
     * @param values
     * @return
     */
    public Map<String, Object> getAsNamedFields(Object... values) throws SqlJetException {

        if (values == null)
            throw new SqlJetException(SqlJetErrorCode.MISUSE, "Values are missing");

        final int fieldsSize = values.length;
        final List<ISqlJetColumnDef> columns = tableDef.getColumns();
        final int columnsSize = columns.size();
        if (fieldsSize > columnsSize) {
            throw new SqlJetException(SqlJetErrorCode.MISUSE, "Data values count is more than columns in table");
        }

        final Map<String, Object> namedFields = new HashMap<String, Object>();

        int i = 0;
        for (final ISqlJetColumnDef column : columns) {
            if (null != column.getConstraints()) {
                for (final ISqlJetColumnConstraint constraint : column.getConstraints()) {
                    if (constraint instanceof ISqlJetColumnNotNull) {
                        if (i >= fieldsSize || null == values[i]) {
                            throw new SqlJetException(SqlJetErrorCode.MISUSE, "Column " + column.getName()
                                    + " is NOT NULL");
                        }
                    }
                }
            }
            namedFields.put(column.getName(), (i < fieldsSize ? values[i] : null));
            i++;
        }

        return namedFields;
    }

    public Object[] getKeyForIndex(final Map<String, Object> fields, final ISqlJetIndexDef indexDef) {
        if (tableDef.getColumnIndexConstraint(indexDef.getName()) != null) {
            final String column = tableDef.getColumnIndexConstraint(indexDef.getName()).getColumn().getName();
            return new Object[] { fields.get(column) };
        } else if (tableDef.getTableIndexConstraint(indexDef.getName()) != null) {
            final List<String> columns = tableDef.getTableIndexConstraint(indexDef.getName()).getColumns();
            final int columnsCount = columns.size();
            final Object[] key = new Object[columnsCount];
            int i = 0;
            for (final String column : columns) {
                key[i++] = fields.get(column);
            }
            return key;
        } else {
            final List<ISqlJetIndexedColumn> indexedColumns = indexDef.getColumns();
            final int columnsCount = indexedColumns.size();
            final Object[] key = new Object[columnsCount];
            int i = 0;
            for (final ISqlJetIndexedColumn column : indexedColumns) {
                key[i++] = fields.get(column.getName());
            }
            return key;
        }
    }

    public boolean checkIndex(String indexName, Object[] key) throws SqlJetException {
        if (!isIndexExists(indexName))
            throw new SqlJetException(SqlJetErrorCode.MISUSE);
        if (null != indexName) {
            return Arrays.equals(key, getKeyForIndex(getAsNamedFields(getValues()), indexesDefs.get(indexName)));
        } else {
            return getRowId() == getKeyForRowId(key);
        }
    }

    private Long getKeyForRowId(Object[] key) throws SqlJetException {
        if (!tableDef.isRowIdPrimaryKey())
            throw new SqlJetException(SqlJetErrorCode.MISUSE, "Index not defined");
        if (key.length == 1) {
            final Object[] k = SqlJetUtility.adjustNumberTypes(key);
            if (k[0] instanceof Long) {
                return (Long) k[0];
            }
        }
        throw new SqlJetException(SqlJetErrorCode.MISUSE, "Bad key");
    }

    /**
     * @return the primaryKeyIndex
     */
    public String getPrimaryKeyIndex() {
        return tableDef.isRowIdPrimaryKey() ? null : tableDef.getPrimaryKeyIndexName();
    }

    public boolean locate(String indexName, boolean next, Object... key) throws SqlJetException {
        if (null == key)
            throw new SqlJetException(SqlJetErrorCode.MISUSE, "Bad key");
        if (null != indexName) {
            if (!indexesDefs.containsKey(indexName))
                throw new SqlJetException(SqlJetErrorCode.MISUSE, "Index not found: " + indexName);
            final ISqlJetBtreeIndexTable indexTable = indexesTables.get(indexName);
            final long lookup = indexTable.lookup(next, key);
            return lookup != 0 && goToRow(lookup);
        } else {
            if (next) {
                return next();
            } else {
                return goToRow(getKeyForRowId(key));
            }
        }
    }

    /**
     * @return the indexesTables
     */
    public Map<String, ISqlJetBtreeIndexTable> getIndexesTables() {
        return Collections.unmodifiableMap(indexesTables);
    }

    public long insert(Map<String, Object> values) throws SqlJetException {
        return insertWithRowId(getRowIdFromValues(values), unwrapValues(values));
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeDataTable#update
     * (long, java.util.Map)
     */
    public void update(long rowId, Map<String, Object> values) throws SqlJetException {
        updateWithRowId(rowId, getRowIdFromValues(values), unwrapValues(values));
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeDataTable#update
     * (java.util.Map)
     */
    public void update(Map<String, Object> values) throws SqlJetException {
        updateWithRowId(getRowId(), getRowIdFromValues(values), unwrapValues(values));
    }

    /**
     * @param values
     * @return
     * 
     * @throws SqlJetException
     */
    private Object[] unwrapValues(Map<String, Object> values) {
        int i = 0;
        final List<ISqlJetColumnDef> columns = tableDef.getColumns();
        final Object[] unwrapped = new Object[columns.size()];
        if (null != values)
            for (ISqlJetColumnDef column : columns) {
                final String columnName = column.getName();
                final Object value = values.get(columnName);
                unwrapped[i++] = value;
            }
        return unwrapped;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.internal.table.SqlJetBtreeTable#getInteger(int)
     */
    @Override
    public long getInteger(int field) throws SqlJetException {
        if (field == tableDef.getRowIdPrimaryKeyColumnIndex()) {
            return getRowId();
        } else {
            return super.getInteger(field);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.internal.table.SqlJetBtreeTable#getValue(int)
     */
    @Override
    public Object getValue(int field) throws SqlJetException {
        if (field == tableDef.getRowIdPrimaryKeyColumnIndex()) {
            return getRowId();
        } else {
            return super.getValue(field);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.internal.table.ISqlJetBtreeDataTable#isIndexExists
     * (java.lang.String)
     */
    public boolean isIndexExists(String indexName) {
        return null == indexName || getIndexDefinitions().containsKey(indexName);
    }

    public static boolean isFieldNameRowId(String fieldName) {
        if (null == fieldName)
            return false;
        for (int i = 0; i < rowIdNames.length; i++) {
            if (rowIdNames[i].equalsIgnoreCase(fieldName))
                return true;
        }
        return false;
    }

    public static long getRowIdFromValues(final Map<String, Object> values) throws SqlJetException {
        if (null == values)
            return 0;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            final String name = entry.getKey();
            final Object value = entry.getValue();
            for (int i = 0; i < rowIdNames.length; i++) {
                if (rowIdNames[i].equalsIgnoreCase(name)) {
                    if (null != value) {
                        if (value instanceof Long) {
                            return (Long) value;
                        } else {
                            throw new SqlJetException(SqlJetErrorCode.MISUSE, "ROWID must be integer value");
                        }
                    }
                }
            }
        }
        return 0;
    }

}
