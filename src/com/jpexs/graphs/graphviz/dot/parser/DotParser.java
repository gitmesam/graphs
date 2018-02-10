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

import com.jpexs.graphs.graphviz.graph.AttributesBag;
import com.jpexs.graphs.graphviz.graph.ConnectableObject;
import com.jpexs.graphs.graphviz.graph.Edge;
import com.jpexs.graphs.graphviz.graph.Graph;
import com.jpexs.graphs.graphviz.graph.NodeId;
import com.jpexs.graphs.graphviz.graph.NodeIdToAttributes;
import com.jpexs.graphs.graphviz.graph.SubGraph;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * https://www.graphviz.org/doc/info/lang.html
 *
 * @author JPEXS
 */
public class DotParser {

    private static final String VALID_COMPASS_VALUES[] = new String[]{"n", "ne", "e", "se", "s", "sw", "w", "nw", "c", "_"};

    public Graph parse(Reader in) throws DotParseException, IOException {
        DotLexer lexer = new DotLexer(in);
        return file(lexer);
    }

    public Graph file(DotLexer lexer) throws DotParseException, IOException {
        Graph ret = graph(lexer);
        _expect(lexer, DotParsedSymbol.TYPE_EOF, "end of file");
        return ret;
    }

    /*
    graph: [ 'strict' ] ('graph' | 'digraph') [ ID ] '{' stmt_list '}'
     */
    public Graph graph(DotLexer lexer) throws IOException, DotParseException {
        boolean isStrict = false;
        boolean isDirectedGraph = false;
        DotParsedSymbol symbol = lexer.lex();
        if (symbol.type == DotParsedSymbol.TYPE_KEYWORD_STRICT) {
            symbol = lexer.lex();
            isStrict = true;
        }
        if (symbol.type == DotParsedSymbol.TYPE_KEYWORD_GRAPH) {

        } else if (symbol.type == DotParsedSymbol.TYPE_KEYWORD_DIGRAPH) {
            isDirectedGraph = true;
        } else {
            _expected("draph or digraph keyword", symbol);
        }
        symbol = lexer.lex();
        String id = null;
        if (symbol.type == DotParsedSymbol.TYPE_ID) {
            id = symbol.getValueAsString();
        } else {
            lexer.pushback(symbol);
        }
        _expect(lexer, DotParsedSymbol.TYPE_BRACE_OPEN, "{");
        List<Edge> edges = new ArrayList<>();
        List<NodeIdToAttributes> standaloneNodes = new ArrayList<>();
        AttributesBag graphAttributes = new AttributesBag();
        AttributesBag nodeAttributes = new AttributesBag();
        AttributesBag edgeAttributes = new AttributesBag();
        stmt_list(edges, standaloneNodes, graphAttributes, nodeAttributes, edgeAttributes, isDirectedGraph, lexer);
        _expect(lexer, DotParsedSymbol.TYPE_BRACE_CLOSE, "}");

        Graph graph = new Graph(isStrict, isDirectedGraph);
        graph.edges = edges;
        graph.nodes = standaloneNodes;
        graph.id = id;
        graph.graphAttributes = graphAttributes;
        graph.nodeAttributes = nodeAttributes;
        graph.edgeAttributes = edgeAttributes;
        return graph;
    }

    /*
    stmt_list : [ stmt [ ';' ] stmt_list ]
     */
    public void stmt_list(List<Edge> edges, List<NodeIdToAttributes> standaloneNodes, AttributesBag graphAttributes, AttributesBag nodeAttributes, AttributesBag edgeAttributes, boolean isDirectedGraph, DotLexer lexer) throws DotParseException, IOException {
        boolean empty = true;
        if (stmt(edges, standaloneNodes, graphAttributes, nodeAttributes, edgeAttributes, isDirectedGraph, lexer)) {
            empty = false;
        }
        DotParsedSymbol symbol = lexer.lex();
        if (symbol.type == DotParsedSymbol.TYPE_SEMICOLON) {
            empty = false;
            //ignore the semicolon
        } else {
            lexer.pushback(symbol);
        }
        if (!empty) {
            stmt_list(edges, standaloneNodes, graphAttributes, nodeAttributes, edgeAttributes, isDirectedGraph, lexer);
        }
    }

