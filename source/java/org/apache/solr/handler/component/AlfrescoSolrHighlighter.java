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
package org.apache.solr.handler.component;

import static org.alfresco.repo.search.adaptor.lucene.QueryConstants.FIELD_SOLR4_ID;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.NoInitialContextException;

import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.solr.AlfrescoSolrDataModel;
import org.alfresco.solr.AlfrescoSolrDataModel.FieldUse;
import org.alfresco.solr.AlfrescoSolrDataModel.TenantAclIdDbId;
import org.alfresco.solr.content.SolrContentStore;
import org.alfresco.solr.content.SolrContentUrlBuilder;
import org.apache.lucene.analysis.CachingTokenFilter;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.highlight.Encoder;
import org.apache.lucene.search.highlight.Formatter;
import org.apache.lucene.search.highlight.Fragmenter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.OffsetLimitTokenFilter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.QueryTermScorer;
import org.apache.lucene.search.highlight.Scorer;
import org.apache.lucene.search.highlight.TextFragment;
import org.apache.lucene.search.highlight.TokenSources;
import org.apache.lucene.search.vectorhighlight.BoundaryScanner;
import org.apache.lucene.search.vectorhighlight.FastVectorHighlighter;
import org.apache.lucene.search.vectorhighlight.FieldQuery;
import org.apache.lucene.search.vectorhighlight.FragListBuilder;
import org.apache.lucene.search.vectorhighlight.FragmentsBuilder;
import org.apache.lucene.util.AttributeSource.State;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.HighlightParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.JavaBinCodec;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.core.PluginInfo;
import org.apache.solr.core.SolrConfig;
import org.apache.solr.core.SolrCore;
import org.apache.solr.core.SolrResourceLoader;
import org.apache.solr.highlight.DefaultEncoder;
import org.apache.solr.highlight.GapFragmenter;
import org.apache.solr.highlight.HtmlFormatter;
import org.apache.solr.highlight.ScoreOrderFragmentsBuilder;
import org.apache.solr.highlight.SimpleBoundaryScanner;
import org.apache.solr.highlight.SimpleFragListBuilder;
import org.apache.solr.highlight.SolrBoundaryScanner;
import org.apache.solr.highlight.SolrEncoder;
import org.apache.solr.highlight.SolrFormatter;
import org.apache.solr.highlight.SolrFragListBuilder;
import org.apache.solr.highlight.SolrFragmenter;
import org.apache.solr.highlight.SolrFragmentsBuilder;
import org.apache.solr.highlight.SolrHighlighter;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocList;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.update.DocumentBuilder;
import org.apache.solr.util.plugin.PluginInfoInitialized;
import org.quartz.JobExecutionException;

/**
 * @author Andy
 */
public class AlfrescoSolrHighlighter extends SolrHighlighter implements PluginInfoInitialized
{
    static SolrContentStore solrContentStore;
    
   

   static
    {
        if(solrContentStore == null)
        {
            try
            {
                solrContentStore = getSolrContentStore(SolrResourceLoader.locateSolrHome());
            }
            catch (JobExecutionException e)
            {
            }
        }
    }
    
   private static SolrContentStore getSolrContentStore(String solrHome) throws JobExecutionException
   {
       // TODO: Could specify the rootStr from a properties file.
       return new SolrContentStore(locateContentHome(solrHome));
   }
   
   public static String locateContentHome(String solrHome)
   {
       String contentDir = null;
       // Try JNDI
       try
       {
           Context c = new InitialContext();
           contentDir = (String) c.lookup("java:comp/env/solr/content/dir");
           log.info("Using JNDI solr.content.dir: " + contentDir);
       }
       catch (NoInitialContextException e)
       {
           log.info("JNDI not configured for solr (NoInitialContextEx)");
       }
       catch (NamingException e)
       {
           log.info("No solr/content/dir in JNDI");
       }
       catch (RuntimeException ex)
       {
           log.warn("Odd RuntimeException while testing for JNDI: " + ex.getMessage());
       }

       // Now try system property
       if (contentDir == null)
       {
           String prop = "solr.solr.content.dir";
           contentDir = System.getProperty(prop);
           if (contentDir != null)
           {
               log.info("using system property " + prop + ": " + contentDir);
           }
       }

       // if all else fails, try
       if (contentDir == null)
       {
           return solrHome + "ContentStore";

       }
       else
       {
           return contentDir;
       }
   }
   
    private SolrCore solrCore;
    
    JavaBinCodec.ObjectResolver resolver = new JavaBinCodec.ObjectResolver()
    {
        @Override
        public Object resolve(Object o, JavaBinCodec codec) throws IOException
        {
            if (o instanceof BytesRef)
            {
                BytesRef br = (BytesRef) o;
                codec.writeByteArray(br.bytes, br.offset, br.length);
                return null;
            }
            return o;
        }
    };

