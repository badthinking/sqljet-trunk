/**
 * SqlJetTableStressTest.java
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
 * contact TMate Software at support@svnkit.com
 */
package org.tmatesoft.sqljet.core.table;

import java.io.File;

import junit.framework.TestCase;

import org.tmatesoft.sqljet.core.SqlJetException;

/**
 * @author TMate Software Ltd.
 * @author Dmitry Stadnik (dtrace@seznam.cz)
 * 
 */
public class SqlJetTableStressTest extends TestCase {

    private File dbFile = new File("sqljet-test/db/bigdb.sqlite");
    private SqlJetDb db;

    public SqlJetTableStressTest() {
        super("Table Stress Test");
    }

    @Override
    protected void setUp() throws Exception {
        if (dbFile.exists()) {
            dbFile.delete();
        }
        db = SqlJetDb.open(dbFile, true);
        db.runWriteTransaction(new ISqlJetTransaction() {

            public Object run(SqlJetDb db) throws SqlJetException {
                db.getSchema().createTable("create table t (c1 text, c2 int)");
                return null;
            }
        });
    }

    @Override
    protected void tearDown() throws Exception {
        db.close();
        dbFile.delete();
    }

    public void testInsert100000Records() throws Exception {
        final ISqlJetTable t = db.getTable("t");
        db.runWriteTransaction(new ISqlJetTransaction() {

            public Object run(SqlJetDb db) throws SqlJetException {
                for (int i = 0; i < 100000; i++) {
                    t.insert("Bolshie nogi shli po doroge!", i);
                }
                return null;
            }
        });

        ISqlJetCursor c = t.open();
        c.last();
        c.previous();
        assertTrue(c.getString(0).indexOf("nogi") > 0);
        assertEquals(99998, c.getInteger(1));
    }
}