    /*
    stmt : a) node_stmt           - prefix: - ID [ ':' ID [ ':' compass_pt ]] '[' ... 
	 | b) edge_stmt           - prefix: - ID [ ':' ID [ ':' compass_pt ]] '--' ...
                                            - ID [ ':' ID [ ':' compass_pt ]] '->' ...
                                            - subgraph  '--' ...
                                            - subgraph  '->' ...
	 | c) attr_stmt           - prefix: - 'graph' ...
                                            - 'node' ...
                                            - 'edge' ...
	 | d) ID '=' ID           - prefix: - ID '=' ...
	 | e) subgraph            - prefix: - subgraph 
     */
    public boolean stmt(List<Edge> edges, List<NodeIdToAttributes> standaloneNodes, AttributesBag graphAttributes, AttributesBag nodeAttributes, AttributesBag edgeAttributes, boolean isDirectedGraph, DotLexer lexer) throws DotParseException, IOException {
        DotParsedSymbol symbol = lexer.lex();
        //d)
        if (symbol.type == DotParsedSymbol.TYPE_ID) {
            DotParsedSymbol idSymbol = symbol;
            symbol = lexer.lex();
            if (symbol.type == DotParsedSymbol.TYPE_EQUAL) {
                String key = idSymbol.getValueAsString();
                symbol = lexer.lex();
                _expect(DotParsedSymbol.TYPE_ID, "ID", symbol);
                String value = symbol.getValueAsString();
                graphAttributes.put(key, value);
                return true;
            } else {
                lexer.pushback(symbol);
                lexer.pushback(idSymbol);
                symbol = lexer.lex();
            }
        }

        //c)
        if (symbol.type == DotParsedSymbol.TYPE_KEYWORD_GRAPH || symbol.type == DotParsedSymbol.TYPE_KEYWORD_NODE || symbol.type == DotParsedSymbol.TYPE_KEYWORD_EDGE) {
            lexer.pushback(symbol);
            AttributesBag attributesBag = new AttributesBag();
            String attrKind = attr_stmt(lexer, attributesBag);
            switch (attrKind) {
                case "graph":
                    graphAttributes.putAll(attributesBag);
                    break;
                case "node":
                    nodeAttributes.putAll(attributesBag);
                    break;
                case "edge":
                    edgeAttributes.putAll(attributesBag);
                    break;
            }
            return true;
        } else {
            /* a) b) e) */
            lexer.pushback(symbol);
            ConnectableObject from;
            if ((from = node_id_or_subgraph(standaloneNodes, isDirectedGraph, lexer)) != null) {
                symbol = lexer.lex();
                Object obj = from;
                //b)
                if (symbol.type == DotParsedSymbol.TYPE_MINUSMINUS || symbol.type == DotParsedSymbol.TYPE_ARROW) {
                    lexer.pushback(symbol);
                    obj = edge_rhs(from, edges, isDirectedGraph, lexer);
                    symbol = lexer.lex();
                }
                //a) or finish of b)
                AttributesBag currentAttributes = new AttributesBag();
                if (symbol.type == DotParsedSymbol.TYPE_BRACKET_OPEN && ((obj instanceof Edge) || (obj instanceof NodeId))) {
                    lexer.pushback(symbol);
                    if (obj instanceof Edge) {
                        currentAttributes = ((Edge) obj).attributes;
                    }
                    AttributesBag attributes = attr_list(lexer);
                    currentAttributes.putAll(attributes);
                } else {
                    lexer.pushback(symbol);
                }
                if (obj instanceof NodeId) {
                    standaloneNodes.add(new NodeIdToAttributes((NodeId) obj, currentAttributes));
                }
                return true;
            }
        }

        return false;
    }

