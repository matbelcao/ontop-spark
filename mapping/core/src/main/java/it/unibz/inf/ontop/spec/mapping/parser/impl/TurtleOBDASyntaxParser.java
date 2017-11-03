package it.unibz.inf.ontop.spec.mapping.parser.impl;

/*
 * #%L
 * ontop-obdalib-core
 * %%
 * Copyright (C) 2009 - 2014 Free University of Bozen-Bolzano
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import it.unibz.inf.ontop.model.IriConstants;
import it.unibz.inf.ontop.model.atom.AtomFactory;
import it.unibz.inf.ontop.model.term.ImmutableFunctionalTerm;

import java.util.Map;

import it.unibz.inf.ontop.model.term.TermFactory;
import it.unibz.inf.ontop.model.type.TypeFactory;
import it.unibz.inf.ontop.spec.mapping.parser.TargetQueryParser;
import it.unibz.inf.ontop.exception.TargetQueryParserException;
import it.unibz.inf.ontop.utils.ImmutableCollectors;
import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;

public class TurtleOBDASyntaxParser implements TargetQueryParser {

	private final Map<String, String> prefixes;
	private final AtomFactory atomFactory;
	private final TermFactory termFactory;
	private final TypeFactory typeFactory;

	/**
	 * Default constructor;
	 * @param atomFactory
	 * @param termFactory
	 * @param typeFactory
	 */
	public TurtleOBDASyntaxParser(AtomFactory atomFactory, TermFactory termFactory, TypeFactory typeFactory) {
		this.atomFactory = atomFactory;
		this.termFactory = termFactory;
		this.typeFactory = typeFactory;
		this.prefixes = ImmutableMap.of();
	}

	/**
	 * Constructs the parser object with prefixes. These prefixes will
	 * help to generate the query header that contains the prefix definitions
	 * (i.e., the directives @BASE and @PREFIX).
	 *
	 */
	public TurtleOBDASyntaxParser(Map<String, String> prefixes, AtomFactory atomFactory, TermFactory termFactory,
								  TypeFactory typeFactory) {
		this.prefixes = prefixes;
		this.atomFactory = atomFactory;
		this.termFactory = termFactory;
		this.typeFactory = typeFactory;
	}

	/**
	 * Returns the CQIE object from the input string. If the input prefix
	 * manager is null then no directive header will be appended.
	 * 
	 * @param input
	 *            A target query string written in Turtle syntax.
	 * @return A CQIE object.
	 */
	@Override
	public ImmutableList<ImmutableFunctionalTerm> parse(String input) throws TargetQueryParserException {
		StringBuffer bf = new StringBuffer(input.trim());
		if (!bf.substring(bf.length() - 2, bf.length()).equals(" .")) {
			bf.insert(bf.length() - 1, ' ');
		}
		if (!prefixes.isEmpty()) {
			// Update the input by appending the directives
			appendDirectives(bf);
		}		
		try {
			ANTLRStringStream inputStream = new ANTLRStringStream(bf.toString());
			TurtleOBDALexer lexer = new TurtleOBDALexer(inputStream);
			CommonTokenStream tokenStream = new CommonTokenStream(lexer);
			TurtleOBDAParser parser = new TurtleOBDAParser(tokenStream, atomFactory, termFactory, typeFactory);
			return parser.parse().stream()
					.map(termFactory::getImmutableFunctionalTerm)
					.collect(ImmutableCollectors.toList());
		} catch (RecognitionException e) {
			throw new TargetQueryParserException(input, e);
		} catch (RuntimeException e) {
			throw new TargetQueryParserException(input, e);
		}
	}

	/**
	 * The turtle syntax predefines the quest, rdf, rdfs and owl prefixes.
	 * 
	 * Adds directives to the query header from the PrefixManager.
	 */
	private void appendDirectives(StringBuffer query) {
		StringBuffer sb = new StringBuffer();
		for (String prefix : prefixes.keySet()) {
			sb.append("@PREFIX");
			sb.append(" ");
			sb.append(prefix);
			sb.append(" ");
			sb.append("<");
			sb.append(prefixes.get(prefix));
			sb.append(">");
			sb.append(" .\n");
		}
		sb.append("@PREFIX " + IriConstants.PREFIX_XSD + " <" + IriConstants.NS_XSD + "> .\n");
		sb.append("@PREFIX " + IriConstants.PREFIX_OBDA + " <" + IriConstants.NS_OBDA + "> .\n");
		sb.append("@PREFIX " + IriConstants.PREFIX_RDF + " <" + IriConstants.NS_RDF + "> .\n");
		sb.append("@PREFIX " + IriConstants.PREFIX_RDFS + " <" + IriConstants.NS_RDFS + "> .\n");
		sb.append("@PREFIX " + IriConstants.PREFIX_OWL + " <" + IriConstants.NS_OWL + "> .\n");
		query.insert(0, sb);
	}
}
