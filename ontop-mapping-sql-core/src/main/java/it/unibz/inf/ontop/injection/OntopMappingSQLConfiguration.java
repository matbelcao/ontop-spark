package it.unibz.inf.ontop.injection;


import it.unibz.inf.ontop.exception.DuplicateMappingException;
import it.unibz.inf.ontop.exception.InvalidMappingException;
import it.unibz.inf.ontop.exception.MappingIOException;
import it.unibz.inf.ontop.injection.impl.OntopMappingSQLConfigurationImpl;
import it.unibz.inf.ontop.model.SQLPPMapping;

import javax.annotation.Nonnull;
import java.util.Optional;

public interface OntopMappingSQLConfiguration extends OntopSQLCoreConfiguration, OntopMappingConfiguration {

    @Override
    OntopMappingSQLSettings getSettings();

    /**
     * Default builder
     */
    static Builder<? extends Builder> defaultBuilder() {
        return new OntopMappingSQLConfigurationImpl.BuilderImpl<>();
    }

    Optional<SQLPPMapping> loadPPMapping() throws MappingIOException, InvalidMappingException, DuplicateMappingException;

    default SQLPPMapping loadProvidedPPMapping() throws DuplicateMappingException, MappingIOException, InvalidMappingException {
        return loadPPMapping()
                .orElseThrow(() -> new IllegalStateException("No PreProcessedMapping could have been loaded. " +
                        "Do not call this method unless you are sure of the input provision."));
    }

    /**
     * TODO: explain
     */
    interface OntopMappingSQLBuilderFragment<B extends Builder<B>> {

        B ppMapping(@Nonnull SQLPPMapping ppMapping);
    }

    interface Builder<B extends Builder<B>> extends OntopMappingSQLBuilderFragment<B>,
            OntopSQLCoreConfiguration.Builder<B>, OntopMappingConfiguration.Builder<B> {

        @Override
        OntopMappingSQLConfiguration build();
    }
}


