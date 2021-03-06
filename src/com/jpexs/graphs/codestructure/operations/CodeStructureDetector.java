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
package com.jpexs.graphs.codestructure.operations;

import com.jpexs.graphs.codestructure.Decision;
import com.jpexs.graphs.codestructure.DecisionList;
import com.jpexs.graphs.codestructure.Edge;
import com.jpexs.graphs.codestructure.nodes.Node;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author JPEXS
 * @param <N> Node type
 */
public class CodeStructureDetector<N extends Node> {

    private List<N> todoList = new ArrayList<>();
    private List<Node> alreadyProcessed = new ArrayList<>();
    private Map<Edge<N>, DecisionList<N>> decistionLists = new HashMap<>();
    private List<N> rememberedDecisionNodes = new ArrayList<>();
    private List<N> waiting = new ArrayList<>();
    private List<Node> loopContinues = new ArrayList<>();
    private List<Edge<N>> backEdges = new ArrayList<>();
    private List<Edge<N>> gotoEdges = new ArrayList<>();
    private List<Edge<N>> exitIfEdges = new ArrayList<>();
    private Set<Edge<N>> ignoredEdges = new LinkedHashSet<>();

    public Node detect(N head, List<Node> loopContinues, List<Edge<N>> gotoEdges, List<Edge<N>> backEdges, List<Edge<N>> exitIfEdges) {
        Set<N> heads = new LinkedHashSet<>();
        heads.add(head);
        Collection<N> multiHeads = detect(heads, loopContinues, gotoEdges, backEdges, exitIfEdges);
        return multiHeads.toArray(new Node[1])[0];
    }

    public Collection<N> detect(Collection<N> heads, List<Node> loopContinues, List<Edge<N>> gotoEdges, List<Edge<N>> backEdges, List<Edge<N>> exitIfEdges) {
        todoList.addAll(heads);
        walk();
        loopContinues.addAll(this.loopContinues);
        gotoEdges.addAll(this.gotoEdges);
        backEdges.addAll(this.backEdges);
        exitIfEdges.addAll(this.exitIfEdges);
        return heads;
    }

    private boolean walk() {

        walkDecisionLists();
        fireNoNodeSelected();
        for (int i = 0; i < waiting.size(); i++) {
            N cek = waiting.get(i);
            Set<Node> visited = new LinkedHashSet<>();
            Set<Node> insideLoopNodes = new LinkedHashSet<>();
            Set<Edge<N>> loopExitEdges = new LinkedHashSet<>();
            Set<Edge<N>> loopContinueEdges = new LinkedHashSet<>();

            if (leadsTo(cek, cek, insideLoopNodes, loopExitEdges, loopContinueEdges, visited)) { //it waits for self => loop

                N continueNode = cek;
                for (Edge<N> edge : loopContinueEdges) {
                    fireEdgeMarked(edge, DetectedEdgeType.BACK);
                }

                Set<N> currentWaiting = new LinkedHashSet<>();
                currentWaiting.addAll(waiting);
                currentWaiting.remove(continueNode);
                loopContinues.add(continueNode);
                backEdges.addAll(loopContinueEdges);
                Set<Edge<N>> cekajiciVstupniEdges = new LinkedHashSet<>();
                for (N c : currentWaiting) {
                    for (N pc : getPrevNodes(c)) {
                        cekajiciVstupniEdges.add(new Edge<>(pc, c));
                    }
                }
                ignoredEdges.addAll(loopContinueEdges);

                for (N next : getNextNodes(continueNode)) {
                    if (!insideLoopNodes.contains(next)) {
                        cekajiciVstupniEdges.add(new Edge<>(continueNode, next));
                        currentWaiting.add(next);
                    }
                }

                ignoredEdges.addAll(cekajiciVstupniEdges); //zakonzervovat cekajici

                waiting.clear();
                todoList.add(continueNode);
                walk();
                ignoredEdges.removeAll(cekajiciVstupniEdges);
                alreadyProcessed.removeAll(currentWaiting);

                DecisionList<N> loopDecisionList = calculateDecisionListFromPrevNodes(continueNode, getPrevNodes(continueNode) /*bez ignored*/).lockForChanges();

                for (Edge<N> edge : cekajiciVstupniEdges) {
                    if (alreadyProcessed.contains(edge.from) && !decistionLists.containsKey(edge)) {
                        decistionLists.put(edge, loopDecisionList);
                    }
                }
                if (currentWaiting.isEmpty()) {
                    return true;
                }
                todoList.addAll(currentWaiting);
                walk();
                return true;
            }
        }
        return false;
    }

