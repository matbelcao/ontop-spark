package it.unibz.inf.ontop.answering.reformulation.input.translation.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import it.unibz.inf.ontop.answering.reformulation.input.translation.RDF4JInputQueryTranslator;
import it.unibz.inf.ontop.exception.OntopInternalBugException;
import it.unibz.inf.ontop.exception.OntopInvalidInputQueryException;
import it.unibz.inf.ontop.exception.OntopUnsupportedInputQueryException;
import it.unibz.inf.ontop.injection.IntermediateQueryFactory;
import it.unibz.inf.ontop.iq.IQ;
import it.unibz.inf.ontop.iq.IQTree;
import it.unibz.inf.ontop.iq.IntermediateQuery;
import it.unibz.inf.ontop.iq.IntermediateQueryBuilder;
import it.unibz.inf.ontop.iq.node.*;
import it.unibz.inf.ontop.model.atom.AtomFactory;
import it.unibz.inf.ontop.model.atom.DataAtom;
import it.unibz.inf.ontop.model.atom.DistinctVariableOnlyDataAtom;
import it.unibz.inf.ontop.model.term.*;
import it.unibz.inf.ontop.model.term.impl.ImmutabilityTools;
import it.unibz.inf.ontop.model.type.RDFDatatype;
import it.unibz.inf.ontop.model.type.TermTypeInference;
import it.unibz.inf.ontop.model.type.TypeFactory;
import it.unibz.inf.ontop.model.vocabulary.XSD;
import it.unibz.inf.ontop.substitution.ImmutableSubstitution;
import it.unibz.inf.ontop.substitution.InjectiveVar2VarSubstitution;
import it.unibz.inf.ontop.substitution.SubstitutionFactory;
import it.unibz.inf.ontop.utils.CoreUtilsFactory;
import it.unibz.inf.ontop.utils.ImmutableCollectors;
import it.unibz.inf.ontop.utils.VariableGenerator;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.Binding;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.algebra.*;
import org.eclipse.rdf4j.query.parser.ParsedQuery;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class RDF4JInputQueryTranslatorImpl implements RDF4JInputQueryTranslator {

    private final CoreUtilsFactory coreUtilsFactory;
    private final TermFactory termFactory;
    private final SubstitutionFactory substitutionFactory;
    private final TypeFactory typeFactory;
    private final IntermediateQueryFactory iqFactory;
    private final AtomFactory atomFactory;

    @Inject
    public RDF4JInputQueryTranslatorImpl(CoreUtilsFactory coreUtilsFactory, TermFactory termFactory, SubstitutionFactory substitutionFactory,
                                         TypeFactory typeFactory, IntermediateQueryFactory iqFactory, AtomFactory atomFactory) {
        this.coreUtilsFactory = coreUtilsFactory;
        this.termFactory = termFactory;
        this.substitutionFactory = substitutionFactory;
        this.typeFactory = typeFactory;
        this.iqFactory = iqFactory;
        this.atomFactory = atomFactory;
    }

    @Override
    public IQ translate(ParsedQuery pq) {
        VariableGenerator variableGenerator = coreUtilsFactory.createVariableGenerator(ImmutableList.of());

        // Assumption: the binding names in the parsed query are in the desired order
        ImmutableList<Variable> projectedVars = pq.getTupleExpr().getBindingNames().stream()
                .map(s -> termFactory.getVariable(s))
                .collect(ImmutableCollectors.toList());
        IQTree tree = translate(pq.getTupleExpr(), variableGenerator).iqTree;
        if (tree.getVariables().containsAll(projectedVars)) {
            return iqFactory.createIQ(
                    atomFactory.getDistinctVariableOnlyDataAtom(
                            atomFactory.getRDFAnswerPredicate(projectedVars.size()),
                            projectedVars
                    ),
                    tree
            );
        }
        throw new Sparql2IqConversionException("The IQ obtained after converting the SPARQL query does not project al expected variables");
    }

    private TranslationResult translate(TupleExpr node, VariableGenerator variableGenerator) throws OntopInvalidInputQueryException, OntopUnsupportedInputQueryException {

        if (node instanceof StatementPattern) // triple pattern
            return translateTriplePattern((StatementPattern) node);

        if (node instanceof Join)    // JOIN algebra operation
            return translateJoin((Join) node, variableGenerator);

        if (node instanceof LeftJoin)   // OPTIONAL algebra operation
            return translateOptional((LeftJoin) node, variableGenerator);

        if (node instanceof Union)    // UNION algebra operation
            return translateUnion((Union) node, variableGenerator);

        if (node instanceof Filter)    // FILTER algebra operation
            return translateFilter((Filter) node, variableGenerator);

        if (node instanceof Projection)   // PROJECT algebra operation
            return translateProjection((Projection) node, variableGenerator);

        if (node instanceof Slice)
            return translateSlice((Slice) node, variableGenerator);

        if (node instanceof Distinct)
            return translateDistinctOrReduced(node, variableGenerator);

        if (node instanceof Reduced)
            return translateDistinctOrReduced(node, variableGenerator);

        if (node instanceof SingletonSet)
            return translateSingletonSet();

        if (node instanceof Extension)
            return translateExtension((Extension) node, variableGenerator);

        if (node instanceof BindingSetAssignment)
            return translateBindingSetAssignment((BindingSetAssignment) node);

        throw new Sparql2IqConversionException("Unexpected SPARQL operator : " + node.toString());
    }


    private TranslationResult translateBindingSetAssignment(BindingSetAssignment node) {

        ImmutableSet<Variable> valueVars = node.getBindingNames().stream()
                .map(termFactory::getVariable)
                .collect(ImmutableCollectors.toSet());
        ImmutableSet<Variable> valueAssuredVars = node.getAssuredBindingNames().stream()
                .map(termFactory::getVariable)
                .collect(ImmutableCollectors.toSet());

        return new TranslationResult(
                iqFactory.createNaryIQTree(
                        iqFactory.createUnionNode(valueVars),
                        getBindingSetCns(
                                node,
                                valueVars
                        )),
                Sets.difference(valueVars, valueAssuredVars).immutableCopy()
        );
    }

    private ImmutableList<IQTree> getBindingSetCns(BindingSetAssignment node, ImmutableSet<Variable> valueVars) {
        return StreamSupport.stream(node.getBindingSets().spliterator(), false)
                .map(b -> getBindingSetCn(b, node.getBindingNames(), valueVars))
                .map(n -> iqFactory.createUnaryIQTree(
                        n,
                        iqFactory.createTrueNode()
                ))
                .collect(ImmutableCollectors.toList());
    }

    private ConstructionNode getBindingSetCn(BindingSet bindingSet, Set<String> bindingNames, ImmutableSet<Variable> valueVars) {
        return iqFactory.createConstructionNode(
                valueVars,
                substitutionFactory.getSubstitution(
                        bindingNames.stream()
                                .collect(ImmutableCollectors.toMap(
                                        termFactory::getVariable,
                                        x -> getTermForBinding(
                                                x,
                                                bindingSet
                                        )))));
    }

    private ImmutableTerm getTermForBinding(String x, BindingSet bindingSet) {
        Binding binding = bindingSet.getBinding(x);
        return binding == null
                ? termFactory.getNullConstant()
                : getTermForLiteralOrIri(binding.getValue());
    }

    private TranslationResult translateSingletonSet() {
        return new TranslationResult(
                iqFactory.createTrueNode(),
                ImmutableSet.of()
        );
    }

    private TranslationResult translateDistinctOrReduced(TupleExpr genNode, VariableGenerator variableGenerator) {
        TranslationResult child;
        if (genNode instanceof Distinct) {
            child = translate(((Distinct) genNode).getArg(), variableGenerator);
        } else if (genNode instanceof Reduced) {
            child = translate(((Reduced) genNode).getArg(), variableGenerator);
        } else {
            throw new Sparql2IqConversionException("Unexpected node type for node: " + genNode.toString());
        }
        return new TranslationResult(
                iqFactory.createUnaryIQTree(
                        iqFactory.createDistinctNode(),
                        child.iqTree
                ),
                child.nullableVariables
        );
    }

    private TranslationResult translateSlice(Slice node, VariableGenerator variableGenerator) {
        TranslationResult child = translate(node.getArg(), variableGenerator);
        return new TranslationResult(
                iqFactory.createUnaryIQTree(
                        iqFactory.createSliceNode(node.getOffset(), node.getLimit()),
                        child.iqTree
                ),
                child.nullableVariables
        );
    }

    private TranslationResult translateFilter(Filter filter, VariableGenerator variableGenerator)
            throws OntopInvalidInputQueryException, OntopUnsupportedInputQueryException {

        TranslationResult child = translate(filter.getArg(), variableGenerator);
        return new TranslationResult(
                iqFactory.createUnaryIQTree(
                        iqFactory.createFilterNode(
                                getFilterExpression(
                                        filter.getCondition(),
                                        child.iqTree.getVariables()
                                )),
                        child.iqTree
                ),
                child.nullableVariables
        );
    }


    private TranslationResult translateOptional(LeftJoin leftJoin, VariableGenerator variableGenerator) {

        TranslationResult leftTranslation = translate(leftJoin.getLeftArg(), variableGenerator);
        TranslationResult rightTranslation = translate(leftJoin.getRightArg(), variableGenerator);

        IQTree leftQuery = leftTranslation.iqTree;
        IQTree rightQuery = rightTranslation.iqTree;

        ImmutableSet<Variable> nullableFromLeft = leftTranslation.nullableVariables;
        ImmutableSet<Variable> nullableFromRight = rightTranslation.nullableVariables;

        ImmutableSet<Variable> projectedFromRight = rightTranslation.iqTree.getVariables();
        ImmutableSet<Variable> projectedFromLeft = leftTranslation.iqTree.getVariables();

        ImmutableSet<Variable> toCoalesce = Sets.intersection(nullableFromLeft, projectedFromRight).immutableCopy();

        ImmutableSet<Variable> toRenameRight = Sets.union(
                toCoalesce,
                Sets.intersection(
                        nullableFromRight,
                        projectedFromLeft
                ).immutableCopy()
        ).immutableCopy();

        ImmutableSet<Variable> bothSidesNullable = Sets.intersection(nullableFromLeft, nullableFromRight).immutableCopy();

        InjectiveVar2VarSubstitution leftRenamingSubstitution = generateVariabledSubstitution(toCoalesce,
                variableGenerator);
        InjectiveVar2VarSubstitution rightRenamingSubstitution = generateVariabledSubstitution(toRenameRight,
                variableGenerator);
        ImmutableSubstitution<ImmutableTerm> topSubstitution = substitutionFactory.getSubstitution(toCoalesce.stream()
                .collect(ImmutableCollectors.toMap(
                        x -> x,
                        x -> termFactory.getImmutableExpression(
                                COALESCE,
                                leftRenamingSubstitution.get(x),
                                rightRenamingSubstitution.get(x)
                        ))));

        LeftJoinNode ljNode;
        ImmutableSet<Variable> newSetOfNullableVars;

        Optional<ImmutableExpression> filterExpression;
        ValueExpr filterCondition = leftJoin.getCondition();
        if (filterCondition != null) {
            ImmutableSet<Variable> knownVariables =
                    Sets.union(leftQuery.getKnownVariables(), rightQuery.getKnownVariables()).immutableCopy();
            ImmutableExpression filterExpressionBeforeSubst =
                    termFactory.getImmutableExpression(getFilterExpression(filterCondition, knownVariables));

            filterExpression =
                    Optional.of(topSubstitution.applyToBooleanExpression(filterExpressionBeforeSubst));
        } else {
            filterExpression = Optional.empty();
        }

        Optional<ImmutableExpression> joinCondition = generateJoinCondition(
                leftRenamingSubstitution,
                rightRenamingSubstitution,
                bothSidesNullable,
                filterExpression
        );

        ljNode = iqFactory.createLeftJoinNode(joinCondition);

        ImmutableSet<Variable> newNullableVars =
                Sets.difference(rightQuery.getVariables(), leftQuery.getVariables())
                        .immutableCopy();

        newSetOfNullableVars =
                Sets.union(Sets.union(nullableFromLeft, nullableFromRight), newNullableVars)
                        .immutableCopy();

        IQTree joinQuery = buildJoinQuery(
                ljNode,
                leftQuery,
                rightQuery,
                leftRenamingSubstitution, rightRenamingSubstitution, topSubstitution
        );

        return new TranslationResult(joinQuery, newSetOfNullableVars);
    }

    private InjectiveVar2VarSubstitution generateVariabledSubstitution(
            ImmutableSet<Variable> nullableVariables, VariableGenerator variableGenerator) {

        return substitutionFactory.getInjectiveVar2VarSubstitution(nullableVariables.stream()
                .collect(ImmutableCollectors.toMap(
                        x -> x,
                        variableGenerator::generateNewVariableFromVar
                )));
    }

    private TranslationResult translateJoin(Join join, VariableGenerator variableGenerator) {

        TranslationResult leftTranslation = translate(join.getLeftArg(), variableGenerator);
        TranslationResult rightTranslation = translate(join.getRightArg(), variableGenerator);

        IQTree leftQuery = leftTranslation.iqTree;
        IQTree rightQuery = rightTranslation.iqTree;

        ImmutableSet<Variable> nullableFromLeft = leftTranslation.nullableVariables;
        ImmutableSet<Variable> nullableFromRight = rightTranslation.nullableVariables;

        ImmutableSet<Variable> toSubstituteLeft =
                Sets.intersection(nullableFromLeft, rightQuery.getVariables()).immutableCopy();
        ImmutableSet<Variable> toSubstituteRight =
                Sets.intersection(nullableFromRight, leftQuery.getVariables()).immutableCopy();

        InjectiveVar2VarSubstitution leftRenamingSubstitution = generateVariabledSubstitution(toSubstituteLeft,
                variableGenerator);
        InjectiveVar2VarSubstitution rightRenamingSubstitution = generateVariabledSubstitution(toSubstituteRight,
                variableGenerator);

        ImmutableSet<Variable> bothSideNullableVars = Sets.intersection(nullableFromLeft, nullableFromRight).immutableCopy();

        ImmutableSubstitution<ImmutableTerm> topSubstitution = substitutionFactory.getSubstitution(bothSideNullableVars.stream()
                .collect(ImmutableCollectors.toMap(
                        x -> x,
                        x -> termFactory.getImmutableExpression(COALESCE, leftRenamingSubstitution.get(x),
                                rightRenamingSubstitution.get(x)))));

        InnerJoinNode joinNode;
        ImmutableSet<Variable> newSetOfNullableVars;

        joinNode = iqFactory.createInnerJoinNode(generateJoinCondition(
                leftRenamingSubstitution,
                rightRenamingSubstitution,
                bothSideNullableVars,
                Optional.empty())
        );

        newSetOfNullableVars = Sets.union(nullableFromLeft, nullableFromRight).immutableCopy();

        IQTree joinQuery = buildJoinQuery(
                joinNode,
                leftQuery,
                rightQuery,
                leftRenamingSubstitution,
                rightRenamingSubstitution,
                topSubstitution
        );

        return new TranslationResult(joinQuery, newSetOfNullableVars);
    }

    private Optional<ImmutableExpression> generateJoinCondition(InjectiveVar2VarSubstitution leftRenamingSubstitution,
                                                                InjectiveVar2VarSubstitution rightRenamingSubstitution,
                                                                ImmutableSet<Variable> bothSideNullableVars,
                                                                Optional<ImmutableExpression> filterCondition) {

        Optional<ImmutableExpression> compatibilityCondition = generateCompatibleJoinCondition(
                leftRenamingSubstitution,
                rightRenamingSubstitution,
                bothSideNullableVars
        );
        return compatibilityCondition.isPresent() ?
                compatibilityCondition
                        .map(compatExpr -> filterCondition.map(
                                filterExpr -> termFactory.getConjunction(
                                        filterExpr,
                                        compatExpr
                                )).orElse(compatExpr)
                        ) :
                filterCondition;
    }

    private Optional<ImmutableExpression> generateCompatibleJoinCondition(
            InjectiveVar2VarSubstitution leftChildSubstitution,
            InjectiveVar2VarSubstitution rightChildSubstitution, ImmutableSet<Variable> bothSideNullableVars) {

        Stream<Variable> nullableVariableStream = Stream.concat(
                leftChildSubstitution.getDomain().stream(),
                rightChildSubstitution.getDomain().stream()
        ).distinct();

        return termFactory.getConjunction(nullableVariableStream
                .map(v -> generateCompatibleExpression(
                        v,
                        leftChildSubstitution,
                        rightChildSubstitution,
                        bothSideNullableVars
                )));
    }


    private ImmutableExpression generateCompatibleExpression(Variable outputVariable,
                                                             InjectiveVar2VarSubstitution leftChildSubstitution,
                                                             InjectiveVar2VarSubstitution rightChildSubstitution,
                                                             ImmutableSet<Variable> bothSideNullableVars) {
        ImmutableExpression isNullExpression;

        Variable leftVariable = leftChildSubstitution.applyToVariable(outputVariable);
        Variable rightVariable = rightChildSubstitution.applyToVariable(outputVariable);

        ImmutableExpression equalityCondition =
                termFactory.getStrictEquality(leftVariable, rightVariable);

        if (bothSideNullableVars.contains(outputVariable)) {
            ImmutableExpression leftIsNull = termFactory.getDBIsNull(leftVariable);
            ImmutableExpression rightIsNull = termFactory.getDBIsNull(rightVariable);
            isNullExpression = termFactory.getDisjunction(leftIsNull, rightIsNull);
        } else if (leftChildSubstitution.isDefining(outputVariable)) {
            isNullExpression = termFactory.getDBIsNull(leftVariable);
        } else {
            isNullExpression = termFactory.getDBIsNull(rightVariable);
        }
        return termFactory.getDisjunction(equalityCondition, isNullExpression);
    }

    private IQTree buildJoinQuery(JoinLikeNode joinNode,
                                  IQTree leftQuery,
                                  IQTree rightQuery,
                                  InjectiveVar2VarSubstitution leftRenamingSubstitution,
                                  InjectiveVar2VarSubstitution rightRenamingSubstitution,
                                  ImmutableSubstitution<ImmutableTerm> topSubstitution
    ) {

        IQTree leftTree = iqFactory.createUnaryIQTree(
                getJoinOperandCN(
                        leftQuery,
                        leftRenamingSubstitution
                ),
                leftQuery
        );
        IQTree rightTree = iqFactory.createUnaryIQTree(
                getJoinOperandCN(
                        rightQuery,
                        rightRenamingSubstitution
                ),
                rightQuery
        );

        ImmutableSet<Variable> projectedVariables =
                Sets.union(leftTree.getVariables(), rightTree.getVariables())
                        .immutableCopy();

        return iqFactory.createUnaryIQTree(
                iqFactory.createConstructionNode(
                        projectedVariables,
                        topSubstitution
                ),
                getJoinTree(
                        joinNode,
                        leftTree,
                        rightTree
                ));
    }

    private ConstructionNode getJoinOperandCN(IQTree tree, InjectiveVar2VarSubstitution sub) {
        return iqFactory.createConstructionNode(
                tree.getVariables().stream()
                        .map(v -> sub.applyToVariable(v))
                        .collect(ImmutableCollectors.toSet()),
                (ImmutableSubstitution) sub
        );
    }

    private IQTree getJoinTree(JoinLikeNode joinNode, IQTree leftTree, IQTree rightTree) {
        if (joinNode instanceof LeftJoinNode) {
            return iqFactory.createBinaryNonCommutativeIQTree(
                    (LeftJoinNode) joinNode,
                    leftTree,
                    rightTree
            );
        }
        if (joinNode instanceof InnerJoinNode) {
            return iqFactory.createNaryIQTree(
                    (InnerJoinNode) joinNode,
                    ImmutableList.of(
                            leftTree,
                            rightTree
                    )
            );
        }
        throw new Sparql2IqConversionException("Left or inner join expected");
    }

    private TranslationResult translateProjection(Projection node, VariableGenerator variableGenerator) {
        TranslationResult child = translate(node.getArg(), variableGenerator);
        IQTree subQuery = child.iqTree;

        List<ProjectionElem> projectionElems = node.getProjectionElemList().getElements();
        ImmutableSubstitution<ImmutableTerm> topSubstitution =
                substitutionFactory.getSubstitution(projectionElems.stream()
                        .filter(pe -> !pe.getTargetName().equals(pe.getSourceName()))
                        .collect(ImmutableCollectors.toMap(
                                pe -> termFactory.getVariable(pe.getSourceName()),
                                pe -> termFactory.getVariable(pe.getTargetName())
                        )));

        ImmutableList<Variable> projectedVariables = projectionElems.stream()
                .map(pe -> termFactory.getVariable(pe.getTargetName()))
                .collect(ImmutableCollectors.toList());
        ConstructionNode projectNode = iqFactory.createConstructionNode(
                ImmutableSet.copyOf(projectedVariables),
                topSubstitution
        );
        return new TranslationResult(
                iqFactory.createUnaryIQTree(
                       projectNode,
                       subQuery
                ),
                child.nullableVariables.stream()
                        .map(v -> topSubstitution.applyToVariable(v))
                        .filter(t -> t instanceof Variable)
                        .map(t -> (Variable) t)
                        .collect(ImmutableCollectors.toSet())
        );
    }

    private TranslationResult translateUnion(Union union, VariableGenerator variableGenerator) {
        TranslationResult leftTranslation = translate(union.getLeftArg(), variableGenerator);
        TranslationResult rightTranslation = translate(union.getRightArg(), variableGenerator);

        IntermediateQuery leftQuery = leftTranslation.iqTree;
        IntermediateQuery rightQuery = rightTranslation.iqTree;

        ImmutableSet<Variable> nullableFromLeft = leftTranslation.nullableVariables;
        ImmutableSet<Variable> nullableFromRight = rightTranslation.nullableVariables;

        ImmutableSet<Variable> leftVariables = leftQuery.getProjectionAtom().getVariables();
        ImmutableSet<Variable> rightVariables = rightQuery.getProjectionAtom().getVariables();

        ImmutableSet<Variable> nullOnLeft = Sets.difference(rightVariables, leftVariables).immutableCopy();
        ImmutableSet<Variable> nullOnRight = Sets.difference(leftVariables, rightVariables).immutableCopy();

        ImmutableSet<Variable> allNullable = Sets.union(nullableFromLeft, Sets.union(nullableFromRight, Sets.union(nullOnLeft, nullOnRight))).immutableCopy();

        ImmutableSet<Variable> rootVariables = Sets.union(leftVariables, rightVariables).immutableCopy();

        ImmutableSubstitution<ImmutableTerm> leftSubstitution = DATA_FACTORY.getSubstitution(nullOnLeft.stream()
                .collect(ImmutableCollectors.toMap(
                        x -> x,
                        x -> NULL)));

        ImmutableSubstitution<ImmutableTerm> rightSubstitution = DATA_FACTORY.getSubstitution(nullOnRight.stream()
                .collect(ImmutableCollectors.toMap(
                        x -> x,
                        x -> NULL)));

        ConstructionNode leftNode = iqFactory.createConstructionNode(rootVariables, leftSubstitution);
        ConstructionNode rightNode = iqFactory.createConstructionNode(rootVariables, rightSubstitution);

        DistinctVariableOnlyDataAtom projectionAtom = DATA_FACTORY.getDistinctVariableOnlyDataAtom(
                generateFreshPredicate("union", rootVariables.size()),
                ImmutableList.copyOf(rootVariables));

        UnionNode unionNode = iqFactory.createUnionNode(rootVariables);

        ConstructionNode rootNode = iqFactory.createConstructionNode(rootVariables);

        IntensionalDataNode leftIChild = iqFactory.createIntensionalDataNode(leftQuery.getProjectionAtom());//subsLeftAtom);;
        IntensionalDataNode rightIChild = iqFactory.createIntensionalDataNode(rightQuery.getProjectionAtom());//subsRightAtom);;

        IntermediateQueryBuilder queryBuilder = leftQuery.newBuilder();
        queryBuilder.init(projectionAtom, rootNode);
        queryBuilder.addChild(rootNode, unionNode);
        queryBuilder.addChild(unionNode, leftNode);
        queryBuilder.addChild(unionNode, rightNode);
        queryBuilder.addChild(leftNode, leftIChild);
        queryBuilder.addChild(rightNode, rightIChild);

        IntermediateQuery newQuery = mergeChildren(queryBuilder.build(),
                ImmutableMap.of(leftIChild, leftQuery, rightIChild, rightQuery));

        return new TranslationResult(newQuery, allNullable, projectedVariables);
    }

    private TranslationResult translateTriplePattern(StatementPattern triple) {

        // A triple pattern is member of the set (RDF-T + V) x (I + V) x (RDF-T + V)
        // VarOrTerm ::=  Var | GraphTerm
        // GraphTerm ::=  iri | RDFLiteral | NumericLiteral | BooleanLiteral | BlankNode | NIL


        ImmutableList<Var> args = ImmutableList.of(
                triple.getSubjectVar(),
                triple.getPredicateVar(),
                triple.getObjectVar());

        final ImmutableList<VariableOrGroundTerm> terms = args.stream()
                .map(this::translateVar)
                .map(ImmutabilityTools::convertIntoVariableOrGroundTerm)
                .collect(ImmutableCollectors.toList());

        final DataAtom dataAtom = DATA_FACTORY.getDataAtom(TRIPLE_PRED, terms);

        final ImmutableList<Variable> signature = terms.stream()
                .filter(Variable.class::isInstance)
                .map(Variable.class::cast)
                .collect(ImmutableCollectors.toList());

        ConstructionNode root = iqFactory.createConstructionNode(ImmutableSet.copyOf(signature));

        DistinctVariableOnlyDataAtom projectionAtom = DATA_FACTORY.getDistinctVariableOnlyDataAtom(
                generateFreshPredicate("project", signature.size()),
                signature
        );

        IntermediateQueryBuilder queryBuilder = iqFactory.createIQBuilder(metadata, executorRegistry);
        IntensionalDataNode atomNode = iqFactory.createIntensionalDataNode(dataAtom);

        queryBuilder.init(projectionAtom, root);

        queryBuilder.addChild(root, atomNode);

        IntermediateQuery newQuery = queryBuilder.build();

        return new TranslationResult(newQuery, ImmutableSet.of(), projectedVariables);
    }

    private TranslationResult translateExtension(Extension node, VariableGenerator variableGenerator) {
        TranslationResult childTranslation = translate(node.getArg(), variableGenerator);
        IntermediateQuery childQuery = childTranslation.iqTree;
        ImmutableSubstitution<ImmutableTerm> extSubstitution = DATA_FACTORY.getSubstitution(node.getElements().stream()
                .filter(ee -> !(ee.getExpr() instanceof Var && ee.getName().equals(((Var) ee.getExpr()).getName())))
                .collect(ImmutableCollectors.toMap(
                        x -> DATA_FACTORY.getVariable(x.getName()),
                        x -> ImmutabilityTools.convertIntoImmutableTerm(getExpression(x.getExpr(), childQuery.getProjectionAtom().getVariables()))
                )));

        ImmutableSet<Variable> projectedVariables =
                Sets.union(childQuery.getProjectionAtom().getVariables(), extSubstitution.getDomain()).immutableCopy();

        DistinctVariableOnlyDataAtom projectionAtom = DATA_FACTORY.getDistinctVariableOnlyDataAtom(
                generateFreshPredicate("value", projectedVariables.size()),
                ImmutableList.copyOf(projectedVariables)
        );
        ConstructionNode rootConstuction = iqFactory.createConstructionNode(projectedVariables, extSubstitution);
        IntermediateQueryBuilder queryBuilder = childQuery.newBuilder();
        queryBuilder.init(projectionAtom, rootConstuction);
        IntensionalDataNode childNode = iqFactory.createIntensionalDataNode(childQuery.getProjectionAtom());
        queryBuilder.addChild(rootConstuction, childNode);

        IntermediateQuery newQuery = mergeChildren(queryBuilder.build(), ImmutableMap.of(childNode, childQuery));

        return new TranslationResult(newQuery, ImmutableSet.of(), projectedVariables);
    }

    private ImmutableTerm getTermForLiteralOrIri(Value v) {

        if (v instanceof Literal)
            return getTermForLiteral((Literal) v);
        else if (v instanceof IRI)
            return getTermForIri((IRI) v);

        throw new OntopUnsupportedInputQueryException("The value " + v + " is not supported yet!");
    }

    private ImmutableTerm getTermForLiteral(Literal literal) throws OntopUnsupportedInputQueryException {
        IRI typeURI = literal.getDatatype();
        String value = literal.getLabel();
        Optional<String> lang = literal.getLanguage();

        if (lang.isPresent()) {
            return termFactory.getRDFLiteralFunctionalTerm(termFactory.getDBStringConstant(value), lang.get());

        } else {
            RDFDatatype type;
            /*
             * default data type is xsd:string
             */
            if (typeURI == null) {
                type = typeFactory.getXsdStringDatatype();
            } else {
                type = typeFactory.getDatatype(rdfFactory.createIRI(typeURI.stringValue()));
            }

            if (type == null)
                // ROMAN (27 June 2016): type1 in open-eq-05 test would not be supported in OWL
                // the actual value is LOST here
                return immutabilityTools.convertToMutableTerm(
                        termFactory.getConstantIRI(rdfFactory.createIRI(typeURI.stringValue())));
            // old strict version:
            // throw new RuntimeException("Unsupported datatype: " + typeURI);

            // BC-march-19: it seems that SPARQL does not forbid invalid lexical forms
            //     (e.g. when interpreted as an EBV, they evaluate to false)
            // However, it is unclear in which cases it would be interesting to offer a (partial) robustness to
            // such errors coming from the input query
            // check if the value is (lexically) correct for the specified datatype
            if (!XMLDatatypeUtil.isValidValue(value, typeURI))
                throw new OntopUnsupportedInputQueryException(
                        String.format("Invalid lexical forms are not accepted. Found for %s: %s", type.toString(), value));

            Term constant = termFactory.getDBStringConstant(value);

            return termFactory.getRDFLiteralMutableFunctionalTerm(constant, type);

        }
    }

    /**
     * @param expr      expression
     * @param variables the set of variables that can occur in the expression
     *                  (the rest will be replaced with NULL)
     * @return
     */

    private ImmutableExpression getFilterExpression(ValueExpr expr, ImmutableSet<Variable> variables)
            throws OntopUnsupportedInputQueryException, OntopInvalidInputQueryException {

        ImmutableTerm term = getExpression(expr, variables);

        ImmutableTerm xsdBooleanTerm = term.inferType()
                .flatMap(TermTypeInference::getTermType)
                .filter(t -> t instanceof RDFDatatype)
                .filter(t -> ((RDFDatatype) t).isA(XSD.BOOLEAN))
                .isPresent()
                ? term
                : termFactory.getSPARQLEffectiveBooleanValue(term);

        return termFactory.getRDF2DBBooleanFunctionalTerm(xsdBooleanTerm);
    }


    /**
     * @param expr      expression
     * @param variables the set of variables that can occur in the expression
     *                  (the rest will be replaced with NULL)
     * @return term
     */

    private ImmutableTerm getExpression(ValueExpr expr, Set<Variable> variables) throws OntopUnsupportedInputQueryException, OntopInvalidInputQueryException {

        // PrimaryExpression ::= BrackettedExpression | BuiltInCall | iriOrFunction |
        //                          RDFLiteral | NumericLiteral | BooleanLiteral | Var
        // iriOrFunction ::= iri ArgList?

        if (expr instanceof Var) {
            Var v = (Var) expr;
            Variable var = termFactory.getVariable(v.getName());
            return variables.contains(var) ? var : valueNull;
        } else if (expr instanceof ValueConstant) {
            Value v = ((ValueConstant) expr).getValue();
            if (v instanceof Literal)
                return getTermForLiteral((Literal) v);
            else if (v instanceof IRI)
                return getTermForIri((IRI) v);

            throw new OntopUnsupportedInputQueryException("The value " + v + " is not supported yet!");
        } else if (expr instanceof Bound) {
            // BOUND (Sec 17.4.1.1)
            // xsd:boolean  BOUND (variable var)
            Var v = ((Bound) expr).getArg();
            Variable var = termFactory.getVariable(v.getName());
            return variables.contains(var)
                    ? termFactory.getFunction(
                    functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.BOUND, 1), var)
                    : termFactory.getRDFLiteralConstant("false", XSD.BOOLEAN);
        } else if (expr instanceof UnaryValueOperator) {
            Term term = getExpression(((UnaryValueOperator) expr).getArg(), variables);

            if (expr instanceof Not) {
                return termFactory.getFunction(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(XPathFunction.NOT.getIRIString(), 1),
                        convertToXsdBooleanTerm(term));
            } else if (expr instanceof IsNumeric) {
                return termFactory.getFunction(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.IS_NUMERIC, 1),
                        term);
            } else if (expr instanceof IsLiteral) {
                return termFactory.getFunction(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.IS_LITERAL, 1),
                        term);
            } else if (expr instanceof IsURI) {
                return termFactory.getFunction(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.IS_IRI, 1),
                        term);
            } else if (expr instanceof Str) {
                return termFactory.getFunction(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.STR, 1),
                        term);
            } else if (expr instanceof Datatype) {
                return termFactory.getFunction(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.DATATYPE, 1),
                        term);
            } else if (expr instanceof IsBNode) {
                return termFactory.getFunction(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.IS_BLANK, 1),
                        term);
            } else if (expr instanceof Lang) {
                ValueExpr arg = ((UnaryValueOperator) expr).getArg();
                if (arg instanceof Var)
                    return termFactory.getFunction(
                            functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.LANG, 1),
                            term);
                else
                    throw new RuntimeException("A variable or a value is expected in " + expr);
            }
            // other subclasses
            // IRIFunction: IRI (Sec 17.4.2.8) for constructing IRIs
            // IsNumeric:  isNumeric (Sec 17.4.2.4) for checking whether the argument is a numeric value
            // AggregateOperatorBase: Avg, Min, Max, etc.
            // Like:  ??
            // IsResource: ??
            // LocalName: ??
            // Namespace: ??
            // Label: ??
        } else if (expr instanceof BinaryValueOperator) {
            BinaryValueOperator bexpr = (BinaryValueOperator) expr;
            Term term1 = getExpression(bexpr.getLeftArg(), variables);
            Term term2 = getExpression(bexpr.getRightArg(), variables);

            if (expr instanceof And) {
                return termFactory.getFunction(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.LOGICAL_AND, 2),
                        convertToXsdBooleanTerm(term1), convertToXsdBooleanTerm(term2));
            } else if (expr instanceof Or) {
                return termFactory.getFunction(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.LOGICAL_OR, 2),
                        convertToXsdBooleanTerm(term1), convertToXsdBooleanTerm(term2));
            } else if (expr instanceof SameTerm) {
                // sameTerm (Sec 17.4.1.8)
                // Corresponds to the STRICT equality (same lexical value, same type)
                return termFactory.getFunction(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.SAME_TERM, 2),
                        term1, term2);
            } else if (expr instanceof Regex) {
                // REGEX (Sec 17.4.3.14)
                // xsd:boolean  REGEX (string literal text, simple literal pattern)
                // xsd:boolean  REGEX (string literal text, simple literal pattern, simple literal flags)
                Regex reg = (Regex) expr;
                return (reg.getFlagsArg() != null)
                        ? termFactory.getFunction(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.REGEX, 3),
                        term1, term2,
                        getExpression(reg.getFlagsArg(), variables))
                        : termFactory.getFunction(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.REGEX, 2),
                        term1, term2);
            } else if (expr instanceof Compare) {
                // TODO: make it a SPARQLFunctionSymbol
                final FunctionSymbol p;

                switch (((Compare) expr).getOperator()) {
                    case NE:
                        return termFactory.getFunction(
                                functionSymbolFactory.getRequiredSPARQLFunctionSymbol(XPathFunction.NOT.getIRIString(), 1),
                                termFactory.getFunction(
                                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.EQ, 2),
                                        term1, term2));
                    case EQ:
                        p = functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.EQ, 2);
                        break;
                    case LT:
                        p = functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.LESS_THAN, 2);
                        break;
                    case LE:
                        return termFactory.getFunction(
                                functionSymbolFactory.getRequiredSPARQLFunctionSymbol(XPathFunction.NOT.getIRIString(), 1),
                                termFactory.getFunction(
                                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.GREATER_THAN, 2),
                                        term1, term2));
                    case GE:
                        return termFactory.getFunction(
                                functionSymbolFactory.getRequiredSPARQLFunctionSymbol(XPathFunction.NOT.getIRIString(), 1),
                                termFactory.getFunction(
                                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.LESS_THAN, 2),
                                        term1, term2));
                    case GT:
                        p = functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.GREATER_THAN, 2);
                        break;
                    default:
                        throw new OntopUnsupportedInputQueryException("Unsupported operator: " + expr);
                }
                return termFactory.getFunction(p, term1, term2);
            } else if (expr instanceof MathExpr) {
                SPARQLFunctionSymbol f = functionSymbolFactory.getRequiredSPARQLFunctionSymbol(
                        NumericalOperations.get(((MathExpr) expr).getOperator()), 2);
                return termFactory.getFunction(f, term1, term2);
            }
            /*
             * Restriction: the first argument must be LANG(...) and the second  a constant
             * (for guaranteeing that the langMatches logic is not delegated to the native query)
             */
            else if (expr instanceof LangMatches) {
                if ((!((term1 instanceof Function)
                        && ((Function) term1).getFunctionSymbol() instanceof LangSPARQLFunctionSymbol))
                        || (!((term2 instanceof Function)
                        // TODO: support "real" constants (not wrapped into a functional term)
                        && ((Function) term2).getFunctionSymbol() instanceof RDFTermFunctionSymbol))) {
                    throw new OntopUnsupportedInputQueryException("The function langMatches is " +
                            "only supported with lang(..) function for the first argument and a constant for the second");
                }

                SPARQLFunctionSymbol langMatchesFunctionSymbol = functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.LANG_MATCHES, 2);

                return termFactory.getFunction(langMatchesFunctionSymbol, term1, term2);
            }
        } else if (expr instanceof FunctionCall) {
            FunctionCall f = (FunctionCall) expr;

            int arity = f.getArgs().size();
            List<Term> terms = new ArrayList<>(arity);
            for (ValueExpr a : f.getArgs())
                terms.add(getExpression(a, variables));

            Optional<SPARQLFunctionSymbol> optionalFunctionSymbol = functionSymbolFactory.getSPARQLFunctionSymbol(
                    f.getURI(), terms.size());

            if (optionalFunctionSymbol.isPresent()) {
                return termFactory.getFunction(optionalFunctionSymbol.get(), terms);
            }
        }
        // other subclasses
        // SubQueryValueOperator
        // If
        // BNodeGenerator
        // NAryValueOperator (ListMemberOperator and Coalesce)
        throw new OntopUnsupportedInputQueryException("The expression " + expr + " is not supported yet!");
    }

    private static class TranslationResult {
        final IQTree iqTree;
        final ImmutableSet<Variable> nullableVariables;

        TranslationResult(IQTree iqTree, ImmutableSet<Variable> nullableVariables) {
            this.nullableVariables = nullableVariables;
            this.iqTree = iqTree;
        }
    }

    private static class Sparql2IqConversionException extends OntopInternalBugException {

        protected Sparql2IqConversionException(String s) {
            super(s);
        }
    }
}
