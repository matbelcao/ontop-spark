package it.unibz.inf.ontop.owlrefplatform.core.optimization;

import java.util.Optional;
import com.google.common.collect.ImmutableList;
import it.unibz.inf.ontop.model.VariableOrGroundTerm;
import it.unibz.inf.ontop.model.DataAtom;
import it.unibz.inf.ontop.model.Variable;
import it.unibz.inf.ontop.owlrefplatform.core.optimization.QueryNodeNavigationTools.NextNodeAndQuery;
import it.unibz.inf.ontop.owlrefplatform.core.optimization.QueryNodeNavigationTools.UpdatedNodeAndQuery;
import it.unibz.inf.ontop.pivotalrepr.*;
import it.unibz.inf.ontop.pivotalrepr.proposal.NodeCentricOptimizationResults;
import it.unibz.inf.ontop.pivotalrepr.proposal.PullVariableOutOfDataNodeProposal;
import it.unibz.inf.ontop.pivotalrepr.proposal.impl.PullVariableOutOfDataNodeProposalImpl;
import it.unibz.inf.ontop.pivotalrepr.proposal.impl.SubstitutionUpPropagationProposalImpl;

import java.util.HashSet;
import java.util.Set;

import static it.unibz.inf.ontop.owlrefplatform.core.optimization.QueryNodeNavigationTools.getDepthFirstNextNode;
import static it.unibz.inf.ontop.owlrefplatform.core.optimization.QueryNodeNavigationTools.getNextNodeAndQuery;

/**
 * TODO: explain
 */
public class PullOutVariableOptimizer implements IntermediateQueryOptimizer {

    @Override
    public IntermediateQuery optimize(IntermediateQuery query) {
        try {
            return pullOutSomeVariables(query);
        } catch (EmptyQueryException e) {
            throw new IllegalStateException("Inconsistency: PullOutVariableOptimizer should not empty the query");
        }
    }

    /**
     * TODO: explain
     *
     * Depth-first exploration
     *
     */
    private IntermediateQuery pullOutSomeVariables(IntermediateQuery initialQuery)
            throws EmptyQueryException {
        // Non-final
        Optional<QueryNode> optionalCurrentNode = initialQuery.getFirstChild(initialQuery.getRootConstructionNode());

        // Non-final
        IntermediateQuery currentQuery = initialQuery;

        Set<Variable> alreadySeenVariables = new HashSet<>();

        while (optionalCurrentNode.isPresent()) {
            final QueryNode currentNode = optionalCurrentNode.get();

            /**
             * Targets: join-like nodes
             */
            if (currentNode instanceof JoinLikeNode) {
                UpdatedNodeAndQuery<JoinLikeNode> updatedJoinLikeNodeAndQuery = optimizeJoinLikeNode(currentQuery,
                        (JoinLikeNode) currentNode);
                NextNodeAndQuery nextNodeAndQuery = optimizeJoinLikeNodeChildren(
                        updatedJoinLikeNodeAndQuery.getNextQuery(), updatedJoinLikeNodeAndQuery.getUpdatedNode());

                optionalCurrentNode = nextNodeAndQuery.getOptionalNextNode();
                currentQuery = nextNodeAndQuery.getNextQuery();
            }
            else if (currentNode instanceof DataNode) {
                NextNodeAndQuery nextNodeAndQuery = optimizeDataNode(currentQuery,
                        (DataNode) currentNode);
                optionalCurrentNode = nextNodeAndQuery.getOptionalNextNode();
                currentQuery = nextNodeAndQuery.getNextQuery();

            }
            else {
                optionalCurrentNode = getDepthFirstNextNode(currentQuery, currentNode);
            }
        }
        return currentQuery;
    }

