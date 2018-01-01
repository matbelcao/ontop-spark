package it.unibz.inf.ontop.injection.impl;

import com.google.inject.Module;
import it.unibz.inf.ontop.exception.InvalidOntopConfigurationException;
import it.unibz.inf.ontop.exception.OBDASpecificationException;
import it.unibz.inf.ontop.exception.OntologyException;
import it.unibz.inf.ontop.injection.OntopReformulationSettings;
import it.unibz.inf.ontop.injection.OntopStandaloneSQLSettings;
import it.unibz.inf.ontop.injection.OntopTemporalMappingSQLAllSettings;
import it.unibz.inf.ontop.injection.OntopTemporalSQLOWLAPIConfiguration;
import it.unibz.inf.ontop.spec.OBDASpecification;
import it.unibz.inf.ontop.spec.ontology.Ontology;
import it.unibz.inf.ontop.spec.ontology.owlapi.OWLAPITranslatorUtility;
import org.apache.commons.rdf.api.Graph;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.Reader;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

public class OntopTemporalSQLOWLAPIConfigurationImpl extends OntopSQLOWLAPIConfigurationImpl implements OntopTemporalSQLOWLAPIConfiguration {

    public final OntopTemporalMappingSQLAllConfigurationImpl temporalConfiguration;
    private final OntopTemporalSQLOWLAPIOptions options;


    OntopTemporalSQLOWLAPIConfigurationImpl(OntopStandaloneSQLSettings settings,
                                            OntopTemporalMappingSQLAllSettings temporalSettings,
                                            OntopTemporalSQLOWLAPIOptions options) {
        super(settings, options.owlOptions);
        this.options = options;
        temporalConfiguration = new OntopTemporalMappingSQLAllConfigurationImpl(temporalSettings, options.temporalOptions);
    }

    @Override
    protected Stream<Module> buildGuiceModules() {
        return Stream.concat(super.buildGuiceModules(), Stream.concat(
                new OntopReformulationConfigurationImpl(getSettings(),
                        options.owlOptions.sqlOptions.systemOptions.sqlTranslationOptions.reformulationOptions).buildGuiceModules(),
                Stream.of(new OntopTemporalModule(this)))
        );
    }

    @Override
    public OBDASpecification loadOBDASpecification() throws OBDASpecificationException {
        //super.loadOBDASpecification();
        return temporalConfiguration.loadSpecification();
    }

    private Optional<Ontology> getOntology() throws OntologyException {
        OWLAPITranslatorUtility owlapiTranslatorUtility = getInjector().getInstance(OWLAPITranslatorUtility.class);

        try {
            return loadInputOntology()
                    .map(owlapiTranslatorUtility::translateImportsClosure);
        } catch (OWLOntologyCreationException e) {
            throw new OntologyException(e.getMessage());
        }
    }

    static class OntopTemporalSQLOWLAPIOptions {
        final OntopSQLOWLAPIOptions owlOptions;
        final OntopTemporalMappingSQLAllConfigurationImpl.OntopTemporalMappingSQLAllOptions temporalOptions;

        OntopTemporalSQLOWLAPIOptions(OntopSQLOWLAPIOptions owlOptions, OntopTemporalMappingSQLAllConfigurationImpl.OntopTemporalMappingSQLAllOptions temporalOptions) {
            this.owlOptions = owlOptions;
            this.temporalOptions = temporalOptions;
        }
    }

    static abstract class OntopTemporalSQLOWLAPIBuilderMixin<B extends OntopTemporalSQLOWLAPIConfiguration.Builder<B>>
            extends OntopSQLOWLAPIBuilderMixin<B>
            implements OntopTemporalSQLOWLAPIConfiguration.Builder<B> {

        private boolean isOntologyDefined = false;
        private final OntopTemporalMappingSQLAllConfigurationImpl.StandardTemporalMappingSQLBuilderFragment<B> temporalMappingBuilderFragment;

        OntopTemporalSQLOWLAPIBuilderMixin() {
            B builder = (B) this;
            temporalMappingBuilderFragment = new OntopTemporalMappingSQLAllConfigurationImpl.StandardTemporalMappingSQLBuilderFragment<>(builder,
                    this::declareMappingDefined, this::declareImplicitConstraintSetDefined);
        }

        void declareOntologyDefined() {
            if (isOntologyDefined) {
                throw new InvalidOntopConfigurationException("Ontology already defined!");
            }
            isOntologyDefined = true;
        }

        @Override
        public B nativeOntopTemporalMappingFile(@Nonnull final File mappingFile) {
            return temporalMappingBuilderFragment.nativeOntopTemporalMappingFile(mappingFile);
        }

        @Override
        public B nativeOntopTemporalMappingFile(@Nonnull final String mappingFilename) {
            return temporalMappingBuilderFragment.nativeOntopTemporalMappingFile(mappingFilename);
        }

        @Override
        public B nativeOntopTemporalMappingReader(@Nonnull final Reader mappingReader) {
            return temporalMappingBuilderFragment.nativeOntopTemporalMappingReader(mappingReader);
        }

        @Override
        public B nativeOntopMappingFile(@Nonnull final String mappingFile) {
            return temporalMappingBuilderFragment.nativeOntopMappingFile(mappingFile);
        }

        @Override
        public B r2rmlMappingFile(@Nonnull final File mappingFile) {
            return temporalMappingBuilderFragment.r2rmlMappingFile(mappingFile);
        }

        @Override
        public B r2rmlMappingGraph(@Nonnull final Graph rdfGraph) {
            return temporalMappingBuilderFragment.r2rmlMappingGraph(rdfGraph);
        }

        boolean isTemporal() {
            return temporalMappingBuilderFragment.isTemporal();
        }

        final OntopTemporalMappingSQLAllConfigurationImpl.OntopTemporalMappingSQLAllOptions generateTemporalMappingSQLAllOptions() {
            return temporalMappingBuilderFragment.generateMappingSQLTemporalOptions(generateMappingSQLOptions());
        }

        final OntopTemporalMappingSQLAllSettings getTemporalSettings(){
            return new OntopTemporalMappingSQLAllSettingsImpl(generateProperties(), isR2rml(), isTemporal());
        }

        @Override
        protected Properties generateProperties() {
            Properties p = super.generateProperties();
            p.putAll(temporalMappingBuilderFragment.generateProperties());
            return p;
        }

        final OntopTemporalSQLOWLAPIOptions generateTemporalSQLOWLAPIOptions() {
            return new OntopTemporalSQLOWLAPIOptions(generateSQLOWLAPIOptions(), generateTemporalMappingSQLAllOptions());
        }

    }

    public static class BuilderImpl<B extends OntopTemporalSQLOWLAPIConfiguration.Builder<B>>
            extends OntopTemporalSQLOWLAPIBuilderMixin<B> {

        @Override
        public OntopTemporalSQLOWLAPIConfiguration build() {

            return new OntopTemporalSQLOWLAPIConfigurationImpl(
                    new OntopStandaloneSQLSettingsImpl(generateProperties(), isR2rml()),
                    getTemporalSettings(),
                    generateTemporalSQLOWLAPIOptions());
        }
    }

}
