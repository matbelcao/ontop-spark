package it.unibz.inf.ontop.identifiers;

/*
 * #%L
 * ontop-test
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

import it.unibz.inf.ontop.quest.AbstractVirtualModeTest;

/***
 * Tests that oracle identifiers for tables and columns are treated
 * correctly. Especially, that the unquoted identifiers are treated as uppercase, and
 * that the case of quoted identifiers is not changed
 */
public class OracleIdentifierTest extends AbstractVirtualModeTest {

	static final String owlfile = "resources/identifiers/identifiers.owl";
	static final String obdafile = "resources/identifiers/identifiers-oracle.obda";
	static final String propertiesfile = "resources/identifiers/identifiers-oracle.properties";

	public OracleIdentifierTest() {
		super(owlfile, obdafile ,propertiesfile);
	}


	/**
	 * Test use of lowercase, unquoted table, schema and column identifiers (also in target)
	 * @throws Exception
	 */
	public void testLowercaseUnquoted() throws Exception {
		String query = "PREFIX : <http://www.semanticweb.org/ontologies/2013/7/untitled-ontology-150#> SELECT ?x WHERE {?x a :Country} ORDER BY ?x";
		String val = runQueryAndReturnStringOfIndividualX(query);
		assertEquals("<http://www.semanticweb.org/ontologies/2013/7/untitled-ontology-150#Country-Argentina>", val);
	}


	/**
	 * Test use of lowercase, unquoted table and column identifiers (also in target) with uppercase table identifiers
	 * @throws Exception
	 */
	public void testUpperCaseTableUnquoted() throws Exception {
		String query = "PREFIX : <http://www.semanticweb.org/ontologies/2013/7/untitled-ontology-150#> SELECT ?x WHERE {?x a :Country2} ORDER BY ?x";
		String val =  runQueryAndReturnStringOfIndividualX(query);
		assertEquals("<http://www.semanticweb.org/ontologies/2013/7/untitled-ontology-150#Country2-Argentina>", val);
	}
	
	/**
	 * Test use of lowercase, quoted alias in a view definition 
	 * @throws Exception
	 */
	public void testLowerCaseColumnViewDefQuoted() throws Exception {
		String query = "PREFIX : <http://www.semanticweb.org/ontologies/2013/7/untitled-ontology-150#> SELECT ?x WHERE {?x a :Country4} ORDER BY ?x";
		String val =  runQueryAndReturnStringOfIndividualX(query);
		assertEquals("<http://www.semanticweb.org/ontologies/2013/7/untitled-ontology-150#Country4-Argentina>", val);
	}

	/**
	 * Test use of lowercase, unquoted alias in a view definition 
	 * @throws Exception
	 */
	public void testLowerCaseColumnViewDefUnquoted() throws Exception {
		String query = "PREFIX : <http://www.semanticweb.org/ontologies/2013/7/untitled-ontology-150#> SELECT ?x WHERE {?x a :Country5} ORDER BY ?x";
		String val =  runQueryAndReturnStringOfIndividualX(query);
		assertEquals("<http://www.semanticweb.org/ontologies/2013/7/untitled-ontology-150#Country5-Argentina>", val);
	}

	public void testDual() throws Exception {
		String query = "PREFIX : <http://www.semanticweb.org/ontologies/2013/7/untitled-ontology-150#> ASK WHERE {:Italy a :Country .} ";
		Boolean val =  runASKTests(query);
		assertTrue(val);
	}

}