    private boolean leadsTo(N nodeSearchIn, N nodeSearchWhich, Set<Node> insideLoopNodes, Set<Edge<N>> noLeadEdges, Set<Edge<N>> foundEdges, Set<Node> visited) {
        if (visited.contains(nodeSearchIn)) {
            return insideLoopNodes.contains(nodeSearchIn);
        }
        visited.add(nodeSearchIn);
        for (Node next : getNextNodes(nodeSearchIn)) {
            @SuppressWarnings("unchecked")
            N nextT = (N) next;
            if (next.equals(nodeSearchWhich)) {
                foundEdges.add(new Edge<>(nodeSearchIn, nextT));
                return true;
            }
        }
        boolean ret = false;
        Set<Edge<N>> currentNoLeadNodes = new LinkedHashSet<>();
        for (N next : getNextNodes(nodeSearchIn)) {
            if (leadsTo(next, nodeSearchWhich, insideLoopNodes, currentNoLeadNodes, foundEdges, visited)) {
                insideLoopNodes.add(next);
                ret = true;
            } else {
                currentNoLeadNodes.add(new Edge<>(nodeSearchIn, next));
            }
        }
        if (ret == true) {
            noLeadEdges.addAll(currentNoLeadNodes);
        }
        return ret;
    }

    private boolean removeExitPointFromPrevDlists(N prevNode, N node, N exitPoint, Set<N> processedNodes) {
        boolean lastOne = false;
        if (processedNodes.contains(node)) {
            return false;
        }
        processedNodes.add(node);
        if (exitPoint.equals(prevNode)) {
            int insideIfBranchIndex = prevNode.getNext().indexOf(node);
            for (int branchIndex = 0; branchIndex < exitPoint.getNext().size(); branchIndex++) {
                if (branchIndex != insideIfBranchIndex) {
                    @SuppressWarnings("unchecked")
                    N branchNodeT = (N) exitPoint.getNext().get(branchIndex);

                    Edge<N> exitEdge = new Edge<>(exitPoint, branchNodeT);
                    exitIfEdges.add(exitEdge);
                    fireEdgeMarked(exitEdge, DetectedEdgeType.OUTSIDEIF);
                }
            }

            return true;
        }
        Edge<N> edge = new Edge<>(prevNode, node);
        DecisionList<N> decisionList = decistionLists.get(edge);
        if (decisionList != null) {
            if (!decisionList.isEmpty() && decisionList.get(decisionList.size() - 1).getIfNode().equals(exitPoint)) {
                DecisionList<N> truncDecisionList = new DecisionList<>();
                truncDecisionList.addAll(decisionList);
                truncDecisionList.remove(truncDecisionList.size() - 1);
                decistionLists.put(edge, truncDecisionList);
            }
        }

        if (!lastOne) {
            for (Node prev : prevNode.getPrev()) {
                if (loopContinues.contains(prev)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                N prevT = (N) prev;
                if (removeExitPointFromPrevDlists(prevT, prevNode, exitPoint, processedNodes)) {
                    return true;
                }
            }
        }
        return false;
    }

    private DecisionList<N> calculateDecisionListFromPrevNodes(N BOD, List<N> prevNodes) {
        DecisionList<N> nextDecisionList;
        List<DecisionList<N>> prevDecisionLists = new ArrayList<>();
        List<N> decisionListNodes = new ArrayList<>(prevNodes);
        for (N prevNode : prevNodes) {
            Edge<N> edge = new Edge<>(prevNode, BOD);
            DecisionList<N> prevDL = decistionLists.get(edge);
            if (prevDL == null) {
                System.err.println("WARNING - no decisionList for edge " + edge);
            }
            prevDecisionLists.add(prevDL);
        }

        if (prevDecisionLists.isEmpty()) {
            nextDecisionList = new DecisionList<>();
        } else if (prevDecisionLists.size() == 1) {
            nextDecisionList = new DecisionList<>(prevDecisionLists.get(0));
        } else {
            //Remove decisionLists, which are remembered from last time as unstructured
            for (int i = prevDecisionLists.size() - 1; i >= 0; i--) {
                if (prevDecisionLists.get(i).containsOneOfNodes(rememberedDecisionNodes)) {
                    Edge<N> gotoEdge = new Edge<>(prevNodes.get(i), BOD);
                    gotoEdges.add(gotoEdge);
                    fireEdgeMarked(gotoEdge, DetectedEdgeType.GOTO);
                    prevDecisionLists.remove(i);
                }
            }

            loopcheck:
            while (true) {

                //search for same decision lists, join them to endif
                for (int i = 0; i < prevDecisionLists.size(); i++) {
                    DecisionList<N> decisionListI = prevDecisionLists.get(i);
                    Set<Integer> sameIndices = new TreeSet<>(new Comparator<Integer>() {
                        @Override
                        public int compare(Integer o1, Integer o2) {
                            return o2 - o1;
                        }
                    });
                    sameIndices.add(i);
                    if (decisionListI.isEmpty()) {
                        continue;
                    }
                    for (int j = 0; j < prevDecisionLists.size(); j++) {
                        if (i != j) {
                            DecisionList<N> decisionListJ = prevDecisionLists.get(j);
                            if (decisionListJ.ifNodesEquals(decisionListI)) {
                                sameIndices.add(j);
                            }
                        }
                    }
                    int numSame = sameIndices.size();
                    if (numSame > 1) { //Actually, there can be more than 2 branches - it's not an if, but... it's kind of structured...
                        N decisionNode = decisionListI.get(decisionListI.size() - 1).getIfNode();
                        int numBranches = getNextNodes(decisionNode).size();
                        if (numSame == numBranches) {
                            DecisionList<N> shorterDecisionList = new DecisionList<>(decisionListI);
                            shorterDecisionList.remove(shorterDecisionList.size() - 1);
                            List<N> endBranchNodes = new ArrayList<>();
                            for (int index : sameIndices) {
                                Decision<N> decision = prevDecisionLists.get(index).get(decisionListI.size() - 1);
                                int branchNum = decision.getBranchNum();
                                prevDecisionLists.remove(index);
                                N prev = decisionListNodes.remove(index);
                                if (branchNum == 0) {
                                    endBranchNodes.add(0, prev);
                                } else {
                                    endBranchNodes.add(prev);
                                }
                            }

                            fireNoNodeSelected();
                            N endIfNode = fireEndIfDetected(decisionNode, endBranchNodes, BOD);

                            alreadyProcessed.add(endIfNode);
                            decisionListNodes.add(endIfNode);
                            prevDecisionLists.add(shorterDecisionList);
                            decistionLists.put(new Edge<>(endIfNode, BOD), shorterDecisionList);
                            fireUpdateDecisionLists(decistionLists);
                            fireStep();
                            continue loopcheck;
                        }
                    }
                }

                int maxDecListSize = 0;
                for (int i = 0; i < prevDecisionLists.size(); i++) {
                    int size = prevDecisionLists.get(i).size();
                    if (size > maxDecListSize) {
                        maxDecListSize = size;
                    }
                }

                //- order decisionLists by their size, descending
                //- search for decisionlist K, and J, decisionlist J has is same as K and has one more added node
                //- replace the longer one with the shorter version
                //- this means that one branch of ifblock does not finish in endif - it might be return / continue / break or some unstructured goto,
                //- we call that edge an ExitEdge of the if
                loopsize:
                for (int findSize = maxDecListSize; findSize > 1; findSize--) {
                    for (int j = 0; j < prevDecisionLists.size(); j++) {
                        DecisionList<N> decisionListJ = prevDecisionLists.get(j);
                        if (decisionListJ.size() == findSize) {
                            for (int k = 0; k < prevDecisionLists.size(); k++) {
                                if (j == k) {
                                    continue;
                                }
                                DecisionList<N> decisionListK = prevDecisionLists.get(k);
                                if (decisionListK.size() == findSize - 1) {
                                    DecisionList<N> decisionListJKratsi = new DecisionList<>();
                                    decisionListJKratsi.addAll(decisionListJ);
                                    decisionListJKratsi.remove(decisionListJKratsi.size() - 1);
                                    if (decisionListJKratsi.ifNodesEquals(decisionListK)) {

                                        prevDecisionLists.set(j, decisionListJKratsi.lockForChanges());
                                        decistionLists.put(new Edge<>(decisionListNodes.get(j), BOD), decisionListJKratsi);
                                        Decision<N> decisionK = decisionListK.get(decisionListK.size() - 1);
                                        Decision<N> decisionJ = decisionListJ.get(decisionListJKratsi.size() - 1);
                                        rememberedDecisionNodes.add(decisionK.getIfNode());
                                        N decisionNode = decisionK.getIfNode();

                                        Decision<N> exitDecision = decisionListJ.get(decisionListJ.size() - 1);
                                        N exitNode = exitDecision.getIfNode();

                                        //----
                                        List<N> endBranchNodes = new ArrayList<>();
                                        int higherIndex = j > k ? j : k;
                                        int lowerIndex = j < k ? j : k;

                                        N longerPrev = decisionListNodes.get(j);

                                        //Trick: remove higher index first. If we removed the lower first, higher indices would change.
                                        prevDecisionLists.remove(higherIndex);
                                        prevDecisionLists.remove(lowerIndex);
                                        N prevNodeK = decisionListNodes.get(k);
                                        N prevNodeJ = decisionListNodes.get(j);
                                        decisionListNodes.remove(higherIndex);
                                        decisionListNodes.remove(lowerIndex);

                                        endBranchNodes.add(decisionJ.getBranchNum() == 0 ? prevNodeJ : prevNodeK);
                                        endBranchNodes.add(decisionK.getBranchNum() == 1 ? prevNodeK : prevNodeJ);

                                        fireNoNodeSelected();

                                        DecisionList<N> shorterDecisionList = new DecisionList<>(decisionListK);
                                        shorterDecisionList.remove(shorterDecisionList.size() - 1);
                                        N endIfNode = fireEndIfDetected(decisionNode, endBranchNodes, BOD);
                                        alreadyProcessed.add(endIfNode);
                                        decisionListNodes.add(endIfNode);
                                        prevDecisionLists.add(shorterDecisionList);
                                        decistionLists.put(new Edge<>(endIfNode, BOD), shorterDecisionList);
                                        //----
                                        fireUpdateDecisionLists(decistionLists);
                                        fireStep();

                                        removeExitPointFromPrevDlists(longerPrev, endIfNode, exitNode, new LinkedHashSet<>());

                                        fireUpdateDecisionLists(decistionLists);
                                        fireStep();
                                        continue loopcheck;
                                    }
                                }
                            }
                        }
                    }
                }
                break; //if no more left found, exit loop
            } //loopcheck

            if (prevDecisionLists.isEmpty()) { //no more prevNodes left
                nextDecisionList = new DecisionList<>();
            } else if (prevDecisionLists.size() == 1) { //onePrev node
                nextDecisionList = new DecisionList<>(prevDecisionLists.get(0));
            } else {
                //more prevNodes remaining

                DecisionList<N> prefix = new DecisionList<>();
                Decision<N> nextDecision;
                int numInPrefix = 0;
                looppocet:
                while (true) {
                    nextDecision = null;
                    for (DecisionList<N> decisionList : prevDecisionLists) {
                        if (decisionList.size() == numInPrefix) {
                            break looppocet;
                        }

                        Decision<N> currentDecision = decisionList.get(numInPrefix);
                        if (nextDecision == null) {
                            nextDecision = currentDecision;
                        }
                        if (!currentDecision.getIfNode().equals(nextDecision.getIfNode())) {
                            break looppocet;
                        }
                    }
                    prefix.add(nextDecision);
                    numInPrefix++;
                }
                for (int i = 0; i < prevDecisionLists.size(); i++) {
                    DecisionList<N> decisionList = prevDecisionLists.get(i);
                    /*if (decisionList.size() > prefix.size()) {
                        System.out.println("decisionList=" + decisionList);
                        System.out.println("prefix=" + prefix);
                        System.out.println("dlsize=" + decisionList.size());
                        System.out.println("prefixsize=" + prefix.size());
                        Edge<N> gotoEdge = new Edge<>(decisionListNodes.get(i), BOD);
                        gotoEdges.add(gotoEdge);
                        fireEdgeMarked(gotoEdge, DetectedEdgeType.GOTO);
                    }*/
                    if (decisionList.size() > prefix.size()) {
                        for (int j = decisionList.size() - 1; j >= prefix.size(); j--) {
                            Decision<N> exitDecision = decisionList.get(j);
                            N exitNode = exitDecision.getIfNode();
                            removeExitPointFromPrevDlists(decisionListNodes.get(i), BOD, exitNode, new LinkedHashSet<>());
                        }
                    }
                }
                N nextBod = BOD;
                if (!prefix.isEmpty()) {

                    List<N> endBranchNodes = new ArrayList<>();
                    N decisionNode = null;
                    for (int i = 0; i < prevDecisionLists.size(); i++) {
                        Decision<N> decision = prevDecisionLists.get(i).get(prefix.size() - 1);
                        decisionNode = decision.getIfNode();
                        int branchNum = decision.getBranchNum();
                        N prev = decisionListNodes.get(i);
                        if (branchNum == 0) {
                            endBranchNodes.add(0, prev);
                        } else {
                            endBranchNodes.add(prev);
                        }
                    }
                    N endIfNode = fireEndIfDetected(decisionNode, endBranchNodes, BOD);
                    alreadyProcessed.add(endIfNode);
                    nextBod = endIfNode;
                }

                //just merge of unstructured branches
                for (Node prev : decisionListNodes) {
                    @SuppressWarnings("unchecked")
                    N prevT = (N) prev;
                    decistionLists.put(new Edge<>(prevT, nextBod), prefix);
                }
                fireStep();
                nextDecisionList = prefix;
            }
        }
        return nextDecisionList;
    }

    private List<N> getPrevNodes(N sourceNode) {
        List<N> ret = new ArrayList<>();
        for (Node prev : sourceNode.getPrev()) {
            @SuppressWarnings("unchecked")
            N prevT = (N) prev;
            if (!ignoredEdges.contains(new Edge<>(prevT, sourceNode))) {
                ret.add((N) prevT);
            }
        }
        return ret;
    }

    private List<N> getNextNodes(N sourceNode) {
        List<N> ret = new ArrayList<>();
        for (Node next : sourceNode.getNext()) {
            @SuppressWarnings("unchecked")
            N nextT = (N) next;
            if (!ignoredEdges.contains(new Edge<>(sourceNode, nextT))) {
                ret.add((N) nextT);
            }
        }
        return ret;
    }

    private void walkDecisionLists() {
        do {
            N currentPoint = todoList.remove(0);
            if (alreadyProcessed.contains(currentPoint)) {
                continue;
            }
            List<N> prevNodes = getPrevNodes(currentPoint);
            boolean vsechnyPrevZpracovane = true;
            for (Node prevNode : prevNodes) {
                if (!alreadyProcessed.contains(prevNode)) {
                    vsechnyPrevZpracovane = false;
                    break;
                }
            }

            if (!vsechnyPrevZpracovane) {
                if (!waiting.contains(currentPoint)) {
                    waiting.add(currentPoint);
                }
            } else {
                waiting.remove(currentPoint);
                DecisionList<N> mergedDecisionList = calculateDecisionListFromPrevNodes(currentPoint, prevNodes);
                alreadyProcessed.add(currentPoint);
                List<N> nextNodes = getNextNodes(currentPoint);

                for (int branch = 0; branch < nextNodes.size(); branch++) {
                    N next = nextNodes.get(branch);
                    Edge<N> edge = new Edge<>(currentPoint, next);
                    DecisionList<N> nextDecisionList = new DecisionList<>(mergedDecisionList);
                    if (nextNodes.size() > 1) {
                        nextDecisionList.add(new Decision<>(currentPoint, branch));
                    }
                    decistionLists.put(edge, nextDecisionList.lockForChanges());
                    todoList.add(next);
                }
                fireNodeSelected(currentPoint);
                fireUpdateDecisionLists(decistionLists);
                fireStep();
            }
        } while (!todoList.isEmpty());
    }

    private List<CodeStructureDetectorProgressListener<N>> listeners = new ArrayList<>();

    public void addListener(CodeStructureDetectorProgressListener<N> l) {
        listeners.add(l);
    }

    public void removeListener(CodeStructureDetectorProgressListener<N> l) {
        listeners.remove(l);
    }

    private N fireEndIfDetected(N decisionNode, List<N> endBranchNodes, N node) {
        List<Edge<N>> beforeEdges = new ArrayList<>();
        for (N prev : endBranchNodes) {
            beforeEdges.add(new Edge<>(prev, node));
        }
        //T endIfNode = afterNode; //fireEndIfDetected(decisionNode, endBranchNodes, BOD);

        for (CodeStructureDetectorProgressListener<N> l : listeners) {
            node = l.endIfDetected(decisionNode, endBranchNodes, node);
        }

        //restore decisionlists of branches
        for (int m = 0; m < node.getPrev().size(); m++) {
            @SuppressWarnings("unchecked")
            N prev = (N) node.getPrev().get(m);
            decistionLists.put(new Edge<>(prev, node), decistionLists.get(beforeEdges.get(m)));
        }
        return node;
    }

    private void fireStep() {
        for (CodeStructureDetectorProgressListener<N> l : listeners) {
            l.step();
        }
    }

    private void fireEdgeMarked(Edge<N> edge, DetectedEdgeType edgeType) {
        for (CodeStructureDetectorProgressListener<N> l : listeners) {
            l.edgeMarked(edge, edgeType);
        }
    }

    private void fireNodeSelected(N node) {
        for (CodeStructureDetectorProgressListener<N> l : listeners) {
            l.nodeSelected(node);
        }
    }

    private void fireUpdateDecisionLists(Map<Edge<N>, DecisionList<N>> decistionLists) {
        for (CodeStructureDetectorProgressListener<N> l : listeners) {
            l.updateDecisionLists(decistionLists);
        }
    }

    private void fireNoNodeSelected() {
        for (CodeStructureDetectorProgressListener<N> l : listeners) {
            l.noNodeSelected();
        }
    }

}