    public AlfrescoSolrHighlighter()
    {
    }

    public AlfrescoSolrHighlighter(SolrCore solrCore)
    {
        this.solrCore = solrCore;
    }

    // Thread safe registry
    protected final Map<String, SolrFormatter> formatters = new HashMap<>();

    // Thread safe registry
    protected final Map<String, SolrEncoder> encoders = new HashMap<>();

    // Thread safe registry
    protected final Map<String, SolrFragmenter> fragmenters = new HashMap<>();

    // Thread safe registry
    protected final Map<String, SolrFragListBuilder> fragListBuilders = new HashMap<>();

    // Thread safe registry
    protected final Map<String, SolrFragmentsBuilder> fragmentsBuilders = new HashMap<>();

    // Thread safe registry
    protected final Map<String, SolrBoundaryScanner> boundaryScanners = new HashMap<>();

    @Override
    public void init(PluginInfo info)
    {
        formatters.clear();
        encoders.clear();
        fragmenters.clear();
        fragListBuilders.clear();
        fragmentsBuilders.clear();
        boundaryScanners.clear();

        // Load the fragmenters
        SolrFragmenter frag = solrCore.initPlugins(info.getChildren("fragmenter"), fragmenters, SolrFragmenter.class, null);
        if (frag == null) frag = new GapFragmenter();
        fragmenters.put("", frag);
        fragmenters.put(null, frag);

        // Load the formatters
        SolrFormatter fmt = solrCore.initPlugins(info.getChildren("formatter"), formatters, SolrFormatter.class, null);
        if (fmt == null) fmt = new HtmlFormatter();
        formatters.put("", fmt);
        formatters.put(null, fmt);

        // Load the encoders
        SolrEncoder enc = solrCore.initPlugins(info.getChildren("encoder"), encoders, SolrEncoder.class, null);
        if (enc == null) enc = new DefaultEncoder();
        encoders.put("", enc);
        encoders.put(null, enc);

        // Load the FragListBuilders
        SolrFragListBuilder fragListBuilder = solrCore.initPlugins(info.getChildren("fragListBuilder"), fragListBuilders, SolrFragListBuilder.class, null);
        if( fragListBuilder == null ) fragListBuilder = new SimpleFragListBuilder();
        fragListBuilders.put("", fragListBuilder);
        fragListBuilders.put(null, fragListBuilder);

        // Load the FragmentsBuilders
        SolrFragmentsBuilder fragsBuilder = solrCore.initPlugins(info.getChildren("fragmentsBuilder"), fragmentsBuilders, SolrFragmentsBuilder.class, null);
        if( fragsBuilder == null ) fragsBuilder = new ScoreOrderFragmentsBuilder();
        fragmentsBuilders.put("", fragsBuilder);
        fragmentsBuilders.put(null, fragsBuilder);

        // Load the BoundaryScanners
        SolrBoundaryScanner boundaryScanner = solrCore.initPlugins(info.getChildren("boundaryScanner"), boundaryScanners, SolrBoundaryScanner.class, null);
        if(boundaryScanner == null) boundaryScanner = new SimpleBoundaryScanner();
        boundaryScanners.put("", boundaryScanner);
        boundaryScanners.put(null, boundaryScanner);
        initialized = true;
    }

    // just for back-compat with the deprecated method
    private boolean initialized = false;

    @Override
    @Deprecated
    public void initalize(SolrConfig config)
    {
        if (initialized)
            return;
        SolrFragmenter frag = new GapFragmenter();
        fragmenters.put("", frag);
        fragmenters.put(null, frag);

        SolrFormatter fmt = new HtmlFormatter();
        formatters.put("", fmt);
        formatters.put(null, fmt);

        SolrEncoder enc = new DefaultEncoder();
        encoders.put("", enc);
        encoders.put(null, enc);

        SolrFragListBuilder fragListBuilder = new SimpleFragListBuilder();
        fragListBuilders.put("", fragListBuilder);
        fragListBuilders.put(null, fragListBuilder);

        SolrFragmentsBuilder fragsBuilder = new ScoreOrderFragmentsBuilder();
        fragmentsBuilders.put("", fragsBuilder);
        fragmentsBuilders.put(null, fragsBuilder);

        SolrBoundaryScanner boundaryScanner = new SimpleBoundaryScanner();
        boundaryScanners.put("", boundaryScanner);
        boundaryScanners.put(null, boundaryScanner);
    }

