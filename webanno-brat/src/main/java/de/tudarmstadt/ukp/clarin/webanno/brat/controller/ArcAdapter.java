/*******************************************************************************
 * Copyright 2012
 * Ubiquitous Knowledge Processing (UKP) Lab and FG Language Technology
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package de.tudarmstadt.ukp.clarin.webanno.brat.controller;

import static de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxCasUtil.getAddr;
import static de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxCasUtil.getFeatureFS;
import static de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxCasUtil.getLastSentenceAddressInDisplayWindow;
import static de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxCasUtil.isSameSentence;
import static de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxCasUtil.selectByAddr;
import static de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxCasUtil.selectSentenceAt;
import static de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxCasUtil.setFeature;
import static de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxCasUtil.setFeatureFS;
import static java.util.Arrays.asList;
import static org.apache.uima.fit.util.CasUtil.getType;
import static org.apache.uima.fit.util.CasUtil.selectCovered;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.fit.util.CasUtil;
import org.apache.uima.jcas.JCas;

import de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst;
import de.tudarmstadt.ukp.clarin.webanno.brat.annotation.BratAnnotatorModel;
import de.tudarmstadt.ukp.clarin.webanno.brat.display.model.Argument;
import de.tudarmstadt.ukp.clarin.webanno.brat.display.model.Comment;
import de.tudarmstadt.ukp.clarin.webanno.brat.display.model.Relation;
import de.tudarmstadt.ukp.clarin.webanno.brat.message.GetDocumentResponse;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.dependency.Dependency;

/**
 * A class that is used to create Brat Arc to CAS relations and vice-versa
 *
 * @author Seid Muhie Yimam
 *
 */
