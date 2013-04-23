/*******************************************************************************
 * Copyright 2012
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package de.tudarmstadt.ukp.clarin.webanno.brat.page.curation.component.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.annotation.Resource;
import javax.persistence.NoResultException;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.uima.UIMAException;
import org.apache.uima.jcas.JCas;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.http.converter.json.MappingJacksonHttpMessageConverter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationService;
import de.tudarmstadt.ukp.clarin.webanno.api.RepositoryService;
import de.tudarmstadt.ukp.clarin.webanno.brat.ApplicationUtils;
import de.tudarmstadt.ukp.clarin.webanno.brat.annotation.BratAnnotatorModel;
import de.tudarmstadt.ukp.clarin.webanno.brat.annotation.BratAnnotatorUIData;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.AnnotationTypeConstant;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.ArcAdapter;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxCasUtil;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxConfiguration;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.CasToBratJson;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.SpanAdapter;
import de.tudarmstadt.ukp.clarin.webanno.brat.display.model.Stored;
import de.tudarmstadt.ukp.clarin.webanno.brat.message.CreateArcResponse;
import de.tudarmstadt.ukp.clarin.webanno.brat.message.CreateSpanResponse;
import de.tudarmstadt.ukp.clarin.webanno.brat.message.DeleteArcResponse;
import de.tudarmstadt.ukp.clarin.webanno.brat.message.DeleteSpanResponse;
import de.tudarmstadt.ukp.clarin.webanno.brat.message.GetCollectionInformationResponse;
import de.tudarmstadt.ukp.clarin.webanno.brat.message.GetDocumentResponse;
import de.tudarmstadt.ukp.clarin.webanno.brat.message.GetDocumentTimestampResponse;
import de.tudarmstadt.ukp.clarin.webanno.brat.message.ImportDocumentResponse;
import de.tudarmstadt.ukp.clarin.webanno.brat.message.LoadConfResponse;
import de.tudarmstadt.ukp.clarin.webanno.brat.message.ReverseArcResponse;
import de.tudarmstadt.ukp.clarin.webanno.brat.message.StoreSvgResponse;
import de.tudarmstadt.ukp.clarin.webanno.brat.message.WhoamiResponse;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocumentStateTransition;
import de.tudarmstadt.ukp.clarin.webanno.model.Tag;
import de.tudarmstadt.ukp.clarin.webanno.model.TagSet;
import de.tudarmstadt.ukp.clarin.webanno.model.User;
import de.tudarmstadt.ukp.dkpro.core.api.coref.type.CoreferenceChain;
import de.tudarmstadt.ukp.dkpro.core.api.coref.type.CoreferenceLink;
import de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS;
import de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.dependency.Dependency;

/**
 * an Ajax Controller for the BRAT Front End. Most of the actions such as getCollectionInformation ,
 * getDocument, createArc, CreateSpan, deleteSpan, DeleteArc,... are implemented. Besides returning
 * the JSON response to the brat FrontEnd, This controller also manipulates creation of annotation
 * Documents
 *
 * @author Seid Muhie Yimam
 * @author Richard Eckart de Castilho
 * @author Andreas Straninger
 *
 */
@Controller
public class BratAjaxCasCurationController
{

    public static final String MIME_TYPE_XML = "application/xml";
    public static final String PRODUCES_JSON = "application/json";
    public static final String PRODUCES_XML = "application/xml";
    public static final String CONSUMES_URLENCODED = "application/x-www-form-urlencoded";

    @Resource(name = "documentRepository")
    private RepositoryService repository;

    @Resource(name = "annotationService")
    private AnnotationService annotationService;

    @Resource(name = "jsonConverter")
    private MappingJacksonHttpMessageConverter jsonConverter;

    private Log LOG = LogFactory.getLog(getClass());

    public BratAjaxCasCurationController()
    {

    }