    /**
     * Return a phrase {@link org.apache.lucene.search.highlight.Highlighter} appropriate for this field.
     * 
     * @param query
     *            The current Query
     * @param fieldName
     *            The name of the field
     * @param request
     *            The current SolrQueryRequest
     * @param tokenStream
     *            document text CachingTokenStream
     * @throws IOException
     *             If there is a low-level I/O error.
     */
    protected Highlighter getPhraseHighlighter(Query query, String requestFieldname, String schemaFieldName, SolrQueryRequest request, CachingTokenFilter tokenStream) throws IOException
    {
        SolrParams params = request.getParams();
        Highlighter highlighter = null;

        highlighter = new Highlighter(getFormatter(requestFieldname, params), getEncoder(requestFieldname, params), getSpanQueryScorer(query, requestFieldname, schemaFieldName, tokenStream, request));

        highlighter.setTextFragmenter(getFragmenter(requestFieldname, params));

        return highlighter;
    }

    /**
     * Return a {@link org.apache.lucene.search.highlight.Highlighter} appropriate for this field.
     * 
     * @param query
     *            The current Query
     * @param fieldName
     *            The name of the field
     * @param request
     *            The current SolrQueryRequest
     */
    protected Highlighter getHighlighter(Query query, String requestFieldname, String schemaFieldName, SolrQueryRequest request)
    {
        SolrParams params = request.getParams();
        Highlighter highlighter = new Highlighter(getFormatter(requestFieldname, params), getEncoder(requestFieldname, params), getQueryScorer(query, requestFieldname, schemaFieldName, request));
        highlighter.setTextFragmenter(getFragmenter(requestFieldname, params));
        return highlighter;
    }

    /**
     * Return a {@link org.apache.lucene.search.highlight.QueryScorer} suitable for this Query and field.
     * 
     * @param query
     *            The current query
     * @param tokenStream
     *            document text CachingTokenStream
     * @param fieldName
     *            The name of the field
     * @param request
     *            The SolrQueryRequest
     */
    private QueryScorer getSpanQueryScorer(Query query, String requestFieldname, String schemaFieldName, TokenStream tokenStream, SolrQueryRequest request)
    {
        boolean reqFieldMatch = request.getParams().getFieldBool(requestFieldname, HighlightParams.FIELD_MATCH, false);
        Boolean highlightMultiTerm = request.getParams().getBool(HighlightParams.HIGHLIGHT_MULTI_TERM, true);
        if (highlightMultiTerm == null)
        {
            highlightMultiTerm = false;
        }
        QueryScorer scorer;
        if (reqFieldMatch)
        {
            scorer = new QueryScorer(query, schemaFieldName);
        }
        else
        {
            scorer = new QueryScorer(query, null);
        }
        scorer.setExpandMultiTermQuery(highlightMultiTerm);
        return scorer;
    }

    /**
     * Return a {@link org.apache.lucene.search.highlight.Scorer} suitable for this Query and field.
     * 
     * @param query
     *            The current query
     * @param fieldName
     *            The name of the field
     * @param request
     *            The SolrQueryRequest
     */
    private Scorer getQueryScorer(Query query, String requestFieldname, String schemaFieldName, SolrQueryRequest request)
    {
        boolean reqFieldMatch = request.getParams().getFieldBool(requestFieldname, HighlightParams.FIELD_MATCH, false);
        if (reqFieldMatch)
        {
            return new QueryTermScorer(query, request.getSearcher().getIndexReader(), schemaFieldName);
        }
        else
        {
            return new QueryTermScorer(query);
        }
    }

    /**
     * Return the max number of snippets for this field. If this has not been configured for this field, fall back to
     * the configured default or the solr default.
     * 
     * @param fieldName
     *            The name of the field
     * @param params
     *            The params controlling Highlighting
     */
    protected int getMaxSnippets(String fieldName, SolrParams params)
    {
        return params.getFieldInt(fieldName, HighlightParams.SNIPPETS, 1);
    }

    /**
     * Return whether adjacent fragments should be merged.
     * 
     * @param fieldName
     *            The name of the field
     * @param params
     *            The params controlling Highlighting
     */
    protected boolean isMergeContiguousFragments(String fieldName, SolrParams params)
    {
        return params.getFieldBool(fieldName, HighlightParams.MERGE_CONTIGUOUS_FRAGMENTS, false);
    }

    /**
     * Return a {@link org.apache.lucene.search.highlight.Formatter} appropriate for this field. If a formatter has not
     * been configured for this field, fall back to the configured default or the solr default (
     * {@link org.apache.lucene.search.highlight.SimpleHTMLFormatter}).
     * 
     * @param fieldName
     *            The name of the field
     * @param params
     *            The params controlling Highlighting
     * @return An appropriate {@link org.apache.lucene.search.highlight.Formatter}.
     */
    protected Formatter getFormatter(String fieldName, SolrParams params)
    {
        String str = params.getFieldParam(fieldName, HighlightParams.FORMATTER);
        SolrFormatter formatter = formatters.get(str);
        if (formatter == null)
        {
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Unknown formatter: " + str);
        }
        return formatter.getFormatter(fieldName, params);
    }

