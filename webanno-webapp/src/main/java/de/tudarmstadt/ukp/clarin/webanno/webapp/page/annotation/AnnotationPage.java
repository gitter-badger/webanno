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
package de.tudarmstadt.ukp.clarin.webanno.webapp.page.annotation;

import static de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxCasUtil.getAddr;
import static de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxCasUtil.getLastSentenceAddressInDisplayWindow;
import static de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxCasUtil.selectByAddr;
import static de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxCasUtil.selectSentenceAt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.uima.UIMAException;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.jcas.JCas;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.form.AjaxFormSubmitBehavior;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.OnLoadHeaderItem;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.NumberTextField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.Model;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.wicketstuff.annotation.mount.MountPath;

import wicket.contrib.input.events.EventType;
import wicket.contrib.input.events.InputBehavior;
import wicket.contrib.input.events.key.KeyType;
import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationService;
import de.tudarmstadt.ukp.clarin.webanno.api.RepositoryService;
import de.tudarmstadt.ukp.clarin.webanno.api.UserDao;
import de.tudarmstadt.ukp.clarin.webanno.brat.annotation.BratAnnotator;
import de.tudarmstadt.ukp.clarin.webanno.brat.annotation.BratAnnotatorModel;
import de.tudarmstadt.ukp.clarin.webanno.brat.annotation.component.AnnotationDetailEditorPanel;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxCasUtil;
import de.tudarmstadt.ukp.clarin.webanno.brat.project.PreferencesUtil;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocumentState;
import de.tudarmstadt.ukp.clarin.webanno.model.Mode;
import de.tudarmstadt.ukp.clarin.webanno.model.ScriptDirection;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.User;
import de.tudarmstadt.ukp.clarin.webanno.webapp.dialog.OpenModalWindowPanel;
import de.tudarmstadt.ukp.clarin.webanno.webapp.home.page.ApplicationPageBase;
import de.tudarmstadt.ukp.clarin.webanno.webapp.page.annotation.component.AnnotationLayersModalPanel;
import de.tudarmstadt.ukp.clarin.webanno.webapp.page.annotation.component.DocumentNamePanel;
import de.tudarmstadt.ukp.clarin.webanno.webapp.page.annotation.component.ExportModalPanel;
import de.tudarmstadt.ukp.clarin.webanno.webapp.page.annotation.component.FinishImage;
import de.tudarmstadt.ukp.clarin.webanno.webapp.page.annotation.component.FinishLink;
import de.tudarmstadt.ukp.clarin.webanno.webapp.page.annotation.component.GuidelineModalPanel;
import de.tudarmstadt.ukp.clarin.webanno.webapp.page.welcome.WelcomePage;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;

/**
 * A wicket page for the Brat Annotation/Visualization page. Included components for pagination,
 * annotation layer configuration, and Exporting document
 *
 * @author Seid Muhie Yimam
 *
 */
