/**
 * SqlJetBtreeTest.java
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
package org.tmatesoft.sqljet.core;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tmatesoft.sqljet.core.internal.SqlJetCloneable;
import org.tmatesoft.sqljet.core.internal.SqlJetUtility;
import org.tmatesoft.sqljet.core.internal.btree.SqlJetBtree;
import org.tmatesoft.sqljet.core.internal.db.SqlJetDb;
import org.tmatesoft.sqljet.core.internal.fs.SqlJetFileSystemsManager;

/**
 * @author TMate Software Ltd.
 * @author Sergey Scherbina (sergey.scherbina@gmail.com)
 * 
 */
public class SqlJetBtreeTest {

    private static Logger logger = Logger.getLogger(SqlJetAbstractMockTest.SQLJET_TEST_LOGGER);

    private ISqlJetFileSystem fileSystem = SqlJetFileSystemsManager.getManager().find(null);
    private File testDataBase = new File("sqljet-test/db/testdb.sqlite");
    private File testTempFile;
    private ISqlJetBtree btree;
    private ISqlJetDb db = new SqlJetDb();

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        testTempFile = fileSystem.getTempFile();
        btree = new SqlJetBtree();
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception {
        if(testTempFile!=null)
            testTempFile.delete();
    }

    static class _OvflCell extends SqlJetCloneable {
        ByteBuffer pCell;
        int idx;
    }

    @Test
    public void testClone() throws Exception {

        _OvflCell c1 = new _OvflCell();
        c1.pCell = ByteBuffer.allocate(1);
        c1.idx = 1;
        c1.pCell.put((byte) 1);

        _OvflCell c2 = SqlJetUtility.memcpy(c1);

        Assert.assertNotNull(c2);
        Assert.assertNotNull(c2.pCell);
        Assert.assertEquals(c2.idx, c1.idx);
        Assert.assertSame(c2.pCell, c1.pCell);
        Assert.assertNotSame(c2, c1);

    }

    @Test
    public void testRead() throws Exception {
        db.getMutex().enter();
        try {
            btree.open(testDataBase, db, EnumSet.of(SqlJetBtreeFlags.READONLY), SqlJetFileType.MAIN_DB, EnumSet
                    .of(SqlJetFileOpenPermission.READONLY));
            try {
                btree.beginTrans(SqlJetTransactionMode.READ_ONLY);
                final int pageCount = btree.getPager().getPageCount();
                for (int i = 1; i <= pageCount; i++) {
                    logger.info("\npage " + i);
                    final ISqlJetBtreeCursor c = btree.getCursor(i, false, null);
                    c.enterCursor();
                    try {
                        final short flags = c.flags();
                        boolean intKey = SqlJetBtreeTableCreateFlags.INTKEY.hasFlag(flags);
                        logger.info("intKey " + intKey);
                        if (c.first()) {
                            logger.info("empty cursor");
                        } else {
                            do {
                                final long keySize = c.getKeySize();
                                logger.info("keySize " + keySize);
                                final int dataSize = c.getDataSize();
                                logger.info("dataSize " + dataSize);
                                ByteBuffer data = ByteBuffer.allocate(dataSize);
                                c.data(0, dataSize, data);
                                logger.info(new String(data.array(), "UTF8").replaceAll("[^\\p{Print}]", ""));
                                logger.info("next");
                            } while (!c.next());
                        }
                    } finally {
                        c.leaveCursor();
                    }
                }
            } finally {
                btree.close();
            }
        } finally {
            db.getMutex().leave();
        }
    }

    @Test
    public void testWrite() throws Exception {
        db.getMutex().enter();
        try {

            final String data = "Test data";
            final byte[] bytes = SqlJetUtility.getBytes(data);
            ByteBuffer pData = ByteBuffer.wrap(bytes);

            final EnumSet<SqlJetBtreeFlags> btreeFlags = EnumSet
                    .of(SqlJetBtreeFlags.CREATE, SqlJetBtreeFlags.READWRITE);
            final EnumSet<SqlJetFileOpenPermission> fileFlags = EnumSet.of(SqlJetFileOpenPermission.CREATE,
                    SqlJetFileOpenPermission.READWRITE);
            btree.open(testTempFile, db, btreeFlags, SqlJetFileType.MAIN_DB, fileFlags);
            try {
                btree.beginTrans(SqlJetTransactionMode.WRITE);
                for (int x = 1; x <= 10; x++) {
                    final int table = btree.createTable(EnumSet.of(SqlJetBtreeTableCreateFlags.INTKEY,
                            SqlJetBtreeTableCreateFlags.LEAFDATA));
                    final ISqlJetBtreeCursor c = btree.getCursor(table, true, null);
                    c.enterCursor();
                    try {
                        for (int y = 1; y <= 10; y++) {
                            c.insert(null, y, pData, pData.capacity(), 0, false);
                        }
                        c.closeCursor();
                    } finally {
                        c.leaveCursor();
                    }
                }
                btree.commit();
            } finally {
                btree.close();
            }

            btree.open(testTempFile, db, btreeFlags, SqlJetFileType.MAIN_DB, fileFlags);
            try {
                final int pageCount = btree.getPager().getPageCount();
                for (int i = 1; i <= pageCount; i++) {
                    final ISqlJetBtreeCursor c = btree.getCursor(i, false, null);
                    c.enterCursor();
                    try {
                        if (!c.first())
                            do {
                                StringBuilder s = new StringBuilder();
                                final long key = c.getKeySize();
                                if (key != 0)
                                    s.append("key : ").append(key).append(" ");
                                final int dataSize = c.getDataSize();
                                if (dataSize > 0) {
                                    ByteBuffer b = ByteBuffer.allocate(dataSize);
                                    c.data(0, dataSize, b);
                                    final String str = new String(b.array(), "UTF8");
                                    s.append("data : ").append(str.replaceAll("[^\\p{Print}]", ""));
                                    Assert.assertEquals(data, str);
                                }
                                if (s.length() > 0)
                                    logger.info(s.toString());
                            } while (!c.next());
                    } finally {
                        c.leaveCursor();
                    }
                }
            } finally {
                btree.close();
            }

        } finally {
            db.getMutex().leave();
        }
    }
}