    /*
        (node_id | subgraph)
     */
    private ConnectableObject node_id_or_subgraph(List<NodeIdToAttributes> standaloneNodes, boolean isDirectedGraph, DotLexer lexer) throws DotParseException, IOException {
        DotParsedSymbol symbol = lexer.lex();
        if (symbol.type == DotParsedSymbol.TYPE_KEYWORD_SUBGRAPH || symbol.type == DotParsedSymbol.TYPE_BRACE_OPEN) {
            lexer.pushback(symbol);
            return subgraph(isDirectedGraph, lexer);
        } else if (symbol.type == DotParsedSymbol.TYPE_ID) {
            lexer.pushback(symbol);
            return node_id(lexer);
        } else {
            lexer.pushback(symbol);
        }
        return null;
    }

    /*
    attr_list :	'[' [ a_list ] ']' [ attr_list ]
     */
    public AttributesBag attr_list(DotLexer lexer) throws DotParseException, IOException {
        AttributesBag attributesBag = new AttributesBag();
        _expect(lexer, DotParsedSymbol.TYPE_BRACKET_OPEN, "[");
        DotParsedSymbol symbol = lexer.lex();
        if (symbol.type == DotParsedSymbol.TYPE_ID) {
            lexer.pushback(symbol);
            a_list(attributesBag, lexer);
        } else {
            lexer.pushback(symbol);
        }
        _expect(lexer, DotParsedSymbol.TYPE_BRACKET_CLOSE, "]");
        symbol = lexer.lex();
        if (symbol.type == DotParsedSymbol.TYPE_BRACKET_OPEN) {
            lexer.pushback(symbol);
            AttributesBag nextAtributesBag = attr_list(lexer);
            attributesBag.putAll(nextAtributesBag);
        } else {
            lexer.pushback(symbol);
        }
        return attributesBag;
    }

    /*
    attr_stmt :	(graph | node | edge) attr_list
     */
    public String /*type*/ attr_stmt(DotLexer lexer, AttributesBag out) throws DotParseException, IOException {
        DotParsedSymbol symbol = lexer.lex();
        if (!((symbol.type == DotParsedSymbol.TYPE_KEYWORD_GRAPH) || (symbol.type == DotParsedSymbol.TYPE_KEYWORD_NODE) || (symbol.type == DotParsedSymbol.TYPE_KEYWORD_EDGE))) {
            _expected("graph or node or edge keyword", symbol);
        }
        out.putAll(attr_list(lexer));
        return symbol.getValueAsString();
    }

    /*
    a_list: ID '=' ID [ (';' | ',') ] [ a_list ]
     */
    public void a_list(AttributesBag attributesBag, DotLexer lexer) throws DotParseException, IOException {
        DotParsedSymbol symbol = lexer.lex();
        _expect(DotParsedSymbol.TYPE_ID, "ID", symbol);
        String key = symbol.getValueAsString();
        _expect(lexer, DotParsedSymbol.TYPE_EQUAL, "=");
        symbol = lexer.lex();
        String value = symbol.getValueAsString();
        attributesBag.put(key, value);
        symbol = lexer.lex();
        if (symbol.type == DotParsedSymbol.TYPE_SEMICOLON || symbol.type == DotParsedSymbol.TYPE_COMMA) {
            symbol = lexer.lex();
        }
        if (symbol.type == DotParsedSymbol.TYPE_ID) {
            lexer.pushback(symbol);
            a_list(attributesBag, lexer);
        } else {
            lexer.pushback(symbol);
        }
    }