@MountPath("/annotation.html")
public class AnnotationPage
    extends ApplicationPageBase
{
    private static final Log LOG = LogFactory.getLog(AnnotationPage.class);

    private static final long serialVersionUID = 1378872465851908515L;

    @SpringBean(name = "documentRepository")
    private RepositoryService repository;

    @SpringBean(name = "annotationService")
    private AnnotationService annotationService;

    @SpringBean(name = "userRepository")
    private UserDao userRepository;

    private BratAnnotator annotator;

    private FinishImage finish;

    private NumberTextField<Integer> gotoPageTextField;
    private AnnotationDetailEditorPanel annotationDetailEditorPanel;

    private int gotoPageAddress;

    // Open the dialog window on first load
    boolean firstLoad = true;

    private Label numberOfPages;
    private DocumentNamePanel documentNamePanel;

    private long currentprojectId;

    private int totalNumberOfSentence;

    private boolean closeButtonClicked;
    public BratAnnotatorModel bModel = new BratAnnotatorModel();

    public AnnotationPage()
    {
        annotationDetailEditorPanel = new AnnotationDetailEditorPanel(
                "annotationDetailEditorPanel", new Model<BratAnnotatorModel>(bModel))
        {
            private static final long serialVersionUID = 2857345299480098279L;

            @Override
            protected void onChange(AjaxRequestTarget aTarget, BratAnnotatorModel aBModel)
            {
                aTarget.addChildren(getPage(), FeedbackPanel.class);

                try {
                    annotator.bratRender(aTarget, getCas(aBModel));
                }
                catch (UIMAException | ClassNotFoundException | IOException e) {
                    LOG.info("Error reading CAS " + e.getMessage());
                    error("Error reading CAS " + e.getMessage());
                    return;
                }

                annotator.bratRenderHighlight(aTarget, aBModel.getSelection().getAnnotation());

                annotator.onChange(aTarget, aBModel);
                annotator.onAnnotate(aTarget, aBModel, aBModel.getSelection().getBegin(), aBModel
                        .getSelection().getEnd());
                if (!aBModel.getSelection().isAnnotate()) {
                    annotator.onDelete(aTarget, aBModel, aBModel.getSelection().getBegin(), aBModel
                            .getSelection().getEnd());
                }

            }

            @Override
            protected void onAutoForward(AjaxRequestTarget aTarget, BratAnnotatorModel aBModel)
            {
                try {
                    annotator.autoForward(aTarget, getCas(aBModel));
                }
                catch (UIMAException | ClassNotFoundException | IOException e) {
                    LOG.info("Error reading CAS " + e.getMessage());
                    error("Error reading CAS " + e.getMessage());
                    return;
                }
            }
        };

        annotationDetailEditorPanel.setOutputMarkupId(true);
        add(annotationDetailEditorPanel);

        annotator = new BratAnnotator("embedder1", new Model<BratAnnotatorModel>(bModel),
                annotationDetailEditorPanel)
        {

            private static final long serialVersionUID = 7279648231521710155L;

            @Override
            public void onChange(AjaxRequestTarget aTarget, BratAnnotatorModel aBratAnnotatorModel)
            {
                bModel = aBratAnnotatorModel;
                aTarget.add(numberOfPages);
            }

            @Override
            public void renderHead(IHeaderResponse aResponse)
            {
                super.renderHead(aResponse);

                // If the page is reloaded in the browser and a document was already open, we need
                // to render it. We use the "later" commands here to avoid polluting the Javascript
                // header items with document data and because loading times are not that critical
                // on a reload.
                if (getModelObject().getProject() != null) {
                    // We want to trigger a late rendering only on a page reload, but not on a
                    // Ajax request.
                    if (!aResponse.getResponse().getClass().getName().endsWith("AjaxResponse")) {
                        aResponse.render(OnLoadHeaderItem.forScript(bratInitLaterCommand()));
                        aResponse.render(OnLoadHeaderItem.forScript(bratRenderLaterCommand()));
                    }
                }
            }
        };

        // This is an Annotation Operation, set model to ANNOTATION mode
        bModel.setMode(Mode.ANNOTATION);
        add(annotator);

        add(documentNamePanel = (DocumentNamePanel) new DocumentNamePanel("documentNamePanel",
                new Model<BratAnnotatorModel>(bModel)).setOutputMarkupId(true));

        numberOfPages = new Label("numberOfPages", new Model<String>());
        numberOfPages.setOutputMarkupId(true);
        add(numberOfPages);

        final ModalWindow openDocumentsModal;
        add(openDocumentsModal = new ModalWindow("openDocumentsModal"));
        openDocumentsModal.setOutputMarkupId(true);

        openDocumentsModal.setInitialWidth(500);
        openDocumentsModal.setInitialHeight(300);
        openDocumentsModal.setResizable(true);
        openDocumentsModal.setWidthUnit("px");
        openDocumentsModal.setHeightUnit("px");
        openDocumentsModal.setTitle("Open document");
        openDocumentsModal.setCloseButtonCallback(new ModalWindow.CloseButtonCallback()
        {
            private static final long serialVersionUID = -5423095433535634321L;

            @Override
            public boolean onCloseButtonClicked(AjaxRequestTarget aTarget)
            {
                closeButtonClicked = true;
                return true;
            }
        });

        add(new AjaxLink<Void>("showOpenDocumentModal")
        {
            private static final long serialVersionUID = 7496156015186497496L;

            @Override
            public void onClick(AjaxRequestTarget target)
            {
                closeButtonClicked = false;
                openDocumentsModal.setContent(new OpenModalWindowPanel(openDocumentsModal
                        .getContentId(), bModel, openDocumentsModal, Mode.ANNOTATION)
                {

                    private static final long serialVersionUID = -3434069761864809703L;

                    @Override
                    protected void onCancel(AjaxRequestTarget aTarget)
                    {
                        closeButtonClicked = true;
                    };
                });
                openDocumentsModal.setWindowClosedCallback(new ModalWindow.WindowClosedCallback()
                {
                    private static final long serialVersionUID = -1746088901018629567L;

                    @Override
                    public void onClose(AjaxRequestTarget target)
                    {
                        // A hack, the dialog opens for the first time, and if no document is
                        // selected window will be "blind down". Something in the brat js causes
                        // this!
                        if (bModel.getProject() == null || bModel.getDocument() == null) {
                            setResponsePage(WelcomePage.class);
                        }

                        // Dialog was cancelled rather that a document was selected.
                        if (closeButtonClicked) {
                            return;
                        }

                        loadDocumentAction(target);
                    }
                });
                // target.appendJavaScript("Wicket.Window.unloadConfirmation = false;");
                openDocumentsModal.show(target);
            }
        });

        add(new AnnotationLayersModalPanel("annotationLayersModalPanel",
                new Model<BratAnnotatorModel>(bModel))
        {
            private static final long serialVersionUID = -4657965743173979437L;

            @Override
            protected void onChange(AjaxRequestTarget aTarget)
            {
                // annotator.reloadContent(aTarget);
                aTarget.appendJavaScript("Wicket.Window.unloadConfirmation = false;"
                        + "window.location.reload()");

            }
        });

        add(new ExportModalPanel("exportModalPanel", new Model<BratAnnotatorModel>(bModel)));
        // Show the previous document, if exist
        add(new AjaxLink<Void>("showPreviousDocument")
        {
            private static final long serialVersionUID = 7496156015186497496L;

            /**
             * Get the current beginning sentence address and add on it the size of the display
             * window
             */
            @Override
            public void onClick(AjaxRequestTarget aTarget)
            {
                // List of all Source Documents in the project
                List<SourceDocument> listOfSourceDocuements = repository.listSourceDocuments(bModel
                        .getProject());

                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                User user = userRepository.get(username);

                List<SourceDocument> sourceDocumentsinIgnorState = new ArrayList<SourceDocument>();
                for (SourceDocument sourceDocument : listOfSourceDocuements) {
                    if (repository.existsAnnotationDocument(sourceDocument, user)
                            && repository.getAnnotationDocument(sourceDocument, user).getState()
                                    .equals(AnnotationDocumentState.IGNORE)) {
                        sourceDocumentsinIgnorState.add(sourceDocument);
                    }
                }

                listOfSourceDocuements.removeAll(sourceDocumentsinIgnorState);

                // Index of the current source document in the list
                int currentDocumentIndex = listOfSourceDocuements.indexOf(bModel.getDocument());

                // If the first the document
                if (currentDocumentIndex == 0) {
                    aTarget.appendJavaScript("alert('This is the first document!')");
                    return;
                }
                bModel.setDocumentName(listOfSourceDocuements.get(currentDocumentIndex - 1)
                        .getName());
                bModel.setDocument(listOfSourceDocuements.get(currentDocumentIndex - 1));

                loadDocumentAction(aTarget);
            }
        }.add(new InputBehavior(new KeyType[] { KeyType.Shift, KeyType.Page_up }, EventType.click)));

        // Show the next document if exist
        add(new AjaxLink<Void>("showNextDocument")
        {
            private static final long serialVersionUID = 7496156015186497496L;

            /**
             * Get the current beginning sentence address and add on it the size of the display
             * window
             */
            @Override
            public void onClick(AjaxRequestTarget aTarget)
            {
                // List of all Source Documents in the project
                List<SourceDocument> listOfSourceDocuements = repository.listSourceDocuments(bModel
                        .getProject());

                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                User user = userRepository.get(username);

                List<SourceDocument> sourceDocumentsinIgnorState = new ArrayList<SourceDocument>();
                for (SourceDocument sourceDocument : listOfSourceDocuements) {
                    if (repository.existsAnnotationDocument(sourceDocument, user)
                            && repository.getAnnotationDocument(sourceDocument, user).getState()
                                    .equals(AnnotationDocumentState.IGNORE)) {
                        sourceDocumentsinIgnorState.add(sourceDocument);
                    }
                }

                listOfSourceDocuements.removeAll(sourceDocumentsinIgnorState);

                // Index of the current source document in the list
                int currentDocumentIndex = listOfSourceDocuements.indexOf(bModel.getDocument());

                // If the first document
                if (currentDocumentIndex == listOfSourceDocuements.size() - 1) {
                    aTarget.appendJavaScript("alert('This is the last document!')");
                    return;
                }
                bModel.setDocumentName(listOfSourceDocuements.get(currentDocumentIndex + 1)
                        .getName());
                bModel.setDocument(listOfSourceDocuements.get(currentDocumentIndex + 1));

                loadDocumentAction(aTarget);
            }
        }.add(new InputBehavior(new KeyType[] { KeyType.Shift, KeyType.Page_down }, EventType.click)));

        // Show the next page of this document
        add(new AjaxLink<Void>("showNext")
        {
            private static final long serialVersionUID = 7496156015186497496L;

            /**
             * Get the current beginning sentence address and add on it the size of the display
             * window
             */
            @Override
            public void onClick(AjaxRequestTarget aTarget)
            {
                try {
                    if (bModel.getDocument() != null) {
                        JCas jCas = getJCas();
                        int nextSentenceAddress = BratAjaxCasUtil.getNextPageFirstSentenceAddress(
                                jCas, bModel.getSentenceAddress(), bModel.getPreferences()
                                        .getWindowSize());
                        if (bModel.getSentenceAddress() != nextSentenceAddress) {

                            ubdateSentenceNumber(jCas, nextSentenceAddress);

                            aTarget.addChildren(getPage(), FeedbackPanel.class);
                            annotator.bratRenderLater(aTarget);
                            gotoPageTextField.setModelObject(BratAjaxCasUtil
                                    .getFirstSentenceNumber(jCas, bModel.getSentenceAddress()) + 1);
                            updateSentenceAddress(jCas, aTarget);
                        }

                        else {
                            aTarget.appendJavaScript("alert('This is last page!')");
                        }
                    }
                    else {
                        aTarget.appendJavaScript("alert('Please open a document first!')");
                    }
                }
                catch (Exception e) {
                    error(e.getMessage());
                    aTarget.addChildren(getPage(), FeedbackPanel.class);
                }
            }
        }.add(new InputBehavior(new KeyType[] { KeyType.Page_down }, EventType.click)));

        // Show the previous page of this document
        add(new AjaxLink<Void>("showPrevious")
        {
            private static final long serialVersionUID = 7496156015186497496L;

            @Override
            public void onClick(AjaxRequestTarget aTarget)
            {
                try {
                    if (bModel.getDocument() != null) {

                        JCas jCas = getJCas();

                        int previousSentenceAddress = BratAjaxCasUtil
                                .getPreviousDisplayWindowSentenceBeginAddress(jCas, bModel
                                        .getSentenceAddress(), bModel.getPreferences()
                                        .getWindowSize());
                        if (bModel.getSentenceAddress() != previousSentenceAddress) {

                            ubdateSentenceNumber(jCas, previousSentenceAddress);

                            aTarget.addChildren(getPage(), FeedbackPanel.class);
                            annotator.bratRenderLater(aTarget);
                            gotoPageTextField.setModelObject(BratAjaxCasUtil
                                    .getFirstSentenceNumber(jCas, bModel.getSentenceAddress()) + 1);
                            updateSentenceAddress(jCas, aTarget);
                        }
                        else {
                            aTarget.appendJavaScript("alert('This is First Page!')");
                        }
                    }
                    else {
                        aTarget.appendJavaScript("alert('Please open a document first!')");
                    }
                }
                catch (Exception e) {
                    error(e.getMessage());
                    aTarget.addChildren(getPage(), FeedbackPanel.class);
                }
            }
        }.add(new InputBehavior(new KeyType[] { KeyType.Page_up }, EventType.click)));

        add(new AjaxLink<Void>("showFirst")
        {
            private static final long serialVersionUID = 7496156015186497496L;

            @Override
            public void onClick(AjaxRequestTarget aTarget)
            {
                try {
                    if (bModel.getDocument() != null) {

                        JCas jCas = getJCas();

                        if (bModel.getFirstSentenceAddress() != bModel.getSentenceAddress()) {

                            ubdateSentenceNumber(jCas, bModel.getFirstSentenceAddress());

                            aTarget.addChildren(getPage(), FeedbackPanel.class);
                            annotator.bratRenderLater(aTarget);
                            gotoPageTextField.setModelObject(BratAjaxCasUtil
                                    .getFirstSentenceNumber(jCas, bModel.getSentenceAddress()) + 1);
                            updateSentenceAddress(jCas, aTarget);
                        }
                        else {
                            aTarget.appendJavaScript("alert('This is first page!')");
                        }
                    }
                    else {
                        aTarget.appendJavaScript("alert('Please open a document first!')");
                    }
                }
                catch (Exception e) {
                    error(e.getMessage());
                    aTarget.addChildren(getPage(), FeedbackPanel.class);
                }
            }
        }.add(new InputBehavior(new KeyType[] { KeyType.Home }, EventType.click)));

        add(new AjaxLink<Void>("showLast")
        {
            private static final long serialVersionUID = 7496156015186497496L;

            @Override
            public void onClick(AjaxRequestTarget aTarget)
            {
                try {
                    if (bModel.getDocument() != null) {

                        JCas jCas = getJCas();

                        int lastDisplayWindowBeginingSentenceAddress = BratAjaxCasUtil
                                .getLastDisplayWindowFirstSentenceAddress(jCas, bModel
                                        .getPreferences().getWindowSize());
                        if (lastDisplayWindowBeginingSentenceAddress != bModel.getSentenceAddress()) {

                            ubdateSentenceNumber(jCas, lastDisplayWindowBeginingSentenceAddress);

                            aTarget.addChildren(getPage(), FeedbackPanel.class);
                            annotator.bratRenderLater(aTarget);
                            gotoPageTextField.setModelObject(BratAjaxCasUtil
                                    .getFirstSentenceNumber(jCas, bModel.getSentenceAddress()) + 1);
                            updateSentenceAddress(jCas, aTarget);
                        }
                        else {
                            aTarget.appendJavaScript("alert('This is last Page!')");
                        }
                    }
                    else {
                        aTarget.appendJavaScript("alert('Please open a document first!')");
                    }
                }
                catch (Exception e) {
                    error(e.getMessage());
                    aTarget.addChildren(getPage(), FeedbackPanel.class);
                }
            }
        }.add(new InputBehavior(new KeyType[] { KeyType.End }, EventType.click)));

        add(new AjaxLink<Void>("toggleScriptDirection")
        {
            private static final long serialVersionUID = -4332566542278611728L;

            @Override
            public void onClick(AjaxRequestTarget aTarget)
            {
                if (ScriptDirection.LTR.equals(bModel.getScriptDirection())) {
                    bModel.setScriptDirection(ScriptDirection.RTL);
                }
                else {
                    bModel.setScriptDirection(ScriptDirection.LTR);
                }
                annotator.bratRenderLater(aTarget);
            }
        });
        add(new GuidelineModalPanel("guidelineModalPanel", new Model<BratAnnotatorModel>(bModel)));

        gotoPageTextField = (NumberTextField<Integer>) new NumberTextField<Integer>("gotoPageText",
                new Model<Integer>(0));
        Form<Void> gotoPageTextFieldForm = new Form<Void>("gotoPageTextFieldForm");
        gotoPageTextFieldForm.add(new AjaxFormSubmitBehavior(gotoPageTextFieldForm, "onsubmit")
        {
            private static final long serialVersionUID = -4549805321484461545L;

            @Override
            protected void onSubmit(AjaxRequestTarget aTarget)
            {
                try {
                    if (gotoPageAddress == 0) {
                        aTarget.appendJavaScript("alert('The sentence number entered is not valid')");
                        return;
                    }
                    if (bModel.getSentenceAddress() != gotoPageAddress) {
                        JCas jCas = getJCas();

                        ubdateSentenceNumber(jCas, gotoPageAddress);

                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        annotator.bratRenderLater(aTarget);
                        aTarget.add(numberOfPages);
                        gotoPageTextField.setModelObject(BratAjaxCasUtil.getFirstSentenceNumber(
                                jCas, bModel.getSentenceAddress()) + 1);
                        aTarget.add(gotoPageTextField);
                    }
                }
                catch (Exception e) {
                    error(e.getMessage());
                    aTarget.addChildren(getPage(), FeedbackPanel.class);
                }
            }
        });
        gotoPageTextField.setType(Integer.class);
        gotoPageTextField.setMinimum(1);
        gotoPageTextField.setDefaultModelObject(1);
        add(gotoPageTextFieldForm.add(gotoPageTextField));

        gotoPageTextField.add(new AjaxFormComponentUpdatingBehavior("onchange")
        {
            private static final long serialVersionUID = 56637289242712170L;

            @Override
            protected void onUpdate(AjaxRequestTarget aTarget)
            {
                try {
                    if (gotoPageTextField.getModelObject() < 1) {
                        aTarget.appendJavaScript("alert('Page number shouldn't be less than 1')");
                    }
                    else {
                        updateSentenceAddress(getJCas(), aTarget);
                    }
                }
                catch (Exception e) {
                    error(e.getMessage());
                    aTarget.addChildren(getPage(), FeedbackPanel.class);
                }
            }
        });

        add(new AjaxLink<Void>("gotoPageLink")
        {
            private static final long serialVersionUID = 7496156015186497496L;

            @Override
            public void onClick(AjaxRequestTarget aTarget)
            {
                try {
                    if (gotoPageAddress == 0) {
                        aTarget.appendJavaScript("alert('The sentence number entered is not valid')");
                        return;
                    }
                    if (bModel.getDocument() == null) {
                        aTarget.appendJavaScript("alert('Please open a document first!')");
                        return;
                    }
                    if (bModel.getSentenceAddress() != gotoPageAddress) {
                        JCas jCas = getJCas();
                        ubdateSentenceNumber(jCas, gotoPageAddress);

                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        annotator.bratRenderLater(aTarget);
                        aTarget.add(numberOfPages);
                        gotoPageTextField.setModelObject(BratAjaxCasUtil.getFirstSentenceNumber(
                                jCas, bModel.getSentenceAddress()) + 1);
                        aTarget.add(gotoPageTextField);
                    }
                }
                catch (Exception e) {
                    error(e.getMessage());
                    aTarget.addChildren(getPage(), FeedbackPanel.class);
                }
            }
        });

        finish = new FinishImage("finishImage", new Model<BratAnnotatorModel>(bModel));
        finish.setOutputMarkupId(true);

        add(new FinishLink("showYesNoModalPanel", new Model<BratAnnotatorModel>(bModel), finish)
        {
            private static final long serialVersionUID = -4657965743173979437L;
        });
    }

    private void updateSentenceAddress(JCas aJCas, AjaxRequestTarget aTarget)
        throws UIMAException, IOException, ClassNotFoundException
    {
        gotoPageAddress = BratAjaxCasUtil.getSentenceAddress(aJCas,
                gotoPageTextField.getModelObject());

        String labelText = "";
        if (bModel.getDocument() != null) {
            // FIXME Why do we have to re-load the CAS here?
            // bratAnnotatorModel.getUser() is always set to the logged-in user
            JCas jCas1 = repository.readAnnotationCas(bModel.getDocument(), bModel.getUser());
            JCas jCas = jCas1;
            totalNumberOfSentence = BratAjaxCasUtil.getNumberOfPages(jCas);

            // If only one page, start displaying from sentence 1
            if (totalNumberOfSentence == 1) {
                bModel.setSentenceAddress(bModel.getFirstSentenceAddress());
            }
            int sentenceNumber = BratAjaxCasUtil.getFirstSentenceNumber(jCas,
                    bModel.getSentenceAddress());
            int firstSentenceNumber = sentenceNumber + 1;
            int lastSentenceNumber;
            if (firstSentenceNumber + bModel.getPreferences().getWindowSize() - 1 < totalNumberOfSentence) {
                lastSentenceNumber = firstSentenceNumber + bModel.getPreferences().getWindowSize()
                        - 1;
            }
            else {
                lastSentenceNumber = totalNumberOfSentence;
            }

            labelText = "showing " + firstSentenceNumber + "-" + lastSentenceNumber + " of "
                    + totalNumberOfSentence + " sentences";

        }
        else {
            labelText = "";// no document yet selected
        }

        numberOfPages.setDefaultModelObject(labelText);
        aTarget.add(numberOfPages);
        aTarget.add(gotoPageTextField);
    }

    @Override
    public void renderHead(IHeaderResponse response)
    {
        super.renderHead(response);

        String jQueryString = "";
        if (firstLoad) {
            jQueryString += "jQuery('#showOpenDocumentModal').trigger('click');";
            firstLoad = false;
        }
        response.render(OnLoadHeaderItem.forScript(jQueryString));
    }

    private JCas getJCas()
        throws UIMAException, IOException, ClassNotFoundException
    {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.get(username);

        SourceDocument aDocument = bModel.getDocument();

        AnnotationDocument annotationDocument = repository.getAnnotationDocument(aDocument, user);

        // If there is no CAS yet for the annotation document, create one.
        return repository.readAnnotationCas(annotationDocument);
    }

    private void ubdateSentenceNumber(JCas aJCas, int aAddress)
    {
        bModel.setSentenceAddress(aAddress);
        Sentence sentence = selectByAddr(aJCas, Sentence.class, aAddress);
        bModel.setSentenceBeginOffset(sentence.getBegin());
        bModel.setSentenceEndOffset(sentence.getEnd());
        bModel.setSentenceNumber(BratAjaxCasUtil.getSentenceNumber(aJCas, sentence.getBegin()));

        Sentence firstSentence = selectSentenceAt(aJCas, bModel.getSentenceBeginOffset(),
                bModel.getSentenceEndOffset());
        int lastAddressInPage = getLastSentenceAddressInDisplayWindow(aJCas,
                getAddr(firstSentence), bModel.getPreferences().getWindowSize());
        // the last sentence address in the display window
        Sentence lastSentenceInPage = (Sentence) selectByAddr(aJCas, FeatureStructure.class,
                lastAddressInPage);
        bModel.setFSN(BratAjaxCasUtil.getSentenceNumber(aJCas, firstSentence.getBegin()));
        bModel.setLSN(BratAjaxCasUtil.getSentenceNumber(aJCas, lastSentenceInPage.getBegin()));
    }

    private void loadDocumentAction(AjaxRequestTarget aTarget)
    {
        LOG.info("BEGIN LOAD_DOCUMENT_ACTION");

        // Update dynamic elements in action bar
        aTarget.add(finish);
        aTarget.add(numberOfPages);
        aTarget.add(documentNamePanel);

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.get(username);

        bModel.setUser(userRepository.get(username));

        try {
            // Check if there is an annotation document entry in the database. If there is none,
            // create one.
            AnnotationDocument annotationDocument = repository.createOrGetAnnotationDocument(
                    bModel.getDocument(), user);

            // Read the CAS
            JCas jcas = repository.readAnnotationCas(annotationDocument);

            // Update the annotation document CAS
            repository.upgradeCas(jcas.getCas(), annotationDocument);

            // After creating an new CAS or upgrading the CAS, we need to save it
            repository.writeAnnotationCas(jcas.getCas().getJCas(),
                    annotationDocument.getDocument(), user);

            // (Re)initialize brat model after potential creating / upgrading CAS
            bModel.initForDocument(jcas);

            // Load user preferences
            PreferencesUtil.setAnnotationPreference(username, repository, annotationService,
                    bModel, Mode.ANNOTATION);

            // if project is changed, reset some project specific settings
            if (currentprojectId != bModel.getProject().getId()) {
                bModel.initForProject();
            }

            currentprojectId = bModel.getProject().getId();

            LOG.debug("Configured BratAnnotatorModel for user [" + bModel.getUser() + "] f:["
                    + bModel.getFirstSentenceAddress() + "] l:[" + bModel.getLastSentenceAddress()
                    + "] s:[" + bModel.getSentenceAddress() + "]");

            gotoPageTextField.setModelObject(1);

            updateSentenceAddress(jcas, aTarget);

            // Wicket-level rendering of annotator because it becomes visible
            // after selecting a document
            aTarget.add(annotator);

            // brat-level initialization and rendering of document
            annotator.bratInit(aTarget);
            annotator.bratRender(aTarget, jcas);
        }
        catch (DataRetrievalFailureException e) {
            LOG.error("Error", e);
            aTarget.addChildren(getPage(), FeedbackPanel.class);
            error(e.getMessage());
        }
        catch (IOException e) {
            LOG.error("Error", e);
            aTarget.addChildren(getPage(), FeedbackPanel.class);
            error(e.getMessage());
        }
        catch (UIMAException e) {
            LOG.error("Error", e);
            aTarget.addChildren(getPage(), FeedbackPanel.class);
            error(ExceptionUtils.getRootCauseMessage(e));
        }
        catch (ClassNotFoundException e) {
            LOG.error("Error", e);
            aTarget.addChildren(getPage(), FeedbackPanel.class);
            error(e.getMessage());
        }

        LOG.info("END LOAD_DOCUMENT_ACTION");
    }
}