    public BratAjaxCasCurationController(MappingJacksonHttpMessageConverter aJsonConverter,
            RepositoryService aRepository, AnnotationService aAnnotationService)
    {
        this.annotationService = aAnnotationService;
        this.repository = aRepository;
        this.jsonConverter = aJsonConverter;
    }

    /**
     * This Method, a generic Ajax call serves the purpose of returning expected export file types.
     * This only will be called for Larger annotation documents
     *
     * @param aParameters
     * @return export file type once in a while!!!
     */
    public StoreSvgResponse ajaxCall(MultiValueMap<String, String> aParameters)
    {
        LOG.info("AJAX-RPC: storeSVG");
        StoreSvgResponse storeSvgResponse = new StoreSvgResponse();
        ArrayList<Stored> storedList = new ArrayList<Stored>();
        Stored stored = new Stored();

        stored.setName("TCF");
        stored.setSuffix("TCF");
        storedList.add(stored);

        storeSvgResponse.setStored(storedList);

        LOG.info("Done.");
        return storeSvgResponse;
    }

    /**
     * a protocol which returns the logged in user
     *
     * @param aParameters
     * @return
     */
    public WhoamiResponse whoami()
    {
        LOG.info("AJAX-RPC: whoami");

        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        LOG.info("Done.");
        return new WhoamiResponse(username);
    }

    /**
     * a protocol to retunr the expected file type for annotation document exporting . Currently, it
     * returns only tcf file type where in the future svg and pdf types are to be supported
     *
     * @return
     */
    public StoreSvgResponse storeSVG()
    {
        LOG.info("AJAX-RPC: storeSVG");
        StoreSvgResponse storeSvgResponse = new StoreSvgResponse();
        ArrayList<Stored> storedList = new ArrayList<Stored>();
        Stored stored = new Stored();

        stored.setName("TCF");
        stored.setSuffix("TCF");
        storedList.add(stored);

        storeSvgResponse.setStored(storedList);

        LOG.info("Done.");
        return storeSvgResponse;
    }

    public ImportDocumentResponse importDocument(String aCollection, String aDocId, String aText,
            String aTitle, HttpServletRequest aRequest)
    {
        LOG.info("AJAX-RPC: importDocument");
        ImportDocumentResponse importDocument = new ImportDocumentResponse();
        importDocument.setDocument(aDocId);
        LOG.info("Done.");
        return importDocument;
    }

    /**
     * some BRAT UI global configurations such as {@code textBackgrounds}
     *
     * @param aParameters
     * @return
     */

    public LoadConfResponse loadConf()
    {
        LOG.info("AJAX-RPC: loadConf");

        LOG.info("Done.");
        return new LoadConfResponse();
    }

    /**
     * This the the method that send JSON response about annotation project information which
     * includes List {@link Tag}s and {@link TagSet}s It includes information about span types
     * {@link POS}, {@link NamedEntity}, and {@link CoreferenceLink#getReferenceType()} and relation
     * types such as {@link Dependency}, {@link CoreferenceChain}
     *
     * @see <a href="http://brat.nlplab.org/index.html">Brat</a>
     * @param aCollection
     * @param aRequest
     * @return
     * @throws UIMAException
     * @throws IOException
     */

    public GetCollectionInformationResponse getCollectionInformation(String aCollection,
            HashSet<TagSet> aAnnotationLayers)

