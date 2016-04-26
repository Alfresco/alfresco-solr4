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

import java.util.List;

import org.apache.lucene.search.Filter;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.BitsFilteredDocIdSet;
import org.apache.lucene.util.Bits;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.util.FixedBitSet;


/*
* A segment level Lucene Filter where each segment is backed by a FixedBitSet.
*/

public class BitsFilter extends Filter {

    private List<FixedBitSet> bitSets;

    public BitsFilter(List<FixedBitSet> bitSets)
    {
        this.bitSets = bitSets;
    }

    public void or(BitsFilter bitsFilter)
    {
        List<FixedBitSet> andSets = bitsFilter.bitSets;
        for(int i=0; i<bitSets.size(); i++)
        {
            FixedBitSet a = bitSets.get(i);
            FixedBitSet b = andSets.get(i);
            a.or(b);
        }
    }

    public void and(BitsFilter bitsFilter)
    {
        List<FixedBitSet> andSets = bitsFilter.bitSets;
        for(int i=0; i<bitSets.size(); i++)
        {
            FixedBitSet a = bitSets.get(i);
            FixedBitSet b = andSets.get(i);
            a.and(b);
        }
    }

    public List<FixedBitSet> getBitSets()
    {
        return this.bitSets;
    }

    public String toString(String s) {
        return s;
    }

    public DocIdSet getDocIdSet(AtomicReaderContext context, Bits bits) {
        return BitsFilteredDocIdSet.wrap(bitSets.get(context.ord), bits);
    }
}
