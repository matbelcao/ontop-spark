package it.unibz.inf.ontop.model.term.functionsymbol;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import it.unibz.inf.ontop.iq.node.VariableNullability;
import it.unibz.inf.ontop.iq.request.DefinitionPushDownRequest;
import it.unibz.inf.ontop.model.term.ImmutableFunctionalTerm.FunctionalTermDecomposition;
import it.unibz.inf.ontop.model.term.ImmutableTerm;
import it.unibz.inf.ontop.model.term.TermFactory;
import it.unibz.inf.ontop.model.term.functionsymbol.impl.AggregationSimplificationImpl;
import it.unibz.inf.ontop.model.type.RDFTermType;

import java.util.Optional;

public interface SPARQLAggregationFunctionSymbol extends SPARQLFunctionSymbol {

    Optional<AggregationSimplification> decomposeIntoDBAggregation(
            ImmutableList<? extends ImmutableTerm> subTerms, ImmutableList<ImmutableSet<RDFTermType>> possibleRDFTypes,
            VariableNullability variableNullability, TermFactory termFactory);


    interface AggregationSimplification {
        FunctionalTermDecomposition getDecomposition();
        ImmutableSet<DefinitionPushDownRequest> getPushDownRequests();

        static AggregationSimplification create(FunctionalTermDecomposition decomposition,
                                                ImmutableSet<DefinitionPushDownRequest> pushDownRequests) {
            return new AggregationSimplificationImpl(decomposition, pushDownRequests);
        }
    }
}