    /**
     * TODO: explain
     */
    private NextNodeAndQuery optimizeJoinLikeNodeChildren(IntermediateQuery initialQuery, JoinLikeNode initialJoinLikeNode)
            throws EmptyQueryException {

        // Non-final variables
        IntermediateQuery currentQuery = initialQuery;
        QueryNode currentJoinLikeNode = initialJoinLikeNode;
        Optional<QueryNode> optionalCurrentChildNode = currentQuery.getFirstChild(initialJoinLikeNode);

        Set<Variable> alreadySeenVariables = new HashSet<>();

        while (optionalCurrentChildNode.isPresent()) {

            QueryNode childNode = optionalCurrentChildNode.get();

            /**
             * PullOutVariableProposals only concern SubTreeDelimiterNodes
             */
            if (childNode instanceof DataNode) {

                /**
                 * May update alreadySeenVariables (append-only)!!
                 */
                Optional<PullVariableOutOfDataNodeProposal> optionalProposal = buildProposal((DataNode) childNode,
                        alreadySeenVariables);

                /**
                 * Applies the proposal and extracts the next node (and query)
                 * from the results
                 */
                if (optionalProposal.isPresent()) {
                    PullVariableOutOfDataNodeProposal proposal = optionalProposal.get();

                    NodeCentricOptimizationResults<DataNode> results = currentQuery.applyProposal(proposal);

                    currentQuery = results.getResultingQuery();
                    optionalCurrentChildNode = results.getOptionalNextSibling();

                    Optional<QueryNode> optionalCurrentParent = results.getOptionalClosestAncestor();
                    if (!optionalCurrentParent.isPresent()) {
                        throw new IllegalStateException("Missing parent of current node after pulling out some variables");
                    }
                    currentJoinLikeNode = optionalCurrentParent.get();
                }
                else {
                    optionalCurrentChildNode = currentQuery.getNextSibling(childNode);
                }
            }
            else {
                optionalCurrentChildNode = currentQuery.getNextSibling(childNode);
            }
        }

        return new NextNodeAndQuery(getDepthFirstNextNode(currentQuery, currentJoinLikeNode),
                currentQuery);
    }

    /**
     * TODO: explain
     *
     * By default, does nothing
     *
     * Can be overwritten (useful for extensions).
     *
     */
    private UpdatedNodeAndQuery<JoinLikeNode> optimizeJoinLikeNode(IntermediateQuery initialQuery,
                                                                     JoinLikeNode initialJoinLikeNode) {
        return new UpdatedNodeAndQuery<>(initialJoinLikeNode, initialQuery);
    }

    /**
     * May update alreadySeenVariables!
     *
     * TODO: explain it further
     */
    private Optional<PullVariableOutOfDataNodeProposal> buildProposal(DataNode dataNode,
                                                                      Set<Variable> alreadySeenVariables) {
        ImmutableList.Builder<Integer> variableIndexListBuilder = ImmutableList.builder();

        DataAtom dataAtom = dataNode.getProjectionAtom();
        ImmutableList<? extends VariableOrGroundTerm> arguments = dataAtom.getArguments();

        for (int i=0; i < arguments.size(); i++) {
            VariableOrGroundTerm argument = arguments.get(i);
            if (argument instanceof Variable) {
                Variable variable = (Variable) argument;

                /**
                 * Tracks the indexes of variables to "pull out"
                 */
                if (!alreadySeenVariables.add(variable)) {
                    variableIndexListBuilder.add(i);
                }
            }
        }

        ImmutableList<Integer> toReplaceVariableIndexes = variableIndexListBuilder.build();
        if (!toReplaceVariableIndexes.isEmpty()) {
            PullVariableOutOfDataNodeProposal proposal = new PullVariableOutOfDataNodeProposalImpl(dataNode,
                    toReplaceVariableIndexes);
            return Optional.of(proposal);
        }
        else {
            return Optional.empty();
        }
    }

    /**
     * TODO: explain
     *
     */
    private NextNodeAndQuery optimizeDataNode(IntermediateQuery currentQuery, DataNode currentNode)
            throws EmptyQueryException {

        Optional<PullVariableOutOfDataNodeProposal> optionalProposal = buildProposal(currentNode, new HashSet<>());

        if (optionalProposal.isPresent()) {
            PullVariableOutOfDataNodeProposal proposal = optionalProposal.get();
            NodeCentricOptimizationResults<DataNode> results = currentQuery.applyProposal(proposal);

            return getNextNodeAndQuery(results);
        }
        else {
            // NB: a DataNode is not expected to have a child
            return new NextNodeAndQuery(getDepthFirstNextNode(currentQuery, currentNode), currentQuery);
        }
    }
}

