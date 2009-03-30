/**
 * SqlJetBtreeTableCreateFlags.java
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

import java.util.EnumSet;

/**
 * The flags parameter to sqlite3BtreeCreateTable can be the bitwise OR of the
 * following flags:
 * 
 * @author TMate Software Ltd.
 * @author Sergey Scherbina (sergey.scherbina@gmail.com)
 * 
 */
public enum SqlJetBtreeTableCreateFlags {

    /** Table has only 64-bit signed integer keys */
    INTKEY((byte)1),

    /** Table has keys only - no data */
    ZERODATA((byte)2),

    /** Data stored in leaves only. Implies INTKEY */
    LEAFDATA((byte)4);

    private byte value;
    
    /**
     * 
     */
    private SqlJetBtreeTableCreateFlags(byte value) {
        this.value = value;
    }
    
    public static byte toByte(EnumSet<SqlJetBtreeTableCreateFlags> flags) {
        byte v = 0;
        for (SqlJetBtreeTableCreateFlags flag : flags) {
            v |= flag.value;
        }
        return v;
    }
    
    /**
     * @return the value
     */
    public byte getValue() {
        return value;
    }
    
    public boolean hasFlag(int flags) {
        return (flags & value) > 0;
    }
    
}
