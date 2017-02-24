package it.unibz.inf.ontop.mapping;


import com.google.common.collect.ImmutableSet;
import it.unibz.inf.ontop.model.AtomPredicate;
import it.unibz.inf.ontop.pivotalrepr.IntermediateQuery;
import it.unibz.inf.ontop.pivotalrepr.MetadataForQueryOptimization;

import java.util.Optional;

/**
 * TODO: explain
 */
public interface Mapping {

    MappingMetadata getMetadata();

    MetadataForQueryOptimization getMetadataForOptimization();

    Optional<IntermediateQuery> getDefinition(AtomPredicate predicate);

    ImmutableSet<AtomPredicate> getPredicates();
}
