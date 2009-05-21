/**
 * SqlJetEncoding.java
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

/**
 * These constant define integer codes that represent the various text encodings
 * supported by SQLite.
 * 
 * @author TMate Software Ltd.
 * @author Sergey Scherbina (sergey.scherbina@gmail.com)
 * 
 */
public enum SqlJetEncoding {

    UTF8("UTF-8"), // 1
    UTF16LE("UTF-16LE"), // 2
    UTF16BE("UTF-16BE"), // 3

    /** Use native byte order */
    UTF16("UTF-16"), // 4

    /** sqlite3_create_function only */
    ANY, // 5

    /** sqlite3_create_collation only */
    UTF16_ALIGNED; // 8

    private String charsetName = "error";

    private SqlJetEncoding() {
    }

    private SqlJetEncoding(String charsetName) {
        this.charsetName = charsetName;
    }

    /**
     * @return the charsetName
     */
    public String getCharsetName() {
        return charsetName;
    }

}