    /**
     * Return an {@link org.apache.lucene.search.highlight.Encoder} appropriate for this field. If an encoder has not
     * been configured for this field, fall back to the configured default or the solr default (
     * {@link org.apache.lucene.search.highlight.DefaultEncoder}).
     * 
     * @param fieldName
     *            The name of the field
     * @param params
     *            The params controlling Highlighting
     * @return An appropriate {@link org.apache.lucene.search.highlight.Encoder}.
     */
    protected Encoder getEncoder(String fieldName, SolrParams params)
    {
        String str = params.getFieldParam(fieldName, HighlightParams.ENCODER);
        SolrEncoder encoder = encoders.get(str);
        if (encoder == null)
        {
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Unknown encoder: " + str);
        }
        return encoder.getEncoder(fieldName, params);
    }

    /**
     * Return a {@link org.apache.lucene.search.highlight.Fragmenter} appropriate for this field. If a fragmenter has
     * not been configured for this field, fall back to the configured default or the solr default (
     * {@link GapFragmenter}).
     * 
     * @param fieldName
     *            The name of the field
     * @param params
     *            The params controlling Highlighting
     * @return An appropriate {@link org.apache.lucene.search.highlight.Fragmenter}.
     */
    protected Fragmenter getFragmenter(String fieldName, SolrParams params)
    {
        String fmt = params.getFieldParam(fieldName, HighlightParams.FRAGMENTER);
        SolrFragmenter frag = fragmenters.get(fmt);
        if (frag == null)
        {
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Unknown fragmenter: " + fmt);
        }
        return frag.getFragmenter(fieldName, params);
    }

    protected FragListBuilder getFragListBuilder(String fieldName, SolrParams params)
    {
        String flb = params.getFieldParam(fieldName, HighlightParams.FRAG_LIST_BUILDER);
        SolrFragListBuilder solrFlb = fragListBuilders.get(flb);
        if (solrFlb == null)
        {
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Unknown fragListBuilder: " + flb);
        }
        return solrFlb.getFragListBuilder(params);
    }

    protected FragmentsBuilder getFragmentsBuilder(String fieldName, SolrParams params)
    {
        BoundaryScanner bs = getBoundaryScanner(fieldName, params);
        return getSolrFragmentsBuilder(fieldName, params).getFragmentsBuilder(params, bs);
    }

    private SolrFragmentsBuilder getSolrFragmentsBuilder(String fieldName, SolrParams params)
    {
        String fb = params.getFieldParam(fieldName, HighlightParams.FRAGMENTS_BUILDER);
        SolrFragmentsBuilder solrFb = fragmentsBuilders.get(fb);
        if (solrFb == null)
        {
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Unknown fragmentsBuilder: " + fb);
        }
        return solrFb;
    }

    private BoundaryScanner getBoundaryScanner(String fieldName, SolrParams params)
    {
        String bs = params.getFieldParam(fieldName, HighlightParams.BOUNDARY_SCANNER);
        SolrBoundaryScanner solrBs = boundaryScanners.get(bs);
        if (solrBs == null)
        {
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Unknown boundaryScanner: " + bs);
        }
        return solrBs.getBoundaryScanner(fieldName, params);
    }

