/**
 * SqlJetFile.java
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
package org.tmatesoft.sqljet.core.internal.fs;

import static org.tmatesoft.sqljet.core.SqlJetException.assertion;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Arrays;
import java.util.EnumSet;

import org.tmatesoft.sqljet.core.ISqlJetFile;
import org.tmatesoft.sqljet.core.SqlJetDeviceCharacteristics;
import org.tmatesoft.sqljet.core.SqlJetErrorCode;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetFileOpenPermission;
import org.tmatesoft.sqljet.core.SqlJetFileType;
import org.tmatesoft.sqljet.core.SqlJetIOErrorCode;
import org.tmatesoft.sqljet.core.SqlJetIOException;
import org.tmatesoft.sqljet.core.SqlJetLockType;

/**
 * @author TMate Software Ltd.
 * @author Sergey Scherbina (sergey.scherbina@gmail.com)
 * 
 */
public class SqlJetFile implements ISqlJetFile {

    public static final int SQLJET_DEFAULT_SECTOR_SIZE = 512;

    private SqlJetFileType fileType;
    private EnumSet<SqlJetFileOpenPermission> permissions;
    private SqlJetFileSystem fileSystem;
    private RandomAccessFile file;
    private File filePath;
    private boolean noLock;

    private SqlJetLockType lockType = SqlJetLockType.NONE;
    private int sharedLockCount = 0;

    /**
     * @param fileSystem
     * @param file
     * @param filePath
     * @param permissions
     * @param type
     * @param noLock
     */