    /*
    node_stmt : node_id [ attr_list ]
     */
    public void node_stmt(List<NodeIdToAttributes> standaloneNodes, DotLexer lexer) throws DotParseException, IOException {
        NodeId node = node_id(lexer);
        DotParsedSymbol symbol = lexer.lex();
        if (symbol.type == DotParsedSymbol.TYPE_BRACKET_OPEN) {
            lexer.pushback(symbol);
            AttributesBag attributes = attr_list(lexer);
            standaloneNodes.add(new NodeIdToAttributes(node, attributes));
        } else {
            lexer.pushback(symbol);
        }
    }

    /*
    edge_stmt : (node_id | subgraph) edgeRHS [ attr_list ]
     */
    public void edge_stmt(List<Edge> edges, boolean isDirectedGraph, DotLexer lexer) throws DotParseException, IOException {
        DotParsedSymbol symbol = lexer.lex();
        ConnectableObject from;
        if (symbol.type == DotParsedSymbol.TYPE_ID) {
            lexer.pushback(symbol);
            from = node_id(lexer);
        } else {
            _expect(DotParsedSymbol.TYPE_KEYWORD_SUBGRAPH, "subgraph", symbol);
            from = subgraph(isDirectedGraph, lexer);
        }
        Edge edge = edge_rhs(from, edges, isDirectedGraph, lexer);
        symbol = lexer.lex();
        if (symbol.type == DotParsedSymbol.TYPE_BRACKET_OPEN) {
            lexer.pushback(symbol);
            AttributesBag attributes = attr_list(lexer);
            edge.attributes.putAll(attributes);
        } else {
            lexer.pushback(symbol);
        }
    }

    /*
    node_id : ID [ port ]
     */
    public NodeId node_id(DotLexer lexer) throws DotParseException, IOException {
        DotParsedSymbol symbol = lexer.lex();
        _expect(DotParsedSymbol.TYPE_ID, "ID", symbol);
        String id = symbol.getValueAsString();
        symbol = lexer.lex();
        NodeId node = new NodeId(id);
        if (symbol.type == DotParsedSymbol.TYPE_COLON) {
            lexer.pushback(symbol);
            port(node, lexer);
        } else {
            lexer.pushback(symbol);
        }
        return node;
    }

    /*    
    port : ':' ID [ ':' compass_pt ]
         | ':' compass_pt
    
     */
    public void port(NodeId node, DotLexer lexer) throws DotParseException, IOException {
        _expect(lexer, DotParsedSymbol.TYPE_COLON, "colon");
        DotParsedSymbol symbol = lexer.lex();
        if (symbol.idtype == DotParsedSymbol.IDTYPE_IDENTIFIER && Arrays.asList(VALID_COMPASS_VALUES).contains(symbol.getValueAsString())) {
            lexer.pushback(symbol);
            node.compassPt = compass_pt(lexer);
        } else if (symbol.type == DotParsedSymbol.TYPE_ID) {
            node.portId = symbol.getValueAsString();
            symbol = lexer.lex();
            if (symbol.type == DotParsedSymbol.TYPE_COLON) {
                node.compassPt = compass_pt(lexer);
            } else {
                lexer.pushback(symbol);
            }
        } else {
            _expected("ID or compass_pt", symbol);
        }

    }

    /*
    compass_pt : (n | ne | e | se | s | sw | w | nw | c | _)
     */
    public String compass_pt(DotLexer lexer) throws DotParseException, IOException {
        DotParsedSymbol symbol = lexer.lex();
        if (symbol.idtype == DotParsedSymbol.IDTYPE_IDENTIFIER) {
            String compassValue = symbol.getValueAsString();
            if (!Arrays.asList(VALID_COMPASS_VALUES).contains(compassValue)) {
                _expected("compass value - one of " + String.join(",", VALID_COMPASS_VALUES), symbol);
            }
            return compassValue;
        } else {
            _expected("compass value - one of " + String.join(",", VALID_COMPASS_VALUES), symbol);
        }
        return null;
    }