    /**
     * Generates a list of Highlighted query fragments for each item in a list of documents, or returns null if
     * highlighting is disabled.
     *
     * @param docs
     *            query results
     * @param query
     *            the query
     * @param req
     *            the current request
     * @param defaultFields
     *            default list of fields to summarize
     * @return NamedList containing a NamedList for each document, which in turns contains sets (field, summary) pairs.
     */
    @Override
    @SuppressWarnings("unchecked")
    public NamedList<Object> doHighlighting(DocList docs, Query query, SolrQueryRequest req, String[] defaultFields) throws IOException
    {
        SolrParams params = req.getParams();
        if (!isHighlightingEnabled(params))
            return null;

        SolrIndexSearcher searcher = req.getSearcher();
        IndexSchema schema = searcher.getSchema();
        NamedList fragments = new SimpleOrderedMap();
        String[] fieldNames = getHighlightFields(query, req, defaultFields);
        Set<String> fset = new HashSet<>();

        {
            // pre-fetch documents using the Searcher's doc cache
            for (String f : fieldNames)
            {
                fset.add(f);
            }
            // fetch unique key if one exists.
            SchemaField keyField = schema.getUniqueKeyField();
            if (null != keyField)
                fset.add(keyField.getName());
        }

        // get FastVectorHighlighter instance out of the processing loop
        FastVectorHighlighter fvh = new FastVectorHighlighter(
        // FVH cannot process hl.usePhraseHighlighter parameter per-field basis
                params.getBool(HighlightParams.USE_PHRASE_HIGHLIGHTER, true),
                // FVH cannot process hl.requireFieldMatch parameter per-field basis
                params.getBool(HighlightParams.FIELD_MATCH, false));
        fvh.setPhraseLimit(params.getInt(HighlightParams.PHRASE_LIMIT, SolrHighlighter.DEFAULT_PHRASE_LIMIT));
        FieldQuery fieldQuery = fvh.getFieldQuery(query, searcher.getIndexReader());

        // Highlight each document
        DocIterator iterator = docs.iterator();
        for (int i = 0; i < docs.size(); i++)
        {
            int docId = iterator.nextDoc();
            Document doc = getDocument(searcher.doc(docId, fset), req);
            NamedList docSummaries = new SimpleOrderedMap();
            for (String fieldName : fieldNames)
            {
                fieldName = fieldName.trim();
                if (useFastVectorHighlighter(params, schema, fieldName))
                    doHighlightingByFastVectorHighlighter(fvh, fieldQuery, req, docSummaries, docId, doc, fieldName);
                else
                    doHighlightingByHighlighter(query, req, docSummaries, docId, doc, fieldName, 0);
            }
            String printId = schema.printableUniqueKey(doc);
            if(doc.get("DBID") != null)
			{
            	docSummaries.add("DBID", doc.get("DBID"));
			}
            fragments.add(printId == null ? null : printId, docSummaries);
        }
        return fragments;
    }

    /*
     * If fieldName is undefined, this method returns false, then doHighlightingByHighlighter() will do nothing for the
     * field.
     */
    private boolean useFastVectorHighlighter(SolrParams params, IndexSchema schema, String fieldName)
    {
        SchemaField schemaField = schema.getFieldOrNull(fieldName);
        if (schemaField == null)
            return false;
        boolean useFvhParam = params.getFieldBool(fieldName, HighlightParams.USE_FVH, false);
        if (!useFvhParam)
            return false;
        boolean termPosOff = schemaField.storeTermPositions() && schemaField.storeTermOffsets();
        if (!termPosOff)
        {
            log.warn("Solr will use Highlighter instead of FastVectorHighlighter because {} field does not store TermPositions and TermOffsets.", fieldName);
        }
        return termPosOff;
    }