    {
        LOG.info("AJAX-RPC: getCollectionInformation");

        LOG.info("Collection: " + aCollection);

        Project project = new Project();
        if (!aCollection.equals("/")) {
            project = repository.getProject(aCollection.replace("/", ""));
        }
        // Get list of TagSets configured in BRAT UI

        // Get The tags of the tagset
        // merge all of them
        List<Tag> tagLists = new ArrayList<Tag>();

        List<String> tagSetNames = new ArrayList<String>();
        for (TagSet tagSet : aAnnotationLayers) {
            List<Tag> tag = annotationService.listTags(tagSet);
            tagLists.addAll(tag);
            tagSetNames.add(tagSet.getType().getName());
        }

        GetCollectionInformationResponse info = new GetCollectionInformationResponse();
        BratAjaxConfiguration configuration = new BratAjaxConfiguration();
        info.setEntityTypes(configuration.configureVisualizationAndAnnotation(tagLists));

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = repository.getUser(username);
        if (aCollection.equals("/")) {
            for (Project projects : repository.listProjects()) {
                if (repository.listProjectUserNames(projects).contains(username)
                        && ApplicationUtils.isMember(projects, repository, user)) {
                    info.addCollection(projects.getName());
                }
            }
        }
        else {

            project = repository.getProject(aCollection.replace("/", ""));

            for (SourceDocument document : repository.listSourceDocuments(project)) {
                info.addDocument(document.getName());
            }
            info.addCollection("../");
        }
        // The norm_search_dialog seems required in the annotation page.
        // This will be removed when our own open dialog is implemented
        info.setSearchConfig(new ArrayList<String[]>());

        LOG.info("Done.");
        return info;
    }

    public GetDocumentTimestampResponse getDocumentTimestamp(String aCollection, String aDocument)
    {
        LOG.info("AJAX-RPC: getDocumentTimestamp");

        LOG.info("Collection: " + aCollection);
        LOG.info("Document: " + aDocument);

        LOG.info("Done.");
        return new GetDocumentTimestampResponse();
    }

    /**
     * Returns the JSON representation of the document for brat visualizer
     *
     * @throws ClassNotFoundException
     */

    public GetDocumentResponse getDocument(BratAnnotatorModel aBratAnnotatorModel,
            BratAnnotatorUIData aUIData)
        throws UIMAException, IOException, ClassNotFoundException
    {
        LOG.info("AJAX-RPC: getDocument");

        LOG.info("Collection: " + aBratAnnotatorModel.getDocument().getName());

        GetDocumentResponse response = new GetDocumentResponse();

        addBratResponses(response, aBratAnnotatorModel, aUIData);

        return response;
    }

    /**
     * Creates a new span annotation, saves the annotation to a file system and return the newly
     * constructed JSON document as a response to BRAT visualizer.
     */

    public CreateSpanResponse createSpan(BratAnnotatorModel aBratAnnotatorModel,
            BratAnnotatorUIData aUIData)
        throws JsonParseException, JsonMappingException, IOException, UIMAException
    {

        String annotationType = BratAjaxCasUtil.getAnnotationType(aUIData.getType());
        String type = BratAjaxCasUtil.getType(aUIData.getType());

        if (annotationType.equals(AnnotationTypeConstant.NAMEDENTITY_PREFIX)) {
            SpanAdapter.getNamedEntityAdapter().addToCas(type, aUIData);
        }
        else if (annotationType.equals(AnnotationTypeConstant.POS_PREFIX)) {
            SpanAdapter.getPosAdapter().addToCas(type, aUIData);
        }
        else if (annotationType.equals(AnnotationTypeConstant.COREFERENCE_PREFIX)) {
            BratAjaxCasUtil.updateCoreferenceType(aBratAnnotatorModel, type, aUIData);
        }

        GetDocumentResponse response = new GetDocumentResponse();

        addBratResponses(response, aBratAnnotatorModel, aUIData);

        CreateSpanResponse createSpanResponse = new CreateSpanResponse();

        createSpanResponse.setAnnotations(response);

        repository.createCurationDocumentContent(aUIData.getjCas(),
        		aBratAnnotatorModel.getDocument(), aBratAnnotatorModel.getUser());
        
        return createSpanResponse;

    }

    /**
     * Creates an arc annotation between two annotated spans
     */