    /*
    edgeRHS : edgeop (node_id | subgraph) [ edgeRHS ]
     */
    public Edge edge_rhs(ConnectableObject from, List<Edge> edges, boolean isDirectedGraph, DotLexer lexer) throws DotParseException, IOException {
        DotParsedSymbol symbol = lexer.lex();
        if (symbol.type == DotParsedSymbol.TYPE_ARROW) {
            if (!isDirectedGraph) {
                _expected("--", symbol);
            }
        } else if (symbol.type == DotParsedSymbol.TYPE_MINUSMINUS) {
            if (isDirectedGraph) {
                _expected("->", symbol);
            }
        }
        symbol = lexer.lex();
        ConnectableObject to = null;
        if (symbol.type == DotParsedSymbol.TYPE_ID) {
            lexer.pushback(symbol);
            to = node_id(lexer);
        } else if (symbol.type == DotParsedSymbol.TYPE_KEYWORD_SUBGRAPH || symbol.type == DotParsedSymbol.TYPE_BRACE_OPEN) {
            lexer.pushback(symbol);
            to = subgraph(isDirectedGraph, lexer);
        }
        Edge edge = null;
        if (to != null) {
            edge = new Edge(isDirectedGraph, from, to);
            edges.add(edge);
        }

        symbol = lexer.lex();
        if (symbol.type == DotParsedSymbol.TYPE_ARROW || symbol.type == DotParsedSymbol.TYPE_MINUSMINUS) {
            lexer.pushback(symbol);
            edge = edge_rhs(to, edges, isDirectedGraph, lexer);
        } else {
            lexer.pushback(symbol);
        }
        return edge;

    }

    /*
    subgraph : [ 'subgraph' [ ID ] ] '{' stmt_list '}'
     */
    public SubGraph subgraph(boolean isDirectedGraph, DotLexer lexer) throws DotParseException, IOException {
        DotParsedSymbol symbol = lexer.lex();
        String id = null;
        if (symbol.type == DotParsedSymbol.TYPE_KEYWORD_SUBGRAPH) {
            symbol = lexer.lex();
            if (symbol.type == DotParsedSymbol.TYPE_ID) {
                id = symbol.getValueAsString();
                symbol = lexer.lex();
            }
        }
        _expect(DotParsedSymbol.TYPE_BRACE_OPEN, "{", symbol);

        List<Edge> edges = new ArrayList<>();
        List<NodeIdToAttributes> standaloneNodes = new ArrayList<>();
        AttributesBag graphAttributes = new AttributesBag();
        AttributesBag nodeAttributes = new AttributesBag();
        AttributesBag edgeAttributes = new AttributesBag();
        stmt_list(edges, standaloneNodes, graphAttributes, nodeAttributes, edgeAttributes, isDirectedGraph, lexer);
        _expect(lexer, DotParsedSymbol.TYPE_BRACE_CLOSE, "}");
        SubGraph graph = new SubGraph(isDirectedGraph);
        graph.edges = edges;
        graph.nodes = standaloneNodes;
        graph.id = id;
        graph.graphAttributes = graphAttributes;
        graph.nodeAttributes = nodeAttributes;
        graph.edgeAttributes = edgeAttributes;
        return graph;
    }

    private void _expect(DotLexer lexer, int symbolType, String expected) throws DotParseException, IOException {
        DotParsedSymbol found = lexer.lex();
        if (found.type != symbolType) {
            throw new DotParseException("Expected " + expected + ", but " + found.getValueAsString() + " found");
        }
    }

    private void _expected(String expected, DotParsedSymbol found) throws DotParseException {
        throw new DotParseException("Expected " + expected + ", but " + found.getValueAsString() + " found");
    }

    private void _expect(int symbolType, String typeString, DotParsedSymbol symbol) throws DotParseException {
        if (symbol.type != symbolType) {
            _expected(typeString, symbol);
        }
    }

}
