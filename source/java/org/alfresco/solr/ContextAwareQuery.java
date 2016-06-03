package org.alfresco.solr;

import java.io.IOException;
import java.util.Set;

import org.alfresco.service.cmr.search.SearchParameters;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Weight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andy
 *
 */
public class ContextAwareQuery extends Query
{
    protected final static Logger log = LoggerFactory.getLogger(ContextAwareQuery.class);

    private Query luceneQuery;
    
    private SearchParameters searchParameters;
    
    /**
     * @param luceneQuery Query
     * @param searchParameters SearchParameters
     */
    public ContextAwareQuery(Query luceneQuery, SearchParameters searchParameters)
    {
        this.luceneQuery = luceneQuery;
        this.searchParameters = searchParameters;
    }

    
    
    /**
     * @param b float
     * @see org.apache.lucene.search.Query#setBoost(float)
     */
    public void setBoost(float b)
    {
        luceneQuery.setBoost(b);
    }



    /**
     * @return float
     * @see org.apache.lucene.search.Query#getBoost()
     */
    public float getBoost()
    {
        return luceneQuery.getBoost();
    }



    /**
     * @param field String
     * @return String
     * @see org.apache.lucene.search.Query#toString(java.lang.String)
     */
    public String toString(String field)
    {
        return luceneQuery.toString(field);
    }



    /**
     * @return String
     * @see org.apache.lucene.search.Query#toString()
     */
    public String toString()
    {
        return luceneQuery.toString();
    }



    /**
     * @param searcher IndexSearcher
     * @return Weight
     * @throws IOException
     * @see org.apache.lucene.search.Query#createWeight(org.apache.lucene.search.IndexSearcher)
     */
    public Weight createWeight(IndexSearcher searcher) throws IOException
    {
        return luceneQuery.createWeight(searcher);
    }



    /**
     * @param reader IndexReader
     * @return Query
     * @throws IOException
     * @see org.apache.lucene.search.Query#rewrite(org.apache.lucene.index.IndexReader)
     */
    public Query rewrite(IndexReader reader) throws IOException
    {
        return luceneQuery.rewrite(reader);
    }



    /**
     * @param terms Set<Term>
     * @see org.apache.lucene.search.Query#extractTerms(java.util.Set)
     */
    public void extractTerms(Set<Term> terms)
    {
        luceneQuery.extractTerms(terms);
    }



    /**
     * @return Query
     * @see org.apache.lucene.search.Query#clone()
     */
    public Query clone()
    {
        return luceneQuery.clone();
    }



    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((luceneQuery == null) ? 0 : luceneQuery.hashCode());
        result = prime * result + ((searchParameters == null) ? 0 : searchParameters.hashCode());
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
        ContextAwareQuery other = (ContextAwareQuery) obj;
        if (luceneQuery == null)
        {
            if (other.luceneQuery != null)
                return false;
        }
        else if (!luceneQuery.equals(other.luceneQuery))
            return false;
        if (searchParameters == null)
        {
            if (other.searchParameters != null)
                return false;
        }
        else if (!searchParameters.equals(other.searchParameters))
            return false;
        return true;
    }

 

    
}