public class ArcAdapter
    implements TypeAdapter, AutomationTypeAdapter
{
    private final Log log = LogFactory.getLog(getClass());

    private final long typeId;

    /**
     * The UIMA type name.
     */
    private final String annotationTypeName;

    /**
     * The feature of an UIMA annotation containing the label to be used as a governor for arc
     * annotations
     */
    private final String sourceFeatureName;
    /**
     * The feature of an UIMA annotation containing the label to be used as a dependent for arc
     * annotations
     */

    private final String targetFeatureName;

    /*    *//**
     * The UIMA type name used as for origin/target span annotations, e.g. {@link POS} for
     * {@link Dependency}
     */
    /*
     * private final String arcSpanType;
     */
    /**
     * The feature of an UIMA annotation containing the label to be used as origin/target spans for
     * arc annotations
     */
    private final String attachFeatureName;

    /**
     * as Governor and Dependent of Dependency annotation type are based on Token, we need the UIMA
     * type for token
     */
    private final String attachType;

    private boolean deletable;

    /**
     * Allow multiple annotations of the same layer (only when the type value is different)
     */
    private boolean allowStacking;

    private boolean crossMultipleSentence;

    private AnnotationLayer layer;

    private Map<String, AnnotationFeature> features;

    public ArcAdapter(AnnotationLayer aLayer, long aTypeId, String aTypeName,
            String aTargetFeatureName, String aSourceFeatureName, /* String aArcSpanType, */
            String aAttacheFeatureName, String aAttachType, Collection<AnnotationFeature> aFeatures)
    {
        layer = aLayer;
        typeId = aTypeId;
        annotationTypeName = aTypeName;
        sourceFeatureName = aSourceFeatureName;
        targetFeatureName = aTargetFeatureName;
        // arcSpanType = aArcSpanType;
        attachFeatureName = aAttacheFeatureName;
        attachType = aAttachType;

        features = new LinkedHashMap<String, AnnotationFeature>();
        for (AnnotationFeature f : aFeatures) {
            features.put(f.getName(), f);
        }
    }

    /**
     * Add arc annotations from the CAS, which is controlled by the window size, to the brat
     * response {@link GetDocumentResponse}
     *
     * @param aJcas
     *            The JCAS object containing annotations
     * @param aResponse
     *            A brat response containing annotations in brat protocol
     * @param aBratAnnotatorModel
     *            Data model for brat annotations
     * @param aColoringStrategy
     *            the coloring strategy to render this layer
     */
    @Override
    public void render(JCas aJcas, List<AnnotationFeature> aFeatures,
            GetDocumentResponse aResponse, BratAnnotatorModel aBratAnnotatorModel,
            ColoringStrategy aColoringStrategy)
    {
        // The first sentence address in the display window!
        Sentence firstSentence = selectSentenceAt(aJcas,
                aBratAnnotatorModel.getSentenceBeginOffset(),
                aBratAnnotatorModel.getSentenceEndOffset());

        int lastAddressInPage = getLastSentenceAddressInDisplayWindow(aJcas,
                getAddr(firstSentence), aBratAnnotatorModel.getPreferences().getWindowSize());

        // the last sentence address in the display window
        Sentence lastSentenceInPage = (Sentence) selectByAddr(aJcas, FeatureStructure.class,
                lastAddressInPage);

        Type type = getType(aJcas.getCas(), annotationTypeName);
        Feature dependentFeature = type.getFeatureByBaseName(targetFeatureName);
        Feature governorFeature = type.getFeatureByBaseName(sourceFeatureName);

        Type spanType = getType(aJcas.getCas(), attachType);
        Feature arcSpanFeature = spanType.getFeatureByBaseName(attachFeatureName);

        FeatureStructure dependentFs;
        FeatureStructure governorFs;

        Map<Integer, List<Integer>> relationLinks = getRelationLinks(aJcas, firstSentence,
                lastSentenceInPage, type, dependentFeature, governorFeature, arcSpanFeature);

        for (AnnotationFS fs : selectCovered(aJcas.getCas(), type, firstSentence.getBegin(),
                lastSentenceInPage.getEnd())) {
            if (attachFeatureName != null) {
                dependentFs = fs.getFeatureValue(dependentFeature).getFeatureValue(arcSpanFeature);
                governorFs = fs.getFeatureValue(governorFeature).getFeatureValue(arcSpanFeature);
            }
            else {
                dependentFs = fs.getFeatureValue(dependentFeature);
                governorFs = fs.getFeatureValue(governorFeature);
            }

            if (dependentFs == null || governorFs == null) {
                log.warn("Relation [" + layer.getName() + "] with id [" + getAddr(fs)
                        + "] has loose ends - cannot render.");
                continue;
            }

            List<Argument> argumentList = getArgument(governorFs, dependentFs);

            String bratLabelText = TypeUtil.getBratLabelText(this, fs, aFeatures);
            String bratTypeName = TypeUtil.getBratTypeName(this);
            String color = aColoringStrategy.getColor(fs, bratLabelText);

            aResponse.addRelation(new Relation(getAddr(fs), bratTypeName, argumentList,
                    bratLabelText, color));
            if (relationLinks.keySet().contains(getAddr(fs))) {
                StringBuffer cm = new StringBuffer();
                boolean f = true;
                for (int id : relationLinks.get(getAddr(fs))) {
                    if (f) {
                        cm.append(selectByAddr(aJcas, id).getCoveredText());
                        f = false;
                    }
                    else {
                        cm.append("→" + selectByAddr(aJcas, id).getCoveredText());
                    }
                }

                aResponse.addComments(new Comment(getAddr(fs), "Yield of relation:", cm.toString()));
            }
        }
    }

    /**
     * Get relation links to display
     * @param aJcas
     * @param firstSentence
     * @param lastSentenceInPage
     * @param type
     * @param dependentFeature
     * @param governorFeature
     * @param arcSpanFeature
     * @return
     */
    private Map<Integer, List<Integer>> getRelationLinks(JCas aJcas, Sentence firstSentence,
            Sentence lastSentenceInPage, Type type, Feature dependentFeature,
            Feature governorFeature, Feature arcSpanFeature)
    {
        FeatureStructure dependentFs;
        FeatureStructure governorFs;
        Map<Integer, List<Integer>> relations = new HashMap<>();
        for (AnnotationFS fs : selectCovered(aJcas.getCas(), type, firstSentence.getBegin(),
                lastSentenceInPage.getEnd())) {
            if (attachFeatureName != null) {
                dependentFs = fs.getFeatureValue(dependentFeature).getFeatureValue(arcSpanFeature);
                governorFs = fs.getFeatureValue(governorFeature).getFeatureValue(arcSpanFeature);
            }
            else {
                dependentFs = fs.getFeatureValue(dependentFeature);
                governorFs = fs.getFeatureValue(governorFeature);
            }
            if (dependentFs == null || governorFs == null) {
                log.warn("Relation [" + layer.getName() + "] with id [" + getAddr(fs)
                        + "] has loose ends - cannot render.");
                continue;
            }
            LinkedList<Integer> links = new LinkedList<>();

            links.add(getAddr(dependentFs));

            // check for the existences of links
            for (AnnotationFS oFS : selectCovered(aJcas.getCas(), type, firstSentence.getBegin(),
                    lastSentenceInPage.getEnd())) {
                FeatureStructure oDependentFs;
                FeatureStructure oGovernorFs;
                if (attachFeatureName != null) {
                    oDependentFs = oFS.getFeatureValue(dependentFeature).getFeatureValue(
                            arcSpanFeature);
                    oGovernorFs = oFS.getFeatureValue(governorFeature).getFeatureValue(
                            arcSpanFeature);
                }
                else {
                    oDependentFs = oFS.getFeatureValue(dependentFeature);
                    oGovernorFs = oFS.getFeatureValue(governorFeature);
                }

                if (oGovernorFs == null || oDependentFs == null) {
                    log.warn("Relation [" + layer.getName() + "] with id [" + getAddr(fs)
                            + "] has no spans to attach to - cannot render.");
                    continue;
                }
                
                
                if (((AnnotationFS) governorFs).getBegin() == ((AnnotationFS) oGovernorFs)
                        .getBegin()
                        && ((AnnotationFS) dependentFs).getBegin() == ((AnnotationFS) oDependentFs)
                                .getBegin()) {
                    continue;
                }
                else if (links.contains(getAddr(oGovernorFs))) {
                    links.add(getAddr(oDependentFs));
                }
            }
            links.addFirst(getAddr(governorFs));
            relations.put(getAddr(fs), links);
        }
        return relations;
    }

    /**
     * Update the CAS with new/modification of arc annotations from brat
     *
     * @param aOriginFs
     *            the origin FS.
     * @param aTargetFs
     *            the target FS.
     * @param aJCas
     *            the JCas.
     * @param aBratAnnotatorModel
     *            the annotator model.
     * @param aFeature
     *            the feature.
     * @param aLabelValue
     *            the value of the annotation for the arc
     * @return the ID.
     * @throws BratAnnotationException
     *             if the annotation could not be created/updated.
     */
    public AnnotationFS add(AnnotationFS aOriginFs, AnnotationFS aTargetFs, JCas aJCas,
            BratAnnotatorModel aBratAnnotatorModel, AnnotationFeature aFeature, Object aLabelValue)
        throws BratAnnotationException
    {
        Sentence sentence = selectSentenceAt(aJCas, aBratAnnotatorModel.getSentenceBeginOffset(),
                aBratAnnotatorModel.getSentenceEndOffset());

        int beginOffset = sentence.getBegin();
        int endOffset = selectByAddr(
                aJCas,
                Sentence.class,
                getLastSentenceAddressInDisplayWindow(aJCas, getAddr(sentence), aBratAnnotatorModel
                        .getPreferences().getWindowSize())).getEnd();
        if (crossMultipleSentence
                || isSameSentence(aJCas, aOriginFs.getBegin(), aTargetFs.getEnd())) {
            return updateCas(aJCas, beginOffset, endOffset, aOriginFs, aTargetFs, aLabelValue,
                    aFeature);
        }
        else {
            throw new ArcCrossedMultipleSentenceException(
                    "Arc Annotation shouldn't cross sentence boundary");
        }
    }

    /**
     * A Helper method to {@link #addToCas(String, BratAnnotatorUIData)}
     */
    private AnnotationFS updateCas(JCas aJCas, int aBegin, int aEnd, AnnotationFS aOriginFs,
            AnnotationFS aTargetFs, Object aValue, AnnotationFeature aFeature)
    {
        Type type = getType(aJCas.getCas(), annotationTypeName);
        Feature dependentFeature = type.getFeatureByBaseName(targetFeatureName);
        Feature governorFeature = type.getFeatureByBaseName(sourceFeatureName);

        Type spanType = getType(aJCas.getCas(), attachType);

        AnnotationFS dependentFs = null;
        AnnotationFS governorFs = null;

        for (AnnotationFS fs : selectCovered(aJCas.getCas(), type, aBegin, aEnd)) {

            if (attachFeatureName != null) {
                Feature arcSpanFeature = spanType.getFeatureByBaseName(attachFeatureName);
                dependentFs = (AnnotationFS) fs.getFeatureValue(dependentFeature).getFeatureValue(
                        arcSpanFeature);
                governorFs = (AnnotationFS) fs.getFeatureValue(governorFeature).getFeatureValue(
                        arcSpanFeature);
            }
            else {
                dependentFs = (AnnotationFS) fs.getFeatureValue(dependentFeature);
                governorFs = (AnnotationFS) fs.getFeatureValue(governorFeature);
            }

            if (dependentFs == null || governorFs == null) {
                log.warn("Relation [" + layer.getName() + "] with id [" + getAddr(fs)
                        + "] has loose ends - ignoring during while checking for duplicates.");
                continue;
            }

            if (isDuplicate((AnnotationFS) governorFs, aOriginFs, (AnnotationFS) dependentFs,
                    aTargetFs) && (aValue == null || !aValue.equals(WebAnnoConst.ROOT))) {

                if (!allowStacking) {
                    setFeature(fs, aFeature, aValue);
                    return fs;
                }
            }
        }

        // It is new ARC annotation, create it
        dependentFs = aTargetFs;
        governorFs = aOriginFs;

        // for POS annotation, since custom span layers do not have attach feature
        if (attachFeatureName != null) {
            dependentFs = selectCovered(aJCas.getCas(), spanType, dependentFs.getBegin(),
                    dependentFs.getEnd()).get(0);
            governorFs = selectCovered(aJCas.getCas(), spanType, governorFs.getBegin(),
                    governorFs.getEnd()).get(0);
        }

        // if span A has (start,end)= (20, 26) and B has (start,end)= (30, 36)
        // arc drawn from A to B, dependency will have (start, end) = (20, 36)
        // arc drawn from B to A, still dependency will have (start, end) = (20, 36)
        AnnotationFS newAnnotation;
        if (dependentFs.getEnd() <= governorFs.getEnd()) {
            newAnnotation = aJCas.getCas().createAnnotation(type, dependentFs.getBegin(),
                    governorFs.getEnd());
        }
        else {
            newAnnotation = aJCas.getCas().createAnnotation(type, governorFs.getBegin(),
                    dependentFs.getEnd());
        }

        // If origin and target spans are multiple tokens, dependentFS.getBegin will be the
        // the begin position of the first token and dependentFS.getEnd will be the End
        // position of the last token.
        newAnnotation.setFeatureValue(dependentFeature, dependentFs);
        newAnnotation.setFeatureValue(governorFeature, governorFs);
        setFeature(newAnnotation, aFeature, aValue);

        // BEGIN HACK - ISSUE 953 - Special treatment for ROOT in DKPro Core dependency layer
        // If the dependency type is set to "ROOT" the create a loop arc
        if (aFeature != null) {
            if (Dependency.class.getName().equals(layer.getName())
                    && "DependencyType".equals(aFeature.getName()) && "ROOT".equals(aValue)) {
                FeatureStructure source = getFeatureFS(newAnnotation, sourceFeatureName);
                setFeatureFS(newAnnotation, targetFeatureName, source);
            }
        }
        // END HACK - ISSUE 953 - Special treatment for ROOT in DKPro Core dependency layer

        aJCas.addFsToIndexes(newAnnotation);
        return newAnnotation;
    }

    @Override
    public void delete(JCas aJCas, int aAddress)
    {
        FeatureStructure fs = selectByAddr(aJCas, FeatureStructure.class, aAddress);
        aJCas.removeFsFromIndexes(fs);
    }

    @Override
    public void deleteBySpan(JCas aJCas, AnnotationFS afs, int aBegin, int aEnd)
    {
        Type type = getType(aJCas.getCas(), annotationTypeName);
        Feature targetFeature = type.getFeatureByBaseName(targetFeatureName);
        Feature sourceFeature = type.getFeatureByBaseName(sourceFeatureName);

        Type spanType = getType(aJCas.getCas(), attachType);
        Feature arcSpanFeature = spanType.getFeatureByBaseName(attachFeatureName);

        Set<AnnotationFS> fsToDelete = new HashSet<AnnotationFS>();

        for (AnnotationFS fs : selectCovered(aJCas.getCas(), type, aBegin, aEnd)) {

            if (attachFeatureName != null) {
                FeatureStructure dependentFs = fs.getFeatureValue(targetFeature).getFeatureValue(
                        arcSpanFeature);
                if (getAddr(afs) == getAddr(dependentFs)) {
                    fsToDelete.add(fs);
                }
                FeatureStructure governorFs = fs.getFeatureValue(sourceFeature).getFeatureValue(
                        arcSpanFeature);
                if (getAddr(afs) == getAddr(governorFs)) {
                    fsToDelete.add(fs);
                }
            }
            else {
                FeatureStructure dependentFs = fs.getFeatureValue(targetFeature);
                if (getAddr(afs) == getAddr(dependentFs)) {
                    fsToDelete.add(fs);
                }
                FeatureStructure governorFs = fs.getFeatureValue(sourceFeature);
                if (getAddr(afs) == getAddr(governorFs)) {
                    fsToDelete.add(fs);
                }
            }
        }
        for (AnnotationFS fs : fsToDelete) {
            aJCas.removeFsFromIndexes(fs);
        }
    }

    /**
     * Argument lists for the arc annotation
     *
     * @return
     */
    private List<Argument> getArgument(FeatureStructure aGovernorFs, FeatureStructure aDependentFs)
    {
        return asList(new Argument("Arg1", getAddr(aGovernorFs)), new Argument("Arg2",
                getAddr(aDependentFs)));
    }

    private boolean isDuplicate(AnnotationFS aAnnotationFSOldOrigin,
            AnnotationFS aAnnotationFSNewOrigin, AnnotationFS aAnnotationFSOldTarget,
            AnnotationFS aAnnotationFSNewTarget)
    {
        if (aAnnotationFSOldOrigin.getBegin() == aAnnotationFSNewOrigin.getBegin()
                && aAnnotationFSOldOrigin.getEnd() == aAnnotationFSNewOrigin.getEnd()
                && aAnnotationFSOldTarget.getBegin() == aAnnotationFSNewTarget.getBegin()
                && aAnnotationFSOldTarget.getEnd() == aAnnotationFSNewTarget.getEnd()) {
            return true;
        }
        else {
            return false;
        }
    }

    @Override
    public long getTypeId()
    {
        return typeId;
    }

    @Override
    public Type getAnnotationType(CAS cas)
    {
        return CasUtil.getType(cas, annotationTypeName);
    }

    @Override
    public String getAnnotationTypeName()
    {
        return annotationTypeName;
    }

    @Override
    public boolean isDeletable()
    {
        return deletable;
    }

    @Override
    public String getAttachFeatureName()
    {
        return attachFeatureName;
    }

    @Override
    public List<String> getAnnotation(JCas aJcas, AnnotationFeature aFeature, int begin, int end)
    {
        return new ArrayList<String>();
    }

    @Override
    public void delete(JCas aJCas, AnnotationFeature aFeature, int aBegin, int aEnd, Object aValue)
    {
        // TODO Auto-generated method stub
    }

    public boolean isCrossMultipleSentence()
    {
        return crossMultipleSentence;
    }

    public void setCrossMultipleSentence(boolean crossMultipleSentence)
    {
        this.crossMultipleSentence = crossMultipleSentence;
    }

    public boolean isAllowStacking()
    {
        return allowStacking;
    }

    public void setAllowStacking(boolean allowStacking)
    {
        this.allowStacking = allowStacking;
    }

    // FIXME this is the version that treats each tag as a separate type in brat - should be removed
    // public static String getBratTypeName(TypeAdapter aAdapter, AnnotationFS aFs,
    // List<AnnotationFeature> aFeatures)
    // {
    // String annotations = "";
    // for (AnnotationFeature feature : aFeatures) {
    // if (!(feature.isEnabled() || feature.isEnabled())) {
    // continue;
    // }
    // Feature labelFeature = aFs.getType().getFeatureByBaseName(feature.getName());
    // if (annotations.equals("")) {
    // annotations = aAdapter.getTypeId()
    // + "_"
    // + (aFs.getFeatureValueAsString(labelFeature) == null ? " " : aFs
    // .getFeatureValueAsString(labelFeature));
    // }
    // else {
    // annotations = annotations
    // + " | "
    // + (aFs.getFeatureValueAsString(labelFeature) == null ? " " : aFs
    // .getFeatureValueAsString(labelFeature));
    // }
    // }
    // return annotations;
    // }

    @Override
    public String getAttachTypeName()
    {
        return attachType;
    }

    @Override
    public void updateFeature(JCas aJcas, AnnotationFeature aFeature, int aAddress, Object aValue)
    {
        FeatureStructure fs = selectByAddr(aJcas, FeatureStructure.class, aAddress);
        setFeature(fs, aFeature, aValue);

        // BEGIN HACK - ISSUE 953 - Special treatment for ROOT in DKPro Core dependency layer
        // If the dependency type is set to "ROOT" the create a loop arc
        if (Dependency.class.getName().equals(layer.getName())
                && "DependencyType".equals(aFeature.getName()) && "ROOT".equals(aValue)) {
            FeatureStructure source = getFeatureFS(fs, sourceFeatureName);
            setFeatureFS(fs, targetFeatureName, source);
        }
        // END HACK - ISSUE 953 - Special treatment for ROOT in DKPro Core dependency layer
    }

    @Override
    public AnnotationLayer getLayer()
    {
        return layer;
    }

    @Override
    public Collection<AnnotationFeature> listFeatures()
    {
        return features.values();
    }

    public String getSourceFeatureName()
    {
        return sourceFeatureName;
    }

    public String getTargetFeatureName()
    {
        return targetFeatureName;
    }
}
