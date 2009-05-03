/**
 * RepCacheDao.java
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
package org.tmatesoft.sqljet.api.repcache;

import java.io.File;

import org.tmatesoft.sqljet.api.SqlJetApiDb;
import org.tmatesoft.sqljet.api.SqlJetApiIndex;
import org.tmatesoft.sqljet.api.SqlJetApiTable;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.internal.SqlJetUtility;

/**
 * @author TMate Software Ltd.
 * @author Sergey Scherbina (sergey.scherbina@gmail.com)
 * 
 */
public class RepCacheDao {

    public static final String REP_CACHE_DAO = "RepCacheDao";

    public static final String REP_CACHE_TABLE = SqlJetUtility.getSysProp(REP_CACHE_DAO + ".REP_CACHE_TABLE",
            "rep_cache");

    private SqlJetApiDb db;
    private SqlJetApiTable table;
    private SqlJetApiIndex index;

    /**
     * @throws SqlJetException
     * 
     */
    public RepCacheDao(File file, boolean write) throws SqlJetException {
        db = new SqlJetApiDb(file, write);
        table = db.openTable(REP_CACHE_TABLE);
        index = db.openIndex(db.getIndexesNames(REP_CACHE_TABLE).iterator().next());
    }

    public void close() throws SqlJetException {
        db.lock();
        try {
            table.close();
            index.close();
            db.close();
        } finally {
            db.unlock();
        }
    }

    public void beginTransaction() throws SqlJetException {
        db.beginTransaction();
    }

    public void commit() throws SqlJetException {
        db.commit();
    }

    public void rollback() throws SqlJetException {
        db.rollback();
    }

    public boolean eof() {
        db.lock();
        try {
            return table.eof();
        } finally {
            db.unlock();
        }
    }

    public boolean first() throws SqlJetException {
        db.lock();
        try {
            return table.first();
        } finally {
            db.unlock();
        }
    }

    public boolean last() throws SqlJetException {
        db.lock();
        try {
            return table.last();
        } finally {
            db.unlock();
        }
    }

    public boolean next() throws SqlJetException {
        db.lock();
        try {
            return table.next();
        } finally {
            db.unlock();
        }
    }

    public boolean previous() throws SqlJetException {
        db.lock();
        try {
            return table.previous();
        } finally {
            db.unlock();
        }
    }

    public RepCache getRepCache() throws SqlJetException {
        db.lock();
        try {
            return new RepCache(table.getRecord());
        } finally {
            db.unlock();
        }
    }

}