    public CreateArcResponse createArc(BratAnnotatorModel aBratAnnotatorModel,
            BratAnnotatorUIData aUIData)
        throws UIMAException, IOException
    {

        String annotationType = BratAjaxCasUtil.getAnnotationType(aUIData.getType());
        String type = BratAjaxCasUtil.getType(aUIData.getType());

        if (annotationType.equals(AnnotationTypeConstant.POS_PREFIX)) {
            ArcAdapter.getDependencyAdapter().addToCas(type, aUIData, aBratAnnotatorModel, false);
        }
        else if (annotationType.equals(AnnotationTypeConstant.COREFERENCE_PREFIX)) {
            BratAjaxCasUtil.updateCoreferenceRelation(aBratAnnotatorModel, type, aUIData);
        }
        GetDocumentResponse response = new GetDocumentResponse();

        addBratResponses(response, aBratAnnotatorModel, aUIData);

        CreateArcResponse createArcResponse = new CreateArcResponse();

        createArcResponse.setAnnotations(response);

        repository.createCurationDocumentContent(aUIData.getjCas(),
        		aBratAnnotatorModel.getDocument(), aBratAnnotatorModel.getUser());

        return createArcResponse;

    }

    /**
     * reverse the direction of arc annotations, in this case, Dependency parsing
     */

    public ReverseArcResponse reverseArc(BratAnnotatorModel aBratAnnotatorModel,
            BratAnnotatorUIData aUIData)
        throws UIMAException, IOException
    {

        String annotationType = BratAjaxCasUtil.getAnnotationType(aUIData.getType());
        String type = BratAjaxCasUtil.getType(aUIData.getType());

        if (annotationType.equals(AnnotationTypeConstant.POS_PREFIX)) {
            ArcAdapter.getDependencyAdapter().deleteFromCas(aUIData, aBratAnnotatorModel);
            // Reverse directions
            String origin = aUIData.getOrigin();// swap variable
            aUIData.setOrigin(aUIData.getTarget());
            aUIData.setTarget(origin);
            ArcAdapter.getDependencyAdapter().addToCas(type, aUIData, aBratAnnotatorModel, false);
        }

        GetDocumentResponse response = new GetDocumentResponse();
        addBratResponses(response, aBratAnnotatorModel, aUIData);

        ReverseArcResponse createArcResponse = new ReverseArcResponse();

        createArcResponse.setAnnotations(response);

        repository.createCurationDocumentContent(aUIData.getjCas(),
        		aBratAnnotatorModel.getDocument(), aBratAnnotatorModel.getUser());

        return createArcResponse;

    }

    /**
     * deletes a span annotation, except POS annotation
     */
    public DeleteSpanResponse deleteSpan(BratAnnotatorModel aBratAnnotatorModel, String aId,
            BratAnnotatorUIData aUIData)
        throws JsonParseException, JsonMappingException, IOException, UIMAException
    {

        String annotationType = BratAjaxCasUtil.getAnnotationType(aUIData.getType());
        String type = BratAjaxCasUtil.getType(aUIData.getType());


        if (annotationType.equals(AnnotationTypeConstant.NAMEDENTITY_PREFIX)) {
            SpanAdapter.getNamedEntityAdapter().deleteFromCas(aUIData.getjCas(), aId);
        }
        else if (annotationType.equals(AnnotationTypeConstant.COREFERENCE_PREFIX)) {
            BratAjaxCasUtil.deleteCoreferenceType(aUIData.getjCas(), aId, type,
                    aUIData.getAnnotationOffsetStart(), aUIData.getAnnotationOffsetEnd());
        }

        GetDocumentResponse response = new GetDocumentResponse();
        addBratResponses(response, aBratAnnotatorModel, aUIData);
        DeleteSpanResponse deleteSpanResponse = new DeleteSpanResponse();

        deleteSpanResponse.setAnnotations(response);

        repository.createCurationDocumentContent(aUIData.getjCas(),
        		aBratAnnotatorModel.getDocument(), aBratAnnotatorModel.getUser());

        return deleteSpanResponse;

    }