    private void doHighlightingByHighlighter(Query query, SolrQueryRequest req, NamedList docSummaries, int docId, Document doc, String requestFieldname, int position) throws IOException
    {
        String schemaFieldName = AlfrescoSolrDataModel.getInstance().mapProperty(requestFieldname, FieldUse.HIGHLIGHT, req, position);
        
        final SolrIndexSearcher searcher = req.getSearcher();
        final IndexSchema schema = searcher.getSchema();

        // TODO: Currently in trunk highlighting numeric fields is broken (Lucene) -
        // so we disable them until fixed (see LUCENE-3080)!
        // BEGIN: Hack
        final SchemaField schemaField = schema.getFieldOrNull(schemaFieldName);
        if (schemaField != null && ((schemaField.getType() instanceof org.apache.solr.schema.TrieField) || (schemaField.getType() instanceof org.apache.solr.schema.TrieDateField)))
            return;
        // END: Hack

        SolrParams params = req.getParams();

        // preserve order of values in a multiValued list
        boolean preserveMulti = params.getFieldBool(requestFieldname, HighlightParams.PRESERVE_MULTI, false);

        List<IndexableField> allFields = doc.getFields();
        if (allFields != null && allFields.size() == 0)
            return; // No explicit contract that getFields returns != null,
        // although currently it can't.

        TokenStream tstream = null;
        int numFragments = getMaxSnippets(requestFieldname, params);
        boolean mergeContiguousFragments = isMergeContiguousFragments(requestFieldname, params);

        String[] summaries = null;
        List<TextFragment> frags = new ArrayList<>();

        TermOffsetsTokenStream tots = null; // to be non-null iff we're using TermOffsets optimization
        TokenStream tvStream = TokenSources.getTokenStreamWithOffsets(searcher.getIndexReader(), docId, schemaFieldName);
        if (tvStream != null)
        {
            tots = new TermOffsetsTokenStream(tvStream);
        }
        int mvToExamine = Integer.parseInt(req.getParams().get(HighlightParams.MAX_MULTIVALUED_TO_EXAMINE, Integer.toString(Integer.MAX_VALUE)));
        int mvToMatch = Integer.parseInt(req.getParams().get(HighlightParams.MAX_MULTIVALUED_TO_MATCH, Integer.toString(Integer.MAX_VALUE)));

        for (IndexableField thisField : allFields)
        {
            if (mvToExamine <= 0 || mvToMatch <= 0)
                break;

            if (!thisField.name().equals(schemaFieldName))
                continue; // Is there a better way to do this?

            --mvToExamine;
            String thisText = thisField.stringValue();

            if (tots != null)
            {
                // if we're using TermOffsets optimization, then get the next
                // field value's TokenStream (i.e. get field j's TokenStream) from tots:
                tstream = tots.getMultiValuedTokenStream(thisText.length());
            }
            else
            {
                // fall back to analyzer
                tstream = createAnalyzerTStream(schema, schemaFieldName, thisText);
            }

            int maxCharsToAnalyze = params.getFieldInt(requestFieldname, HighlightParams.MAX_CHARS, Highlighter.DEFAULT_MAX_CHARS_TO_ANALYZE);

            Highlighter highlighter;
            if (Boolean.valueOf(req.getParams().get(HighlightParams.USE_PHRASE_HIGHLIGHTER, "true")))
            {
                if (maxCharsToAnalyze < 0)
                {
                    tstream = new CachingTokenFilter(tstream);
                }
                else
                {
                    tstream = new CachingTokenFilter(new OffsetLimitTokenFilter(tstream, maxCharsToAnalyze));
                }

                // get highlighter
                highlighter = getPhraseHighlighter(query, requestFieldname, schemaFieldName, req, (CachingTokenFilter) tstream);

                // after highlighter initialization, reset tstream since construction of highlighter already used it
                tstream.reset();
            }
            else
            {
                // use "the old way"
                highlighter = getHighlighter(query, requestFieldname, schemaFieldName, req);
            }

            if (maxCharsToAnalyze < 0)
            {
                highlighter.setMaxDocCharsToAnalyze(thisText.length());
            }
            else
            {
                highlighter.setMaxDocCharsToAnalyze(maxCharsToAnalyze);
            }

            try
            {
                TextFragment[] bestTextFragments = highlighter.getBestTextFragments(tstream, fixLocalisedText(thisText), mergeContiguousFragments, numFragments);
                for (int k = 0; k < bestTextFragments.length; k++)
                {
                    if (preserveMulti)
                    {
                        if (bestTextFragments[k] != null)
                        {
                            frags.add(bestTextFragments[k]);
                            --mvToMatch;
                        }
                    }
                    else
                    {
                        if ((bestTextFragments[k] != null) && (bestTextFragments[k].getScore() > 0))
                        {
                            frags.add(bestTextFragments[k]);
                            --mvToMatch;
                        }
                    }
                }
            }
            catch (InvalidTokenOffsetsException e)
            {
                throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, e);
            }
        }
        // sort such that the fragments with the highest score come first
        if (!preserveMulti)
        {
            Collections.sort(frags, new Comparator<TextFragment>()
            {
                @Override
                public int compare(TextFragment arg0, TextFragment arg1)
                {
                    return Math.round(arg1.getScore() - arg0.getScore());
                }
            });
        }

