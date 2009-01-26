/**
 * IPage.java
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

import java.util.EnumSet;

/**
 * @author TMate Software Ltd.
 * @author Sergey Scherbina (sergey.scherbina@gmail.com)
 * 
 */
public interface ISqlJetPage {
    
    ISqlJetPager getPager();
    void setPager(final ISqlJetPager pager);
    
    EnumSet<SqlJetPageFlags> getFlags();
    void setFlags(final EnumSet<SqlJetPageFlags> flags);

    /**
     * Increment the reference count for a page.
     * @throws SqlJetException 
     * 
     */
    void ref() throws SqlJetException;

    /**
     * Release a page.
     * 
     * If the number of references to the page drop to zero, then the page is
     * added to the LRU list. When all references to all pages are released, a
     * rollback occurs and the lock on the database is removed.
     * 
     */
    void unref();

    /**
     * This function is used to mark a data-page as writable. It uses
     * pager_write() to open a journal file (if it is not already open) and
     * write the page *pData to the journal.
     * 
     * The difference between this function and pager_write() is that this
     * function also deals with the special case where 2 or more pages fit on a
     * single disk sector. In this case all co-resident pages must have been
     * written to the journal file before returning.
     * 
     * @throws SqlJetException
     */
    void write() throws SqlJetException;

    /**
     * A call to this routine tells the pager that if a rollback occurs, it is
     * not necessary to restore the data on the given page. This means that the
     * pager does not have to record the given page in the rollback journal.
     * 
     * If we have not yet actually read the content of this page (if the
     * PgHdr.needRead flag is set) then this routine acts as a promise that we
     * will never need to read the page content in the future. so the needRead
     * flag can be cleared at this point.
     * 
     * @throws SqlJetException
     */
    void dontRollback() throws SqlJetException;

    /**
     * A call to this routine tells the pager that it is not necessary to write
     * the information on page pPg back to the disk, even though that page might
     * be marked as dirty.
     * 
     * The overlying software layer calls this routine when all of the data on
     * the given page is unused. The pager marks the page as clean so that it
     * does not get written to disk.
     * 
     * Tests show that this optimization, together with the
     * sqlite3PagerDontRollback() below, more than double the speed of large
     * INSERT operations and quadruple the speed of large DELETEs.
     * 
     * When this routine is called, set the alwaysRollback flag to true.
     * Subsequent calls to sqlite3PagerDontRollback() for the same page will
     * thereafter be ignored. This is necessary to avoid a problem where a page
     * with data is added to the freelist during one part of a transaction then
     * removed from the freelist during a later part of the same transaction and
     * reused for some other purpose. When it is first added to the freelist,
     * this routine is called. When reused, the sqlite3PagerDontRollback()
     * routine is called. But because the page contains critical data, we still
     * need to be sure it gets rolled back in spite of the
     * sqlite3PagerDontRollback() call.
     * 
     * @throws SqlJetException
     */
    void dontWrite() throws SqlJetException;

    /**
     * Move the page to location pageNumber in the file.
     * 
     * There must be no references to the page previously located at pageNumber
     * (which we call pPgOld) though that page is allowed to be in cache. If the
     * page previously located at pgno is not already in the rollback journal,
     * it is not put there by by this routine.
     * 
     * References to the page remain valid. Updating any meta-data associated
     * with page (i.e. data stored in the nExtra bytes allocated along with the
     * page) is the responsibility of the caller.
     * 
     * A transaction must be active when this routine is called. It used to be
     * required that a statement transaction was not active, but this
     * restriction has been removed (CREATE INDEX needs to move a page when a
     * statement transaction is active).
     * 
     * If the second argument, isCommit, is true, then this page is being moved
     * as part of a database reorganization just before the transaction is being
     * committed. In this case, it is guaranteed that the database page refers
     * to will not be written to again within this transaction.
     * 
     * 
     * @param pageNumber
     * @param isCommit
     * @throws SqlJetException
     */
    void move(final int pageNumber, final boolean isCommit) throws SqlJetException;

    /**
     * Return a pointer to the data for the specified page.
     * @throws SqlJetException 
     * 
     */
    byte[] getData() throws SqlJetException;

    /**
     * Return a pointer to the Pager.nExtra bytes of "extra" space allocated
     * along with the specified page.
     * 
     */
    byte[] getExtra();

    
    /**
     * Hash of page content
     * 
     * @return
     */
    long getHash();
    void setHash(long hash);
    
    int getPageNumber();
    void setPageNumber(final int pageNumber);
    
    ISqlJetPage getNext(); 
    ISqlJetPage getPrev();
    
}