    SqlJetFile(final SqlJetFileSystem fileSystem, final RandomAccessFile file, final File filePath,
            final SqlJetFileType fileType, final EnumSet<SqlJetFileOpenPermission> permissions, final boolean noLock) {

        this.fileSystem = fileSystem;
        this.file = file;
        this.filePath = filePath;
        this.fileType = fileType;
        this.permissions = permissions;
        this.noLock = noLock;

    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetFile#getFileType()
     */
    public SqlJetFileType getFileType() {
        return fileType;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetFile#getPermissions()
     */
    public EnumSet<SqlJetFileOpenPermission> getPermissions() {
        // return clone to avoid manipulations with file's permissions
        return permissions.clone();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetFile#close()
     */
    public synchronized void close() throws SqlJetException {
        if (null == file)
            return;

        if (!noLock && SqlJetLockType.NONE != lockType) {
            unlock(SqlJetLockType.NONE);
            /*
             * If there are outstanding locks, do not actually close the file
             * just yet because that would clear those locks.
             */
            if (sharedLockCount > 0)
                return;
        }

        try {
            file.close();
        } catch (IOException e) {
            throw new SqlJetException(SqlJetErrorCode.IOERR, e);
        } finally {
            file = null;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetFile#read(byte[], int, long)
     */
    public int read(byte[] buffer, int amount, long offset) throws SqlJetException {
        assertion(amount > 0);
        assertion(offset >= 0);
        assertion(buffer);
        assertion(buffer.length >= amount);
        assertion(file);
        try {
            final ByteBuffer dst = ByteBuffer.wrap(buffer, 0, amount);
            final int read = file.getChannel().position(offset).read(dst);
            if (amount > read)
                Arrays.fill(buffer, read < 0 ? 0 : read, amount, (byte) 0);
            return read < 0 ? 0 : read;
        } catch (IOException e) {
            throw new SqlJetIOException(SqlJetIOErrorCode.IOERR_READ, e);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetFile#write(byte[], int, long)
     */
    public void write(byte[] buffer, int amount, long offset) throws SqlJetException {
        assertion(amount > 0);
        assertion(offset >= 0);
        assertion(buffer);
        assertion(buffer.length >= amount);
        assertion(file);
        try {
            final ByteBuffer src = ByteBuffer.wrap(buffer, 0, amount);
            file.getChannel().position(offset).write(src);
        } catch (IOException e) {
            throw new SqlJetIOException(SqlJetIOErrorCode.IOERR_WRITE, e);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetFile#truncate(long)
     */
    public void truncate(long size) throws SqlJetException {
        assertion(size >= 0);
        assertion(file);
        try {
            file.setLength(size);
        } catch (IOException e) {
            throw new SqlJetIOException(SqlJetIOErrorCode.IOERR_TRUNCATE, e);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetFile#sync(boolean, boolean)
     */
    public void sync(boolean dataOnly, boolean full) throws SqlJetException {
        assertion(file);
        try {
            file.getChannel().force(!dataOnly || full);
        } catch (IOException e) {
            throw new SqlJetIOException(SqlJetIOErrorCode.IOERR_FSYNC, e);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetFile#fileSize()
     */
    public long fileSize() throws SqlJetException {
        assertion(file);
        try {
            return file.length();
        } catch (IOException e) {
            throw new SqlJetException(SqlJetErrorCode.IOERR, e);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetFile#lockType()
     */
    public SqlJetLockType getLockType() throws SqlJetException {
        return lockType;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.ISqlJetFile#lock(org.tmatesoft.sqljet.core.
     * SqlJetLockType)
     */

    public synchronized boolean lock(final SqlJetLockType lockType) throws SqlJetException {
        assertion(lockType);
        assertion(file);

        /*
         * The following describes the implementation of the various locks and
         * lock transitions in terms of the POSIX advisory shared and exclusive
         * lock primitives (called read-locks and write-locks below, to avoid
         * confusion with SQLite lock names). The algorithms are complicated
         * slightly in order to be compatible with windows systems
         * simultaneously accessing the same database file, in case that is ever
         * required.
         * 
         * Symbols defined in os.h indentify the 'pending byte' and the
         * 'reserved byte', each single bytes at well known offsets, and the
         * 'shared byte range', a range of 510 bytes at a well known offset.
         * 
         * To obtain a SHARED lock, a read-lock is obtained on the 'pending
         * byte'. If this is successful, a random byte from the 'shared byte
         * range' is read-locked and the lock on the 'pending byte' released.
         * 
         * A process may only obtain a RESERVED lock after it has a SHARED lock.
         * A RESERVED lock is implemented by grabbing a write-lock on the
         * 'reserved byte'.
         * 
         * A process may only obtain a PENDING lock after it has obtained a
         * SHARED lock. A PENDING lock is implemented by obtaining a write-lock
         * on the 'pending byte'. This ensures that no new SHARED locks can be
         * obtained, but existing SHARED locks are allowed to persist. A process
         * does not have to obtain a RESERVED lock on the way to a PENDING lock.
         * This property is used by the algorithm for rolling back a journal
         * file after a crash.
         * 
         * An EXCLUSIVE lock, obtained after a PENDING lock is held, is
         * implemented by obtaining a write-lock on the entire 'shared byte
         * range'. Since all other locks require a read-lock on one of the bytes
         * within this range, this ensures that no other locks are held on the
         * database.
         * 
         * The reason a single byte cannot be used instead of the 'shared byte
         * range' is that some versions of windows do not support read-locks. By
         * locking a random byte from a range, concurrent SHARED locks may exist
         * even if the locking primitive used is always a write-lock.
         */
        
        if (noLock)
            return false;

        /*
         * If there is already a lock of this type or more restrictive on the
         * file then do nothing.
         */
        if (this.lockType.compareTo(lockType) > 0)
            return false;

        /* Make sure the locking sequence is correct */
        assertion(lockType != SqlJetLockType.PENDING);
        assertion(this.lockType != SqlJetLockType.NONE || lockType == SqlJetLockType.SHARED);
        assertion(lockType != SqlJetLockType.RESERVED || this.lockType == SqlJetLockType.SHARED);

         /* If a SHARED lock is requested, and some thread using this PID already
         ** has a SHARED or RESERVED lock, then increment reference counts and
         ** return SQLITE_OK.
         */
         if( lockType == SqlJetLockType.SHARED && 
             ( this.lockType == SqlJetLockType.SHARED || 
               this.lockType == SqlJetLockType.RESERVED) )
         {
           //pFile->locktype = SHARED_LOCK;
           this.sharedLockCount++;
           return true;
         }

        try {

            final FileChannel channel = file.getChannel();

            /*
             * A PENDING lock is needed before acquiring a SHARED lock and
             * before acquiring an EXCLUSIVE lock. For the SHARED lock, the
             * PENDING will be released.
             */
            FileLock pendingLock = null;
            
            if (lockType == SqlJetLockType.SHARED
                    || (lockType == SqlJetLockType.EXCLUSIVE && this.lockType.compareTo(SqlJetLockType.PENDING) < 0)) {
                pendingLock = channel.tryLock(PENDING_BYTE, 1, lockType == SqlJetLockType.SHARED);
                if(null==pendingLock) return false;
            }
            
            /* If control gets to this point, then actually go ahead and make
             ** operating system calls for the specified lock.
             */
             if( lockType == SqlJetLockType.SHARED ){

               /* Now get the read-lock */
               final FileLock sharedLock = channel.tryLock(SHARED_FIRST, SHARED_SIZE, true);
               
               /* Drop the temporary PENDING lock */
               if(null!=pendingLock) pendingLock.release();

               if(null==sharedLock) return false;
               
               this.lockType = SqlJetLockType.SHARED;
               sharedLockCount++;
               
             }else if( lockType == SqlJetLockType.EXCLUSIVE && sharedLockCount>1 ){
               /* We are trying for an exclusive lock but another thread in this
               ** same process is still holding a shared lock. */
               return false;
               
             }else{
               /* The request was for a RESERVED or EXCLUSIVE lock.  It is
               ** assumed that there is a SHARED or greater lock on the file
               ** already.
               */
               assertion(SqlJetLockType.NONE!=this.lockType);
               
               switch( lockType ){
                 case RESERVED:
                   final FileLock reservedLock = 
                       channel.tryLock(RESERVED_BYTE, 1, false);
                   if(null==reservedLock) return false;
                   break;
                 case EXCLUSIVE:
                   final FileLock exclusiveLock = 
                       channel.tryLock(SHARED_FIRST, SHARED_SIZE, false);
                   if(null==exclusiveLock) {
                       this.lockType = SqlJetLockType.PENDING;
                       return false;
                   }
                   break;
                 default:
                   assertion(false);
               }

               
             }
             
             this.lockType = lockType;

        } catch (IOException e) {
            throw new SqlJetIOException(SqlJetIOErrorCode.IOERR_LOCK, e);
        }

        return true;
        
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.ISqlJetFile#unlock(org.tmatesoft.sqljet.core
     * .SqlJetLockType)
     */
    public boolean unlock(SqlJetLockType lockType) throws SqlJetException {
        assertion(lockType);
        assertion(file);

        if (noLock)
            return false;

        // TODO Auto-generated method stub
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetFile#checkReservedLock()
     */
    public boolean checkReservedLock() {
        if (noLock)
            return false;

        // TODO Auto-generated method stub
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetFile#sectorSize()
     */
    public int sectorSize() {
        return SQLJET_DEFAULT_SECTOR_SIZE;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetFile#deviceCharacteristics()
     */
    public EnumSet<SqlJetDeviceCharacteristics> deviceCharacteristics() {
        return null;
    }

}