        // convert fragments back into text
        // TODO: we can include score and position information in output as snippet attributes
        if (frags.size() > 0)
        {
            ArrayList<String> fragTexts = new ArrayList<>();
            for (TextFragment fragment : frags)
            {
                if (preserveMulti)
                {
                    if (fragment != null)
                    {
                        fragTexts.add(fragment.toString());
                    }
                }
                else
                {
                    if ((fragment != null) && (fragment.getScore() > 0))
                    {
                        fragTexts.add(fragment.toString());
                    }
                }

                if (fragTexts.size() >= numFragments && !preserveMulti)
                    break;
            }
            summaries = fragTexts.toArray(new String[0]);
            if (summaries.length > 0)
                docSummaries.add(requestFieldname, summaries);
        }
        if ((summaries == null || summaries.length == 0) && position == 0)
        {
        	 doHighlightingByHighlighter(query, req, docSummaries, docId, doc, requestFieldname, 1);
        }
        // no summeries made, copy text from alternate field
        if (summaries == null || summaries.length == 0)
        {
            alternateField(docSummaries, params, doc, requestFieldname, schemaFieldName, req);
        }
    }

    private String fixLocalisedText(String text) 
    {
    	if((text == null) || (text.length() == 0))
    	{
    		return text;
    	}
    
    	if(text.charAt(0) == '\u0000' )
    	{
    		int index = text.indexOf('\u0000', 1);
    		if(index == -1)
    		{
    			return text;
    		}
    		else
    		{
    			if(index + 1 < text.length())
    			{
    				return text.substring(index+1);
    			}
    			else
    			{
    				return text;
    			}
    		}
    	}
    	else
    	{
    		return text;
    	}
		
	}

	private void doHighlightingByFastVectorHighlighter(FastVectorHighlighter highlighter, FieldQuery fieldQuery, SolrQueryRequest req, NamedList docSummaries, int docId,
            Document doc, String fieldName) throws IOException
    {
        SolrParams params = req.getParams();
        SolrFragmentsBuilder solrFb = getSolrFragmentsBuilder(fieldName, params);
        String[] snippets = highlighter.getBestFragments(fieldQuery, req.getSearcher().getIndexReader(), docId, fieldName,
                params.getFieldInt(fieldName, HighlightParams.FRAGSIZE, 100), params.getFieldInt(fieldName, HighlightParams.SNIPPETS, 1), getFragListBuilder(fieldName, params),
                getFragmentsBuilder(fieldName, params), solrFb.getPreTags(params, fieldName), solrFb.getPostTags(params, fieldName), getEncoder(fieldName, params));
        if (snippets != null && snippets.length > 0)
            docSummaries.add(fieldName, snippets);
        else
            alternateField(docSummaries, params, doc, fieldName, fieldName, req);
    }

    private void alternateField(NamedList docSummaries, SolrParams params, Document doc, String requestFieldname, String schemaFieldName, SolrQueryRequest req)
    {
        String requestAlternateField = params.getFieldParam(requestFieldname, HighlightParams.ALTERNATE_FIELD);
        if (requestAlternateField != null && requestAlternateField.length() > 0)
        {
            String schemaAlternateFieldName = AlfrescoSolrDataModel.getInstance().mapProperty(requestAlternateField, FieldUse.HIGHLIGHT, req);
            IndexableField[] docFields = doc.getFields(schemaAlternateFieldName);
            if (docFields.length == 0)
            {
                // The alternate field did not exist, treat the original field as fallback instead
                docFields = doc.getFields(schemaFieldName);
            }
            List<String> listFields = new ArrayList<>();
            for (IndexableField field : docFields)
            {
                if (field.binaryValue() == null)
                    listFields.add(field.stringValue());
            }

            String[] altTexts = listFields.toArray(new String[listFields.size()]);

            if (altTexts != null && altTexts.length > 0)
            {
                Encoder encoder = getEncoder(requestFieldname, params);
                int alternateFieldLen = params.getFieldInt(requestFieldname, HighlightParams.ALTERNATE_FIELD_LENGTH, 0);
                List<String> altList = new ArrayList<>();
                int len = 0;
                for (String altText : altTexts)
                {
                    if (alternateFieldLen <= 0)
                    {
                        altList.add(encoder.encodeText(altText));
                    }
                    else
                    {
                        altList.add(len + altText.length() > alternateFieldLen ? encoder.encodeText(new String(altText.substring(0, alternateFieldLen - len))) : encoder
                                .encodeText(altText));
                        len += altText.length();
                        if (len >= alternateFieldLen)
                            break;
                    }
                }
                docSummaries.add(requestFieldname, altList);
            }
        }
    }

    private TokenStream createAnalyzerTStream(IndexSchema schema, String fieldName, String docText) throws IOException
    {

        TokenStream tstream;
        TokenStream ts = schema.getIndexAnalyzer().tokenStream(fieldName, docText);
        ts.reset();
        tstream = new TokenOrderingFilter(ts, 10);
        return tstream;
    }
    
    
    private SolrInputDocument retrieveDocFromSolrContentStore(String tenant, long dbId) throws IOException
    {
        String contentUrl = SolrContentUrlBuilder
                    .start()
                    .add(SolrContentUrlBuilder.KEY_TENANT, tenant)
                    .add(SolrContentUrlBuilder.KEY_DB_ID, String.valueOf(dbId))
                    .get();
        ContentReader reader = AlfrescoSolrHighlighter.solrContentStore.getReader(contentUrl);
        SolrInputDocument cachedDoc = null;
        if (reader.exists())
        {
            // try-with-resources statement closes all these InputStreams
            try (
                    InputStream contentInputStream = reader.getContentInputStream();
                    // Uncompresses the document
                    GZIPInputStream gzip = new GZIPInputStream(contentInputStream);
                )
            {
                cachedDoc = (SolrInputDocument) new JavaBinCodec(resolver).unmarshal(gzip);
            }
            catch (Exception e)
            {
                // Don't fail for this
                log.warn("Failed to get doc from store using URL: " + contentUrl, e);
                return null;
            }
        }
        return cachedDoc;
    }
    
    private Document getDocument(Document doc, SolrQueryRequest req) throws IOException
    {
        try
        {
            String id = getFieldValueString(doc, FIELD_SOLR4_ID);
            TenantAclIdDbId tenantAndDbId = AlfrescoSolrDataModel.decodeNodeDocumentId(id);
            SolrInputDocument sid = retrieveDocFromSolrContentStore(tenantAndDbId.tenant, tenantAndDbId.dbId);
            if(sid == null)
            {
                sid = new SolrInputDocument();
            	sid.addField(FIELD_SOLR4_ID, id);
            	sid.addField("_version_", 0);
            	return DocumentBuilder.toDocument(sid, req.getSchema());
            }
            else
            {
                return DocumentBuilder.toDocument(sid, req.getSchema());
            }
        }
        catch(StringIndexOutOfBoundsException e)
        {
            throw new IOException(e);
        }
    }
    
    private String getFieldValueString(Document doc, String fieldName)
    {
        IndexableField field = (IndexableField)doc.getField(fieldName);
        String value = null;
        if (field != null)
        {
            value = field.stringValue();
        }
        return value;
    }
}

