/*
 * Copyright (C) 2005-2012 Alfresco Software Limited.
 *
 * This file is part of Alfresco
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
 */
package org.alfresco.solr.query;

import java.io.IOException;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.IndexReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.similarities.TFIDFSimilarity;
import org.apache.lucene.util.Bits;
import org.apache.solr.search.SolrIndexSearcher;

/**
 * @author Andy
 *
 */
public class SolrAuthoritySetQuery extends Query
{

    String authorities;

    public SolrAuthoritySetQuery(String authorities)
    {
        this.authorities = authorities;
    }
    
    /*
     * @see org.apache.lucene.search.Query#createWeight(org.apache.lucene.search.Searcher)
     */
    @Override
    public Weight createWeight(IndexSearcher searcher) throws IOException
    {
        if(!(searcher instanceof SolrIndexSearcher))
        {
            throw new IllegalStateException("Must have a SolrIndexSearcher");
        }
        return new SolrAuthoritySetQueryWeight((SolrIndexSearcher)searcher);
    }

    /*
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString()
    {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("AUTHSET:");
        stringBuilder.append(authorities.toString());
        return stringBuilder.toString();
    }

    /*
     * @see org.apache.lucene.search.Query#toString(java.lang.String)
     */
    @Override
    public String toString(String field)
    {
        return toString();
    }


    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((authorities == null) ? 0 : authorities.hashCode());
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        SolrAuthoritySetQuery other = (SolrAuthoritySetQuery) obj;
        if (authorities == null)
        {
            if (other.authorities != null)
                return false;
        }
        else if (!authorities.equals(other.authorities))
            return false;
        return true;
    }






    private class SolrAuthoritySetQueryWeight extends Weight
    {
        SolrIndexSearcher searcher;
        
        private TFIDFSimilarity similarity;
        private float value;
        private float idf;
        private float queryNorm;
        private float queryWeight;
        private Explanation idfExp;

        public SolrAuthoritySetQueryWeight(SolrIndexSearcher searcher) throws IOException 
        {
            this.searcher = searcher;
            // TODO: doubtful this case will work?! Just to aid compilation at this stage.
            this.similarity = (TFIDFSimilarity) searcher.getSimilarity();
            CollectionStatistics collectionStats = searcher.collectionStatistics("AUTHSET");
            final IndexReaderContext context = searcher.getTopReaderContext();
            final Term term = new Term("AUTHSET", SolrAuthoritySetQuery.this.authorities);
            final TermContext termContext = TermContext.build(context, term);
            TermStatistics termStats = searcher.termStatistics(term, termContext);
            idfExp = similarity.idfExplain(collectionStats, termStats);
            idf = idfExp.getValue();
        }

        /*
         * @see org.apache.lucene.search.Weight#getQuery()
         */
        public Query getQuery()
        {
            return SolrAuthoritySetQuery.this;
        }

        /*
         * (non-Javadoc)
         * @see org.apache.lucene.search.Weight#sumOfSquaredWeights()
         */
        private float sumOfSquaredWeights() throws IOException
        {
            queryWeight = idf * getBoost();             // compute query weight
            return queryWeight * queryWeight;           // square it
        }

        /*
         * (non-Javadoc)
         * @see org.apache.lucene.search.Weight#scorer(org.apache.lucene.index.IndexReader, boolean, boolean)
         */
        @Override
//        public Scorer scorer(IndexReader reader, boolean scoreDocsInOrder, boolean topScorer) throws IOException
        public Scorer scorer(AtomicReaderContext context, Bits acceptDocs) throws IOException
        {
            // TODO: unable to access LiveDocsReader?
//            if(!(reader instanceof LiveDocsReader))
//            {
//                throw new IllegalStateException("Must have a LiveDocsReader");
//            }
//            return SolrAuthoritySetScorer.createAuthoritySetScorer(searcher, getSimilarity(searcher), SolrAuthoritySetQuery.this.authorities, (SolrIndexReader)reader);
            return SolrAuthoritySetScorer.createAuthoritySetScorer(this, context, searcher, SolrAuthoritySetQuery.this.authorities);
        }

        @Override
        public Explanation explain(AtomicReaderContext context, int doc) throws IOException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public float getValueForNormalization() throws IOException
        {            
            // TODO: correct?
            return sumOfSquaredWeights();
        }

        @Override
        public void normalize(float queryNorm, float topLevelBoost)
        {
            // TODO: what about topLevelBoost?
            this.queryNorm = queryNorm;
            queryWeight *= queryNorm;                   // normalize query weight
            value = queryWeight * idf;                  // idf for document
        }
    }
}