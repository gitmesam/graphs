package com.jpexs.graphs.gui;

import com.jpexs.graphs.structure.CodeStructureChangerProgressListener;
import com.jpexs.graphs.structure.CodeStructureDetector;
import com.jpexs.graphs.structure.CodeStructureDetectorProgressListener;
import com.jpexs.graphs.structure.DecisionList;
import com.jpexs.graphs.structure.Edge;
import com.jpexs.graphs.structure.EdgeType;
import com.jpexs.graphs.structure.EndIfNodeInjector;
import com.jpexs.graphs.structure.MultiNodeJoiner;
import com.jpexs.graphs.structure.nodes.EndIfNode;
import com.jpexs.graphs.structure.nodes.MultiNode;
import com.jpexs.graphs.structure.nodes.Node;
import guru.nidi.graphviz.model.MutableGraph;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.jpexs.graphs.structure.nodes.EditableNode;
import com.jpexs.graphs.structure.nodes.EditableEndIfNode;
import com.jpexs.graphs.structure.nodes.EditableMultiNode;

/**
 *
 * @author JPEXS
 */
public class DetectCodeStructureOperation extends AbstractOperation {

    public DetectCodeStructureOperation(String text) {
        super(text);
    }

    @Override
    public void executeOnMutableGraph(Set<EditableNode> nodes, Map<Node, AttributesBag> nodeAttributesMap, Map<Edge<EditableNode>, AttributesBag> edgeAttributesMap, Map<Edge<EditableNode>, String> edgeCompassesMap) {
        CodeStructureDetector<EditableNode> det = new CodeStructureDetector<>();
        EditableNode startNode = nodes.iterator().next();
        final EditableNode fStartNode = startNode;
        final EndIfNodeInjector<EditableNode> endIfInjector = new EndIfNodeInjector<>();
        CodeStructureChangerProgressListener<EditableNode> listener = new CodeStructureChangerProgressListener<EditableNode>() {

            private EditableNode startNode = fStartNode;

            @Override
            public EditableNode endIfDetected(EditableNode decisionNode, List<EditableNode> endBranchNodes, EditableNode afterNode) {
                return endIfInjector.injectEndIf(decisionNode, endBranchNodes, afterNode);
            }

            @Override
            public void step() {
                DetectCodeStructureOperation.this.step(currentGraph);
                regenerate();
            }

            @Override
            public void edgeMarked(Edge<EditableNode> edge, EdgeType edgeType) {
                String color = "black";
                String label = "";
                boolean alreadyHasColor = edgeAttributesMap.containsKey(edge) && edgeAttributesMap.get(edge).containsKey("color");
                switch (edgeType) {
                    case BACK:
                        color = "darkorchid1";
                        if (!edgeCompassesMap.containsKey(edge)) {
                            edgeCompassesMap.put(edge, ":");
                        }
                        String compass = edgeCompassesMap.get(edge);
                        String compasses[] = compass.split(":");
                        String newcompass = (edge.from.getNext().size() > 1 ? "se" : "") + ":ne";
                        if (compasses.length > 0) {
                            newcompass = compasses[0] + ":ne";
                        }
                        edgeCompassesMap.put(edge, newcompass);
                        label = "back";
                        break;
                    case GOTO:
                        color = "brown";
                        label = "goto";
                        break;
                    case OUTSIDEIF:
                        if (alreadyHasColor) {
                            return;
                        }
                        color = "red";
                        label = "outside";
                        break;
                }
                DetectCodeStructureOperation.this.markEdge(edgeAttributesMap, edge, color, label);
                regenerate();
            }

            @Override
            public void nodeSelected(EditableNode node) {
                DetectCodeStructureOperation.this.hilightOneNode(nodes, nodeAttributesMap, node);
                regenerate();
            }

            @Override
            public void updateDecisionLists(Map<Edge<EditableNode>, DecisionList<EditableNode>> decistionLists) {
                DetectCodeStructureOperation.this.updateDecisionLists(currentGraph, decistionLists, edgeAttributesMap);
                regenerate();
            }

            @Override
            public void noNodeSelected() {
                DetectCodeStructureOperation.this.hilightNoNode(nodes, nodeAttributesMap);
                regenerate();
            }

            @Override
            public void endIfAdded(EditableEndIfNode endIfNode) {
                nodes.add(endIfNode);
                for (Node prev : endIfNode.getPrev()) {
                    @SuppressWarnings("unchecked")
                    EditableNode prevEditable = (EditableNode) prev;
                    edgeCompassesMap.put(new Edge<>(prevEditable, endIfNode), "s:");
                }
                for (Node next : endIfNode.getNext()) {
                    @SuppressWarnings("unchecked")
                    EditableNode nextEditable = (EditableNode) next;
                    edgeCompassesMap.put(new Edge<>(endIfNode, nextEditable), "s:");
                }
                @SuppressWarnings("unchecked")
                EditableNode ifNode = (EditableNode) endIfNode.getIfNode();
                List<Node> ifNodeNext = ifNode.getNext();
                @SuppressWarnings("unchecked")
                EditableNode onTrue = (EditableNode) ifNodeNext.get(0);
                @SuppressWarnings("unchecked")
                EditableNode onFalse = (EditableNode) ifNodeNext.get(1);
                Edge<EditableNode> onTrueEdge = new Edge<>(ifNode, onTrue);
                Edge<EditableNode> onFalseEdge = new Edge<>(ifNode, onFalse);
                edgeCompassesMap.put(onTrueEdge, "sw:n");
                edgeCompassesMap.put(onFalseEdge, "se:n");
                if (!edgeAttributesMap.containsKey(onTrueEdge)) {
                    edgeAttributesMap.put(onTrueEdge, new AttributesBag());
                }
                if (!edgeAttributesMap.containsKey(onFalseEdge)) {
                    edgeAttributesMap.put(onFalseEdge, new AttributesBag());
                }
                AttributesBag onTrueAttr = edgeAttributesMap.get(onTrueEdge);
                AttributesBag onFalseAttr = edgeAttributesMap.get(onFalseEdge);
                if (!onTrueAttr.containsKey("color")) {
                    onTrueAttr.put("color", "darkgreen");
                }
                if (!onFalseAttr.containsKey("color")) {
                    onFalseAttr.put("color", "red");
                }

                @SuppressWarnings("unchecked")
                EditableNode onTrue2 = (EditableNode) endIfNode.getPrev().get(0);
                @SuppressWarnings("unchecked")
                EditableNode onFalse2 = (EditableNode) endIfNode.getPrev().get(1);
                onTrueEdge = new Edge<>(onTrue2, endIfNode);
                onFalseEdge = new Edge<>(onFalse2, endIfNode);
                edgeCompassesMap.put(onTrueEdge, "s:nw");
                edgeCompassesMap.put(onFalseEdge, "s:ne");
                if (!edgeAttributesMap.containsKey(onTrueEdge)) {
                    edgeAttributesMap.put(onTrueEdge, new AttributesBag());
                }
                if (!edgeAttributesMap.containsKey(onFalseEdge)) {
                    edgeAttributesMap.put(onFalseEdge, new AttributesBag());
                }
                markTrueFalseOrder(startNode, new LinkedHashSet<>(), edgeAttributesMap);
                regenerate();
            }

            private void regenerate() {
                regenerateGraph(nodes, nodeAttributesMap, edgeAttributesMap, edgeCompassesMap);
            }

            @Override
            public void multiNodeJoined(EditableMultiNode node) {
                List<String> labels = new ArrayList<>();
                String shape;
                /*=null;*/
                for (Node subNode : node.getAllSubNodes()) {
                    if (subNode.getId().equals("start")) {
                        startNode = node;
                    }
                    if (nodeAttributesMap.containsKey(subNode)) {
                        AttributesBag attr = nodeAttributesMap.get(subNode);
                        if (attr.containsKey("label")) {
                            labels.add(attr.get("label").toString());
                        } else {
                            labels.add(subNode.getId());
                        }
                        /*if (attr.containsKey("shape")) {
                            String nshape = attr.get("shape").toString();
                            if (shape == null) {
                                shape = nshape;
                            }
                        } else {
                            shape = "";
                        }*/
                    } else {
                        labels.add(subNode.getId());
                    }
                    nodes.remove(subNode);
                }
                shape = "box";

                AttributesBag nattr = new AttributesBag();

                if (!labels.isEmpty()) {
                    nattr.put("label", String.join("\\l", labels));
                    nodeAttributesMap.put(node, nattr);
                }
                if (/*shape != null &&*/!shape.isEmpty()) {
                    nattr.put("shape", shape);
                    nodeAttributesMap.put(node, nattr);
                }
                if (node == startNode) {
                    //make startNode first again
                    List<com.jpexs.graphs.structure.nodes.EditableNode> oldCopy = new ArrayList<>(nodes);
                    nodes.clear();
                    nodes.add(startNode);
                    nodes.addAll(oldCopy);
                } else {
                    nodes.add(node);
                }
                markTrueFalseOrder(startNode, new LinkedHashSet<>(), edgeAttributesMap);
            }
        };
        endIfInjector.addListener(listener);
        det.addListener(listener);
        MultiNodeJoiner<EditableNode> multiNodeJoiner = new MultiNodeJoiner<>();
        multiNodeJoiner.addListener(listener);

        markTrueFalseOrder(startNode, new LinkedHashSet<>(), edgeAttributesMap);
        startNode = multiNodeJoiner.createMultiNodes(startNode);
        det.detect(startNode, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    private void markTrueFalseOrder(EditableNode n, Set<EditableNode> visited, Map<Edge<EditableNode>, AttributesBag> edgeAttributesMap) {
        if (visited.contains(n)) {
            return;
        }
        String branchLabels[] = new String[]{"+", "-"};
        if (n instanceof EndIfNode) {
            for (int i = 0; i < n.getPrev().size(); i++) {
                Node prev = n.getPrev().get(i);
                @SuppressWarnings("unchecked")
                EditableNode prevEditable = (EditableNode) prev;
                Edge<EditableNode> edge = new Edge<>(prevEditable, n);
                if (!edgeAttributesMap.containsKey(edge)) {
                    edgeAttributesMap.put(edge, new AttributesBag());
                }
                edgeAttributesMap.get(edge).put("headlabel", branchLabels[i]);
            }
        }
        visited.add(n);
        if (n.getNext().size() == 2) { //more than 2 = some of them are gotos
            for (int i = 0; i < n.getNext().size(); i++) {
                Node next = n.getNext().get(i);
                @SuppressWarnings("unchecked")
                EditableNode nextEditable = (EditableNode) next;
                Edge<EditableNode> edge = new Edge<>(n, nextEditable);
                if (!edgeAttributesMap.containsKey(edge)) {
                    edgeAttributesMap.put(edge, new AttributesBag());
                }
                edgeAttributesMap.get(edge).put("taillabel", branchLabels[i]);
            }
        }
        for (Node next : n.getNext()) {
            @SuppressWarnings("unchecked")
            EditableNode nextEditable = (EditableNode) next;
            markTrueFalseOrder(nextEditable, visited, edgeAttributesMap);
        }
    }

    private void updateDecisionLists(MutableGraph g, Map<Edge<EditableNode>, DecisionList<EditableNode>> decistionLists, Map<Edge<EditableNode>, AttributesBag> edgeAttributesMap) {
        boolean displayDecisionLists = false;
        for (Edge<EditableNode> edge : decistionLists.keySet()) {
            if (!edgeAttributesMap.containsKey(edge)) {
                edgeAttributesMap.put(edge, new AttributesBag());
            }
            if (displayDecisionLists) {
                edgeAttributesMap.get(edge).put("label", decistionLists.get(edge).isEmpty() ? "(empty)" : decistionLists.get(edge).toString());
                edgeAttributesMap.get(edge).put("fontcolor", "red");
            }
        }
    }
}