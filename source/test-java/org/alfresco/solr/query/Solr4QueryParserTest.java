/*
 * #%L
 * Alfresco Solr 4
 * %%
 * Copyright (C) 2005 - 2016 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software. 
 * If the software was purchased under a paid Alfresco license, the terms of 
 * the paid license agreement will prevail.  Otherwise, the software is 
 * provided under the following open source license terms:
 * 
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.alfresco.solr.query;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedList;

import org.alfresco.repo.search.impl.parsers.FTSQueryParser;
import org.alfresco.repo.search.impl.parsers.FTSQueryParser.RerankPhase;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.util.Version;
import org.junit.Before;
import org.junit.Test;

public class Solr4QueryParserTest
{

    private Solr4QueryParser parser;
    private final static String TEST_FIELD = "creator";
    private final static String TEST_QUERY = "user@1user1";
    
    @Before
    public void setUp() throws Exception
    {
        parser = new Solr4QueryParser(null, Version.LUCENE_48, "TEXT", null, FTSQueryParser.RerankPhase.SINGLE_PASS);
    }

    @Test
    public void testFlatQueryShouldBeGeneratedFromSequentiallyShiftedTokens() throws Exception
    {
        // prepare test data
        LinkedList<Token> tokenSequenceWithRepeatedGroup = new LinkedList<Token>();
        tokenSequenceWithRepeatedGroup.add(new Token(TEST_QUERY.substring(0, 4), 0, 4, null));
        tokenSequenceWithRepeatedGroup.add(new Token(TEST_QUERY.substring(5, 6), 5, 6, null));
        tokenSequenceWithRepeatedGroup.add(new Token(TEST_QUERY.substring(6, 10), 6, 10, null));
        tokenSequenceWithRepeatedGroup.add(new Token(TEST_QUERY.substring(10, 11), 10, 11, null));
        
        assertTrue("All tokens in test data must be sequentially shifted",
                parser.isAllTokensSequentiallyShifted(tokenSequenceWithRepeatedGroup));
        assertTrue(parser.getEnablePositionIncrements());
        
        LinkedList<LinkedList<Token>> fixedTokenSequences = new LinkedList<LinkedList<Token>>();
        fixedTokenSequences.add(tokenSequenceWithRepeatedGroup);
        
        // call method to test
        SpanOrQuery q = parser.generateSpanOrQuery(TEST_FIELD, fixedTokenSequences);
        
        // check results
        assertNotNull(q);
        SpanQuery[] spanQuery = q.getClauses();
        assertEquals("Flat query must be generated", 1, spanQuery.length);
        assertTrue(spanQuery[0] instanceof SpanNearQuery);
        SpanNearQuery spanNearQuery = (SpanNearQuery) spanQuery[0];
        assertEquals("Slop between term must be 0", 0, spanNearQuery.getSlop());
        assertTrue("Terms must be in order", spanNearQuery.isInOrder());
        
        SpanQuery[] termClauses = spanNearQuery.getClauses();
        assertEquals("Flat query must be generated (Query: " + q + ")", tokenSequenceWithRepeatedGroup.size(), termClauses.length);
        for (int i = 0; i < termClauses.length; i++)
        {
            assertTrue(termClauses[i] instanceof SpanTermQuery);
            assertEquals("All tokens must become spanQuery terms",
                    tokenSequenceWithRepeatedGroup.get(i).toString(), ((SpanTermQuery) termClauses[i]).getTerm().text());
        }
    }
    
}