    /**
     * deletes an arc between the origin and target spans
     */
    public DeleteArcResponse deleteArc(BratAnnotatorModel aBratAnnotatorModel,
            BratAnnotatorUIData aUIData)
        throws UIMAException, IOException
    {

        String annotationType = BratAjaxCasUtil.getAnnotationType(aUIData.getType());
        String type = BratAjaxCasUtil.getType(aUIData.getType());

        if (annotationType.equals(AnnotationTypeConstant.POS_PREFIX)) {
            ArcAdapter.getDependencyAdapter().deleteFromCas(aUIData, aBratAnnotatorModel);
        }
        else if (annotationType.equals(AnnotationTypeConstant.COREFERENCE_PREFIX)) {
            BratAjaxCasUtil.deleteCoreference(aBratAnnotatorModel, type, aUIData);
        }

        GetDocumentResponse response = new GetDocumentResponse();
        addBratResponses(response, aBratAnnotatorModel, aUIData);

        DeleteArcResponse deleteArcResponse = new DeleteArcResponse();

        deleteArcResponse.setAnnotations(response);

        repository.createCurationDocumentContent(aUIData.getjCas(),
        		aBratAnnotatorModel.getDocument(), aBratAnnotatorModel.getUser());

        return deleteArcResponse;

    }

    /**
     * wrap JSON responses to BRAT visualizer
     */
    private void addBratResponses(GetDocumentResponse aResponse,
            BratAnnotatorModel aBratAnnotatorModel, BratAnnotatorUIData aUIData)
    {
        List<String> annotationLayers = new ArrayList<String>();
        for (TagSet tag : aBratAnnotatorModel.getAnnotationLayers()) {
            annotationLayers.add(tag.getType().getName());
        }

        if (aBratAnnotatorModel.isScrollPage() && !aUIData.isGetDocument()) {
            aBratAnnotatorModel.setSentenceAddress(BratAjaxCasUtil.getSentenceBeginAddress(
                    aUIData.getjCas(), aBratAnnotatorModel.getSentenceAddress(),
                    aUIData.getAnnotationOffsetStart(), aBratAnnotatorModel.getProject(),
                    aBratAnnotatorModel.getDocument(), aBratAnnotatorModel.getWindowSize()));
        }
        CasToBratJson casToBratJson = new CasToBratJson();

        casToBratJson.addTokenToResponse(aUIData.getjCas(), aResponse, aBratAnnotatorModel);
        casToBratJson.addSentenceToResponse(aUIData.getjCas(), aResponse, aBratAnnotatorModel);

        if (annotationLayers.contains(AnnotationTypeConstant.POS)) {
            SpanAdapter.getPosAdapter().addToBrat(aUIData.getjCas(), aResponse,
                    aBratAnnotatorModel);
        }

        if (annotationLayers.contains(AnnotationTypeConstant.COREFRELTYPE)) {
            casToBratJson.addCorefTypeToResponse(aUIData.getjCas(), aResponse, aBratAnnotatorModel);
        }
        if (aBratAnnotatorModel.isDisplayLemmaSelected()) {
            SpanAdapter.getLemmaAdapter().addToBrat(aUIData.getjCas(), aResponse,
                    aBratAnnotatorModel);
        }
        if (annotationLayers.contains(AnnotationTypeConstant.NAMEDENTITY)) {
            SpanAdapter.getNamedEntityAdapter().addToBrat(aUIData.getjCas(),
            		aResponse, aBratAnnotatorModel);
        }
        if (annotationLayers.contains(AnnotationTypeConstant.DEPENDENCY)
                && annotationLayers.contains(AnnotationTypeConstant.POS)) {
            ArcAdapter.getDependencyAdapter().addToBrat(aUIData.getjCas(), aResponse, aBratAnnotatorModel);
        }
        if (annotationLayers.contains(AnnotationTypeConstant.COREFERENCE)
                && annotationLayers.contains(AnnotationTypeConstant.COREFRELTYPE)) {
            casToBratJson.addCoreferenceToResponse(aUIData.getjCas(), aResponse,
                    aBratAnnotatorModel);
        }
    }

}