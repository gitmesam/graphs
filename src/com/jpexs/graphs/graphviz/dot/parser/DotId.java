/*
 * Copyright (C) 2018 JPEXS, All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */
package com.jpexs.graphs.graphviz.dot.parser;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 *
 * @author JPEXS
 */
public final class DotId {

    final static String[] RESERVED_WORDS = new String[]{"node", "edge", "graph", "digraph", "subgraph", "strict"};
    final static Pattern RESERVED_PATTERN = Pattern.compile("^" + String.join("|", RESERVED_WORDS) + "$", Pattern.CASE_INSENSITIVE);
    final static Pattern NUMERAL_PATTERN = Pattern.compile("^[-]?(.[0-9]+ | [0-9]+(.[0-9]*)?)$");
    final static String IDENTIFIER_FIRST_CHARS = "a-zA-Z\\u0200-\\u0377";
    final static String IDENTIFIER_NEXT_CHARS = IDENTIFIER_FIRST_CHARS + "0-9";
    final static String CH = "^[" + IDENTIFIER_FIRST_CHARS + "][" + IDENTIFIER_NEXT_CHARS + "]*$";
    final static Pattern IDENTIFIER_PATTERN = Pattern.compile(CH);
    final static Pattern HTML_PATTERN = Pattern.compile("^<.+>$");

    private String value;
    private boolean isHtml;
    private String toStringValue;

    public DotId(String value, boolean isHtml) {
        this.value = value;
        this.isHtml = isHtml;
        this.toStringValue = generateToString();
    }

    public static DotId fromString(String id) {
        DotLexer lex = new DotLexer(new StringReader(id));
        try {
            DotParsedSymbol symbol = lex.lex();
            if (symbol.type != DotParsedSymbol.TYPE_ID) {
                return null;
            }
            boolean isHtml = false;
            if (symbol.idtype == DotParsedSymbol.IDTYPE_HTML_STRING) {
                isHtml = true;
            }
            DotParsedSymbol next = lex.lex();
            if (next.type != DotParsedSymbol.TYPE_EOF) {
                throw new RuntimeException("Invalid id: " + id);
            }
            return new DotId(symbol.getValueAsString(), isHtml);
        } catch (IOException | DotParseException ex) {
            throw new RuntimeException("Invalid id: " + id);
        }
    }

    @Override
    public String toString() {
        return toStringValue;
    }

    private String generateToString() {
        if (isHtml) {
            return "<" + value + ">";
        }
        if (RESERVED_PATTERN.matcher(value).matches()) {
            return "\"" + value + "\"";
        }

        if (NUMERAL_PATTERN.matcher(value).matches()) {
            return value;
        }
        if (IDENTIFIER_PATTERN.matcher(value).matches()) {
            return value;
        }
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 89 * hash + Objects.hashCode(this.value);
        hash = 89 * hash + (this.isHtml ? 1 : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DotId other = (DotId) obj;
        if (this.isHtml != other.isHtml) {
            return false;
        }
        if (!Objects.equals(this.value, other.value)) {
            return false;
        }
        return true;
    }

    public static DotId join(CharSequence delimiter, DotId... ids) {
        return join(new DotId(delimiter.toString(), false), ids);
    }

    public static DotId join(DotId delimiter, DotId... ids) {
        return join(delimiter, Arrays.asList(ids));
    }

    public static DotId join(CharSequence delimiter, Iterable<? extends DotId> ids) {
        return join(new DotId(delimiter.toString(), false), ids);
    }

    public static DotId join(DotId delimiter, Iterable<? extends DotId> ids) {
        StringBuilder sb = new StringBuilder();
        boolean retHtml = false;

        for (DotId id : ids) {
            if (id.isHtml) {
                retHtml = true;
            }
        }
        DotId usedDelimiter = delimiter;
        if (retHtml) {
            usedDelimiter = usedDelimiter.toHtmlDotId();
        }

        boolean first = true;
        for (DotId id : ids) {
            if (!first) {
                sb.append(usedDelimiter.value);
            }
            first = false;
            DotId idToAppend = id;
            if (retHtml) {
                idToAppend = idToAppend.stripEndNewLine().toHtmlDotId();
            }
            sb.append(idToAppend.value);
        }

        return new DotId(sb.toString(), retHtml);
    }

    private DotId stripEndNewLine() {
        if (isHtml) {
            return this;
        }
        return new DotId(value.replaceFirst("\\\\(r|n|l)$", ""), false);
    }

    private DotId toHtmlDotId() {
        if (isHtml) {
            return this;
        }
        String htmlValue = value.replaceAll("\\\\(r|n|l)", "<BR/>");
        return new DotId(htmlValue, true);
    }

}
