/**
 * SqlJetValue.java
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
package org.tmatesoft.sqljet.api;

import java.nio.ByteBuffer;
import java.util.EnumSet;

import org.tmatesoft.sqljet.core.ISqlJetVdbeMem;
import org.tmatesoft.sqljet.core.SqlJetEncoding;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetMemType;
import org.tmatesoft.sqljet.core.internal.SqlJetUtility;
import org.tmatesoft.sqljet.core.internal.vdbe.SqlJetVdbeMem;
import org.tmatesoft.sqljet.core.internal.vdbe.SqlJetVdbeMemFlags;

/**
 * @author TMate Software Ltd.
 * @author Sergey Scherbina (sergey.scherbina@gmail.com)
 * 
 */
public class SqlJetApiValue {

    private ISqlJetVdbeMem mem;

    /**
     * 
     */
    SqlJetApiValue(ISqlJetVdbeMem mem) {
        this.mem = mem;
    }

    /**
     * @return the mem
     */
    ISqlJetVdbeMem getMem() {
        return mem;
    }
    
    /**
     * 
     */
    public SqlJetApiValue() {
        mem = new SqlJetVdbeMem();
        mem.setNull();
    }

    public boolean isNull() {
        if(null==mem) return true;
        final EnumSet<SqlJetVdbeMemFlags> flags = mem.getFlags();
        if(null==flags) return false;
        return flags.contains(SqlJetVdbeMemFlags.Null);
    }
    
    public SqlJetApiValue(String value) throws SqlJetException {
        mem = new SqlJetVdbeMem();
        mem.setStr(ByteBuffer.wrap(SqlJetUtility.getBytes(value)), SqlJetEncoding.UTF8);
    }

    public SqlJetApiValue(Long value) {
        mem = new SqlJetVdbeMem();
        mem.setInt64(value);
    }

    public SqlJetApiValue(Double value) {
        mem = new SqlJetVdbeMem();
        mem.setDouble(value);
    }

    public String getString() throws SqlJetException {
        if(isNull()) return null;
        return SqlJetUtility.toString(mem.valueText(SqlJetEncoding.UTF8));
    }

    public Long getInteger() {
        if(isNull()) return null;
        return mem.intValue();
    }

    public Double getReal() {
        if(isNull()) return null;
        return mem.realValue();
    }

}
