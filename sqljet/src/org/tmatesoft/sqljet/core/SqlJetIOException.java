/**
 * SqlJetIOException.java
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

/**
 * Extended exception for {@link SqlJetErrorCode#IOERR}
 * 
 * @author TMate Software Ltd.
 * @author Sergey Scherbina (sergey.scherbina@gmail.com)
 *
 */
public class SqlJetIOException extends SqlJetException {

    private static final long serialVersionUID = -7059309339596959681L;
    
    private SqlJetIOErrorCode ioErrorCode;

    /**
     * @return the ioErrorCode
     */
    public SqlJetIOErrorCode getIoErrorCode() {
        return ioErrorCode;
    }
    
    /**
     * Create extended exception for IOERR.
     * 
     * @param ioErrorCode
     */
    public SqlJetIOException(final SqlJetIOErrorCode ioErrorCode) {
        super(SqlJetErrorCode.IOERR);
        this.ioErrorCode = ioErrorCode;
    }
    
}
