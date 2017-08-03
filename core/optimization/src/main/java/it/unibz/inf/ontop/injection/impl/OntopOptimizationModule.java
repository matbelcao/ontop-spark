package it.unibz.inf.ontop.injection.impl;


import it.unibz.inf.ontop.iq.executor.construction.ConstructionNodeCleaningExecutor;
import it.unibz.inf.ontop.iq.executor.expression.PushDownBooleanExpressionExecutor;
import it.unibz.inf.ontop.iq.executor.expression.PushUpBooleanExpressionExecutor;
import it.unibz.inf.ontop.iq.executor.groundterm.GroundTermRemovalFromDataNodeExecutor;
import it.unibz.inf.ontop.iq.executor.join.InnerJoinExecutor;
import it.unibz.inf.ontop.iq.executor.leftjoin.LeftJoinExecutor;
import it.unibz.inf.ontop.iq.executor.merging.QueryMergingExecutor;
import it.unibz.inf.ontop.iq.executor.projection.ProjectionShrinkingExecutor;
import it.unibz.inf.ontop.iq.executor.pullout.PullVariableOutOfDataNodeExecutor;
import it.unibz.inf.ontop.iq.executor.pullout.PullVariableOutOfSubTreeExecutor;
import it.unibz.inf.ontop.iq.executor.substitution.SubstitutionPropagationExecutor;
import it.unibz.inf.ontop.iq.executor.truenode.TrueNodeRemovalExecutor;
import it.unibz.inf.ontop.iq.executor.union.FlattenUnionExecutor;
import it.unibz.inf.ontop.iq.executor.union.UnionLiftExecutor;
import it.unibz.inf.ontop.iq.executor.unsatisfiable.RemoveEmptyNodesExecutor;
import it.unibz.inf.ontop.injection.OntopOptimizationConfiguration;
import it.unibz.inf.ontop.injection.OntopOptimizationSettings;
import it.unibz.inf.ontop.iq.tools.UnionBasedQueryMerger;
import it.unibz.inf.ontop.iq.optimizer.BindingLiftOptimizer;
import it.unibz.inf.ontop.iq.optimizer.InnerJoinOptimizer;
import it.unibz.inf.ontop.iq.optimizer.JoinLikeOptimizer;
import it.unibz.inf.ontop.iq.optimizer.LeftJoinOptimizer;
import it.unibz.inf.ontop.datalog.DatalogProgram2QueryConverter;
import it.unibz.inf.ontop.iq.tools.QueryUnionSplitter;

public class OntopOptimizationModule extends OntopAbstractModule {

    private OntopOptimizationConfiguration configuration;

    protected OntopOptimizationModule(OntopOptimizationConfiguration configuration) {
        super(configuration.getSettings());
        // Temporary (will be dropped)
        this.configuration = configuration;
    }


    @Override
    protected void configure() {
        bind(OntopOptimizationSettings.class).toInstance(configuration.getSettings());

        // Executors
        bindFromPreferences(InnerJoinExecutor.class);
        bindFromPreferences(SubstitutionPropagationExecutor.class);
        bindFromPreferences(PushDownBooleanExpressionExecutor.class);
        bindFromPreferences(PushUpBooleanExpressionExecutor.class);
        bindFromPreferences(GroundTermRemovalFromDataNodeExecutor.class);
        bindFromPreferences(PullVariableOutOfDataNodeExecutor.class);
        bindFromPreferences(PullVariableOutOfSubTreeExecutor.class);
        bindFromPreferences(RemoveEmptyNodesExecutor.class);
        bindFromPreferences(UnionBasedQueryMerger.class);
        bindFromPreferences(QueryMergingExecutor.class);
        bindFromPreferences(UnionLiftExecutor.class);
        bindFromPreferences(LeftJoinExecutor.class);
        bindFromPreferences(ProjectionShrinkingExecutor.class);
        bindFromPreferences(TrueNodeRemovalExecutor.class);
        bindFromPreferences(FlattenUnionExecutor.class);
        bindFromPreferences(ConstructionNodeCleaningExecutor.class);
        bindFromPreferences(DatalogProgram2QueryConverter.class);
        bindFromPreferences(QueryUnionSplitter.class);
        bindFromPreferences(InnerJoinOptimizer.class);
        bindFromPreferences(JoinLikeOptimizer.class);
        bindFromPreferences(LeftJoinOptimizer.class);
        bindFromPreferences(BindingLiftOptimizer.class);

        // Releases the configuration (enables some GC)
        this.configuration = null;
    }
}