/**
 * Orders Tokens in a window first by their startOffset ascending. endOffset is currently ignored. This is meant to work
 * around fickleness in the highlighter only. It can mess up token positions and should not be used for indexing or
 * querying.
 */
final class TokenOrderingFilter extends TokenFilter
{
    private final int windowSize;

    private final LinkedList<OrderedToken> queue = new LinkedList<>();

    private boolean done = false;

    private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);

    protected TokenOrderingFilter(TokenStream input, int windowSize)
    {
        super(input);
        this.windowSize = windowSize;
    }

    @Override
    public boolean incrementToken() throws IOException
    {
        while (!done && queue.size() < windowSize)
        {
            if (!input.incrementToken())
            {
                done = true;
                break;
            }

            // reverse iterating for better efficiency since we know the
            // list is already sorted, and most token start offsets will be too.
            ListIterator<OrderedToken> iter = queue.listIterator(queue.size());
            while (iter.hasPrevious())
            {
                if (offsetAtt.startOffset() >= iter.previous().startOffset)
                {
                    // insertion will be before what next() would return (what
                    // we just compared against), so move back one so the insertion
                    // will be after.
                    iter.next();
                    break;
                }
            }
            OrderedToken ot = new OrderedToken();
            ot.state = captureState();
            ot.startOffset = offsetAtt.startOffset();
            iter.add(ot);
        }

        if (queue.isEmpty())
        {
            return false;
        }
        else
        {
            restoreState(queue.removeFirst().state);
            return true;
        }
    }

    @Override
    public void reset() throws IOException
    {
        // this looks wrong: but its correct.
    }
}

// for TokenOrderingFilter, so it can easily sort by startOffset
class OrderedToken
{
    State state;

    int startOffset;
}

class TermOffsetsTokenStream
{

    TokenStream bufferedTokenStream = null;

    OffsetAttribute bufferedOffsetAtt;

    State bufferedToken;

    int bufferedStartOffset;

    int bufferedEndOffset;

    int startOffset;

    int endOffset;

    public TermOffsetsTokenStream(TokenStream tstream)
    {
        bufferedTokenStream = tstream;
        bufferedOffsetAtt = bufferedTokenStream.addAttribute(OffsetAttribute.class);
        startOffset = 0;
        bufferedToken = null;
    }

    public TokenStream getMultiValuedTokenStream(final int length)
    {
        endOffset = startOffset + length;
        return new MultiValuedStream(length);
    }

    final class MultiValuedStream extends TokenStream
    {
        private final int length;

        OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);

        MultiValuedStream(int length)
        {
            super(bufferedTokenStream.cloneAttributes());
            this.length = length;
        }

        @Override
        public boolean incrementToken() throws IOException
        {
            while (true)
            {
                if (bufferedToken == null)
                {
                    if (!bufferedTokenStream.incrementToken())
                        return false;
                    bufferedToken = bufferedTokenStream.captureState();
                    bufferedStartOffset = bufferedOffsetAtt.startOffset();
                    bufferedEndOffset = bufferedOffsetAtt.endOffset();
                }

                if (startOffset <= bufferedStartOffset && bufferedEndOffset <= endOffset)
                {
                    restoreState(bufferedToken);
                    bufferedToken = null;
                    offsetAtt.setOffset(offsetAtt.startOffset() - startOffset, offsetAtt.endOffset() - startOffset);
                    return true;
                }
                else if (bufferedEndOffset > endOffset)
                {
                    startOffset += length + 1;
                    return false;
                }
                bufferedToken = null;
            }
        }

    }
}
