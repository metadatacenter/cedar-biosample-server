package org.metadatacenter.submission.ncbi.cairr;

import biosample.TypeAttribute;
import biosample.TypeBioSample;
import biosample.TypeBioSampleIdentifier;
import common.sp.TypeBlock;
import common.sp.TypeContactInfo;
import common.sp.TypeDescriptor;
import common.sp.TypeIdentifier;
import common.sp.TypeLocalId;
import common.sp.TypeName;
import common.sp.TypeOrganism;
import common.sp.TypeSPUID;
import generated.Submission;
import generated.TypeAccount;
import generated.TypeFileAttribute;
import generated.TypeInlineData;
import generated.TypeOrganization;
import generated.TypeTargetDb;
import org.metadatacenter.submission.*;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.UUID;

// TODO Very brittle. Need to do a lot more testing for empty values

/**
 * Convert a CEDAR JSON Schema-based CAIRR template instance into a BioProject/BioSample/SRA XML-based submission.
 */
public class CAIRRTemplateInstance2SRAXMLConverter {
  private List<String> bioSampleIds = new ArrayList<>();
  private List<String> sraIds = new ArrayList<>();

  /**
   * The {@link CAIRRTemplate} class is generated by jsonschema2pojo from the
   * CAIRRTemplate.json JSON Schema file in the resources directory.
   *
   * @param cairrInstance A CAIRR template instance
   * @return A string containing a SRA-conformant XML representation of the supplied CAIRR instance
   * @throws DatatypeConfigurationException If a configuration error occurs during processing
   * @throws JAXBException                  If a JAXB error occurs during processing
   */
  public String convertTemplateInstanceToXML(CAIRRTemplate cairrInstance)
      throws DatatypeConfigurationException, JAXBException {
    final generated.ObjectFactory submissionObjectFactory = new generated.ObjectFactory();
    final common.sp.ObjectFactory spCommonObjectFactory = new common.sp.ObjectFactory();
    final biosample.ObjectFactory bioSampleObjectFactory = new biosample.ObjectFactory();

    // This is the NCBI SRA submission. We will generate SRA XML from this submission.
    Submission submission = submissionObjectFactory.createSubmission();

    // Retrieve the BioProject from the CAIRR instance
    BioProject bioProject = cairrInstance.getBioProject();

    /*
     * Object construction for the submission <Description> section
     */
    TypeName contactName = spCommonObjectFactory.createTypeName();
    contactName.setFirst(bioProject.getFirstGivenName().getValue());
    contactName.setLast(bioProject.getLastFamilyName().getValue());

    TypeContactInfo contactInfo = spCommonObjectFactory.createTypeContactInfo();
    contactInfo.setEmail(bioProject.getEMail().getValue());
    contactInfo.setName(contactName);

    TypeOrganization.Name organizationName = submissionObjectFactory.createTypeOrganizationName();
    organizationName.setValue(bioProject.getSubmittingOrganization().getValue());

    TypeAccount contactSubmitter = submissionObjectFactory.createTypeAccount();
    contactSubmitter.setUserName("ahmadchan@gmail.com"); // TODO

    TypeOrganization contactOrganization = submissionObjectFactory.createTypeOrganization();
    contactOrganization.setType("lab"); // TODO
    contactOrganization.setRole("Data submitter"); // TODO
    contactOrganization.setName(organizationName);
    contactOrganization.getContact().add(contactInfo);

    Submission.Description submissionDescription = submissionObjectFactory.createSubmissionDescription();
    submissionDescription.setComment("AIRR (myasthenia gravis) data to the NCBI using the CAIRR"); // TODO
    submissionDescription.setSubmitter(contactSubmitter);
    submissionDescription.getOrganization().add(contactOrganization);
    submission.setDescription(submissionDescription);

    // TODO Other BioProject fields need to be set

    // Retrieve the biosamples from the CAIRR instance
    for (BioSample bioSample : cairrInstance.getBioSample()) {
      // Start <BioSample> section
      TypeBioSample ncbiBioSample = bioSampleObjectFactory.createTypeBioSample();
      ncbiBioSample.setSchemaVersion("2.0"); // XXX: Hard-coded

      // SampleId
      String bioSampleId = createNewBioSampleId();
      TypeBioSampleIdentifier.SPUID spuid = bioSampleObjectFactory.createTypeBioSampleIdentifierSPUID();
      spuid.setSpuidNamespace("CEDAR"); // TODO
      spuid.setValue(bioSampleId);

      TypeBioSampleIdentifier sampleID = bioSampleObjectFactory.createTypeBioSampleIdentifier();
      sampleID.getSPUID().add(spuid);

      ncbiBioSample.setSampleId(sampleID);

      // Descriptor
      JAXBElement descriptionElement = new JAXBElement(new QName("p"), String.class, "AIRR (myasthenia gravis) data " +
          "to the NCBI using the CAIRR"); // TODO

      TypeBlock sampleDescription = spCommonObjectFactory.createTypeBlock();
      sampleDescription.getPOrUlOrOl().add(descriptionElement);

      TypeDescriptor sampleDescriptor = spCommonObjectFactory.createTypeDescriptor();
      sampleDescriptor.setTitle("AIRR (myasthenia gravis) data to the NCBI using the CAIRR"); // XXX: Hard-coded due
      // to no corresponding entry in
      // the AIRR instance
      sampleDescriptor.setDescription(sampleDescription);

      ncbiBioSample.setDescriptor(sampleDescriptor);

      // Organism
      TypeOrganism sampleOrganism = spCommonObjectFactory.createTypeOrganism();
      sampleOrganism.setOrganismName("Homo sapiens"); // TODO

      ncbiBioSample.setOrganism(sampleOrganism);

      // Package
      ncbiBioSample.setPackage("Human.1.0"); // TODO

      // Attributes
      TypeBioSample.Attributes bioSampleAttributes = bioSampleObjectFactory.createTypeBioSampleAttributes();


      // Following are the CAIRR BioSample Elements/attributes

      // Subject ID
      String subjectIdValue = bioSample.getSubjectId().getValue();
      if (subjectIdValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("SubjectId");
        attribute.setValue(subjectIdValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Synthetic Library
      if (bioSample.getSyntheticLibrary().size() > 1) {
        String syntheticLibraryValue = bioSample.getSyntheticLibrary().get(0).getValue();
        if (syntheticLibraryValue != null) {
          TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
          attribute.setAttributeName("SyntheticLibrary");
          attribute.setValue(syntheticLibraryValue);
          bioSampleAttributes.getAttribute().add(attribute);
        }
      }

      // Organism
      String organismValue = bioSample.getOrganism().getId().toString();
      if (organismValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("Organism");
        attribute.setValue(organismValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Sex
      String sexValue = bioSample.getSex().getValue();
      if (sexValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("Sex");
        attribute.setValue(sexValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Age
      String ageValue = bioSample.getAge().getValue();
      if (ageValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("Age");
        attribute.setValue(ageValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Age Event
      String ageEventValue = bioSample.getAgeEvent().getValue();
      if (ageEventValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("AgeEvent");
        attribute.setValue(ageEventValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }


      // Ancestry Population
      String ancestryPopulationValue = bioSample.getAncestryPopulation().getValue();
      if (ancestryPopulationValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("AncestryPopulation");
        attribute.setValue(ancestryPopulationValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }


      // Ethnicity
      String ethnicityValue = bioSample.getEthnicity().getValue();
      if (ethnicityValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("Ethnicity");
        attribute.setValue(ethnicityValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Race
      String raceValue = bioSample.getRace().getValue();
      if (raceValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("Race");
        attribute.setValue(raceValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // StarinName
      String starinNameValue = bioSample.getStarinName().getValue();
      if (starinNameValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("StarinName");
        attribute.setValue(starinNameValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }


      // Relation to other Subject
      String relationToOtherSubjectValue = bioSample.getRelationToOtherSubject().getValue();
      if (relationToOtherSubjectValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("RelationToOtherSubject");
        attribute.setValue(relationToOtherSubjectValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Relation Type
      String relationTypeValue = bioSample.getRelationType().getValue();
      if (relationTypeValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("RelationType");
        attribute.setValue(relationTypeValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }


      // Projected Release Date
      String projectedReleaseDateValue = bioSample.getProjectedReleaseDate().getValue();
      if (projectedReleaseDateValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("ProjectedReleaseDate");
        attribute.setValue(projectedReleaseDateValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }
      // Isolate
      String isolateValue = bioSample.getIsolate().getValue();
      if (isolateValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("Isolate");
        attribute.setValue(isolateValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Diagnosis
      if (bioSample.getDiagnosis2() != null) {
        String diagnosisValue = bioSample.getDiagnosis2().toString();
        if (diagnosisValue != null) {
          TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
          attribute.setAttributeName("Diagnosis");
          attribute.setValue(diagnosisValue);
          bioSampleAttributes.getAttribute().add(attribute);
        }
      }

      // StudyGroupDescription
      String studyGroupDescriptionValue = bioSample.getStudyGroupDescription().getValue();
      if (studyGroupDescriptionValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("StudyGroupDescription");
        attribute.setValue(studyGroupDescriptionValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Length of Disease
      String lengthOfDiseaseValue = bioSample.getLengthOfDisease().getValue();
      if (lengthOfDiseaseValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("LengthOfDisease");
        attribute.setValue(lengthOfDiseaseValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Disease stage
      String diseaseStageValue = bioSample.getDiseaseStage().getValue();
      if (diseaseStageValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("DiseaseStage");
        attribute.setValue(diseaseStageValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Prior Therapies For Primary Disease Under Study
      String priorTherapiesForPrimaryDiseaseUnderStudyValue = bioSample.getPriorTherapiesForPrimaryDiseaseUnderStudy
          ().getValue();
      if (priorTherapiesForPrimaryDiseaseUnderStudyValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("PriorTherapiesForPrimaryDiseaseUnderStudy");
        attribute.setValue(priorTherapiesForPrimaryDiseaseUnderStudyValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }


      // Immunogen
      String immunogenValue = bioSample.getImmunogen().getValue();
      if (immunogenValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("Immunogen");
        attribute.setValue(immunogenValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Intervention Definition
      String interventionDefinitionValue = bioSample.getInterventionDefinition().getValue();
      if (interventionDefinitionValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("InterventionDefinition");
        attribute.setValue(interventionDefinitionValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Other Relevant Medical History
      String otherRelevantMedicalHistoryValue = bioSample.getOtherRelevantMedicalHistory().getValue();
      if (otherRelevantMedicalHistoryValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("OtherRelevantMedicalHistory");
        attribute.setValue(otherRelevantMedicalHistoryValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Sample Name
      String sampleNameValue = bioSample.getSampleName().getValue();
      if (sampleNameValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("SampleName");
        attribute.setValue(sampleNameValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Sample Type
      String sampleTypeValue = bioSample.getSampleType().getValue();
      if (sampleTypeValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("SampleType");
        attribute.setValue(sampleTypeValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }
      // Tissue
      String tissueValue = bioSample.getTissue().getId().toString();
      if (tissueValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("Tissue");
        attribute.setValue(tissueValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Anatomic Site
      String anatomicSiteValue = bioSample.getAnatomicSite().getValue();
      if (anatomicSiteValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("AnatomicSite");
        attribute.setValue(anatomicSiteValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }
      // Disease State of Sample
      String diseaseStateOfSample1value = bioSample.getDiseaseStateOfSample().getValue();
      if (diseaseStateOfSample1value != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("DiseaseStateOfSample1");
        attribute.setValue(diseaseStateOfSample1value);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Sample Collection Time
      String sampleCollectionTime1Value = bioSample.getSampleCollectionTime().getValue();
      if (sampleCollectionTime1Value != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("SampleCollectionTime1");
        attribute.setValue(sampleCollectionTime1Value);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Collection Time Event T01
      String collectionTimeEventT01Value = bioSample.getCollectionTimeEventT0().getValue();
      if (collectionTimeEventT01Value != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("CollectionTimeEventT01");
        attribute.setValue(collectionTimeEventT01Value);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Biomaterial Provider
      String biomaterialProviderValue = bioSample.getBiomaterialProvider().getValue();
      if (biomaterialProviderValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("BiomaterialProvider");
        attribute.setValue(biomaterialProviderValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Geolocation Name
      String geolocationNameValue = bioSample.getGeolocationName().getValue();
      if (geolocationNameValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("GeolocationName");
        attribute.setValue(geolocationNameValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Tissue Processing
      String tissueProcessingValue = bioSample.getTissueProcessing().getValue();
      if (tissueProcessingValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("TissueProcessing");
        attribute.setValue(tissueProcessingValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Cell Subset
      String cellSubsetValue = bioSample.getCellSubset().getId().toString();
      if (cellSubsetValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("CellSubset");
        attribute.setValue(cellSubsetValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Cell Subset Phenotype
      String cellSubsetPhenotypeValue = bioSample.getCellSubsetPhenotype().getValue();
      if (cellSubsetPhenotypeValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("CellSubsetPhenotype");
        attribute.setValue(cellSubsetPhenotypeValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Single-cell Sort
      String singleCellSortValue = bioSample.getSingleCellSort().getValue();
      if (singleCellSortValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("SingleCellSort");
        attribute.setValue(singleCellSortValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Number of Cells in Experiment
      String numberOfCellsInExperiment1Value = bioSample.getNumberOfCellsInExperiment().getValue();
      if (numberOfCellsInExperiment1Value != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("NumberOfCellsInExperiment");
        attribute.setValue(numberOfCellsInExperiment1Value);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Number of Cells per Sequencing Reaction1
      String numberOfCellsPerSequencingReaction1Value = bioSample.getNumberOfCellsPerSequencingReaction().getValue();
      if (numberOfCellsPerSequencingReaction1Value != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("NumberOfCellsPerSequencingReaction");
        attribute.setValue(numberOfCellsPerSequencingReaction1Value);
        bioSampleAttributes.getAttribute().add(attribute);
      }


      // Cell Storage1
      String cellStorage1Value = bioSample.getCellStorage().getValue();
      if (cellStorage1Value != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("CellStorage");
        attribute.setValue(cellStorage1Value);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Cell Quality
      String cellQualityValue = bioSample.getCellQuality().getValue();
      if (cellQualityValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("CellQuality");
        attribute.setValue(cellQualityValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Cell Isolation
      String cellIsolationValue = bioSample.getCellIsolation().getValue();
      if (cellIsolationValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("CellIsolation");
        attribute.setValue(cellIsolationValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }

      // Processing Protocol
      String processingProtocolValue = bioSample.getProcessingProtocol().getValue();
      if (processingProtocolValue != null) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName("ProcessingProtocol");
        attribute.setValue(processingProtocolValue);
        bioSampleAttributes.getAttribute().add(attribute);
      }


      // Optional attributes
      for (OptionalBioSampleAttribute optionalAttribute : bioSample.getOptionalBioSampleAttribute()) {
        TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
        attribute.setAttributeName(optionalAttribute.getName().getValue());
        attribute.setValue(optionalAttribute.getValue().getValue());
        bioSampleAttributes.getAttribute().add(attribute);
      }

      ncbiBioSample.setAttributes(bioSampleAttributes);

      // XmlContent
      // Developement Note: The original NCBI submission doesn't includ the BioSample element, so it
      // is required to append the rule in the submission.xsd file (See submission.xsd:441)
      TypeInlineData.XmlContent xmlContent = submissionObjectFactory.createTypeInlineDataXmlContent();
      xmlContent.setBioSample(ncbiBioSample);

      // Data
      Submission.Action.AddData.Data bioSampleData = submissionObjectFactory.createSubmissionActionAddDataData();
      bioSampleData.setContentType("XML"); // TODO
      bioSampleData.setXmlContent(xmlContent);

      // Identifier
      TypeSPUID bioSampleSpuid = spCommonObjectFactory.createTypeSPUID();
      bioSampleSpuid.setSpuidNamespace("CEDAR"); // TODO
      bioSampleSpuid.setValue(bioSampleId);

      TypeIdentifier bioSampleIdentifier = spCommonObjectFactory.createTypeIdentifier();
      bioSampleIdentifier.setSPUID(bioSampleSpuid);

      // AddData
      Submission.Action.AddData bioSampleAddData = submissionObjectFactory.createSubmissionActionAddData();
      bioSampleAddData.setTargetDb(TypeTargetDb.BIO_SAMPLE);
      bioSampleAddData.setData(bioSampleData);
      bioSampleAddData.setIdentifier(bioSampleIdentifier);

      // Action
      Submission.Action bioSampleAction = submissionObjectFactory.createSubmissionAction();
      bioSampleAction.setAddData(bioSampleAddData);

      submission.getAction().add(bioSampleAction);
    }

    // Retrieve the SRAs from the CAIRR instance
    int sraIndex = 0; // to track the corresponding BioSample record for this SRA entry
    for (SequenceReadArchive sequenceReadArchive : cairrInstance.getSequenceReadArchive()) {

      // AddFiles
      Submission.Action.AddFiles sraAddFiles = submissionObjectFactory.createSubmissionActionAddFiles();
      sraAddFiles.setTargetDb(TypeTargetDb.SRA);

      // TODO Process each SRA section


      // File
      for (SRAMultipleFileUploadAttribute sraFileUploadAttribute : sequenceReadArchive
          .getSRAMultipleFileUploadAttributes()) {

        FileName fileName = sraFileUploadAttribute.getFileName();
        FileType fileType = sraFileUploadAttribute.getFileType();

        if (fileName != null && fileType != null) {
          Submission.Action.AddFiles.File sraFile = submissionObjectFactory.createSubmissionActionAddFilesFile();
          sraFile.setFilePath(fileName.getValue());
          sraFile.setDataType(fileType.getValue());
          sraAddFiles.getFile().add(sraFile);
        }
      }

      // library ID

      String libraryIDValue = sequenceReadArchive.getLibraryID().getValue();
      if (libraryIDValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("LibraryID");
        fileAttribute.setValue(libraryIDValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Library Name

      String libraryNameValue = sequenceReadArchive.getLibraryName().getValue();
      if (libraryNameValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("LibraryName");
        fileAttribute.setValue(libraryNameValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Library instrument

      String LibraryInstrument1Value = sequenceReadArchive.getLibraryInstrument().getValue();
      if (LibraryInstrument1Value != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("LibraryInstrument");
        fileAttribute.setValue(LibraryInstrument1Value);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Library Strategy

      String LibraryStrategy1Value = sequenceReadArchive.getLibraryStrategy().getValue();
      if (LibraryStrategy1Value != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("LibraryStrategy");
        fileAttribute.setValue(LibraryStrategy1Value);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }


// Library Source

      String librarySourceValue = sequenceReadArchive.getLibrarySource().getValue();
      if (librarySourceValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("LibrarySource");
        fileAttribute.setValue(librarySourceValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }


      // Library Selection

      String librarySelectionValue = sequenceReadArchive.getLibrarySelection().getValue();
      if (librarySelectionValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("LibrarySelection");
        fileAttribute.setValue(librarySelectionValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Library Layout

      String libraryLayoutValue = sequenceReadArchive.getLibraryLayout().getValue();
      if (libraryLayoutValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("LibraryLayout");
        fileAttribute.setValue(libraryLayoutValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }
      // Library Construction Protocol

      String libraryConstructionProtocolValue = sequenceReadArchive.getLibraryConstructionProtocol().getValue();
      if (libraryConstructionProtocolValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("LibraryConstructionProtocol");
        fileAttribute.setValue(libraryConstructionProtocolValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }


      // Design Description

      String designDescriptionValue = sequenceReadArchive.getDesignDescription().getValue();
      if (designDescriptionValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("DesignDescription");
        fileAttribute.setValue(designDescriptionValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }


      // Target Substrate

      String targetSubstrateValue = sequenceReadArchive.getTargetSubstrate().getValue();
      if (targetSubstrateValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("TargetSubstrate");
        fileAttribute.setValue(targetSubstrateValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Target Substrate Quality

      String targetSubstrateQualityValue = sequenceReadArchive.getTargetSubstrateQuality().getValue();
      if (targetSubstrateQualityValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("TargetSubstrateQuality");
        fileAttribute.setValue(targetSubstrateQualityValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Library Generation Method

      String libraryGenerationMethodValue = sequenceReadArchive.getLibraryGenerationMethod().getValue();
      if (libraryGenerationMethodValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("LibraryGenerationMethod");
        fileAttribute.setValue(libraryGenerationMethodValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Library Generation Protocol

      String libraryGenerationProtocolValue = sequenceReadArchive.getLibraryGenerationProtocol().getValue();
      if (libraryGenerationProtocolValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("LibraryGenerationProtocol");
        fileAttribute.setValue(libraryGenerationProtocolValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Target Locus for PCR

      String TargetLocusForPCRValue = sequenceReadArchive.getTargetLocusForPCR().getValue();
      if (TargetLocusForPCRValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("TargetLocusForPCR");
        fileAttribute.setValue(TargetLocusForPCRValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }


      // Forward PCR Primer Target Location

      String forwardPCRPrimerTargetLocationValue = sequenceReadArchive.getForwardPCRPrimerTargetLocation().getValue();
      if (forwardPCRPrimerTargetLocationValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("ForwardPCRPrimerTargetLocation");
        fileAttribute.setValue(forwardPCRPrimerTargetLocationValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Reverse PCR Primer Target Location

      String reversePCRPrimerTargetLocationValue = sequenceReadArchive.getReversePCRPrimerTargetLocation().getValue();
      if (reversePCRPrimerTargetLocationValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("ReversePCRPrimerTargetLocation");
        fileAttribute.setValue(reversePCRPrimerTargetLocationValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }


      // Complete Sequence

      String completeSequenceValue = sequenceReadArchive.getCompleteSequence().getValue();
      if (completeSequenceValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("CompleteSequence");
        fileAttribute.setValue(completeSequenceValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Physical Linkage of Different Loci

      String physicalLinkageOfDifferentLociValue = sequenceReadArchive.getPhysicalLinkageOfDifferentLoci().getValue();
      if (physicalLinkageOfDifferentLociValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("PhysicalLinkageOfDifferentLoci");
        fileAttribute.setValue(physicalLinkageOfDifferentLociValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }


      // Template Amount

      String TemplateAmountValue = sequenceReadArchive.getTemplateAmount().getValue();
      if (TemplateAmountValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("TemplateAmount");
        fileAttribute.setValue(TemplateAmountValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Total Reads Passing QC Filter1

      String totalReadsPassingQCFilterValue = sequenceReadArchive.getTotalReadsPassingQCFilter().getValue();
      if (totalReadsPassingQCFilterValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("TotalReadsPassingQCFilter");
        fileAttribute.setValue(totalReadsPassingQCFilterValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Protocol ID

      String protocolIDValue = sequenceReadArchive.getProtocolID().getValue();
      if (protocolIDValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("ProtocolID");
        fileAttribute.setValue(protocolIDValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }


      // Sequencing Platform

      String sequencingPlatformValue = sequenceReadArchive.getSequencingPlatform().getValue();
      if (sequencingPlatformValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("SequencingPlatform1");
        fileAttribute.setValue(sequencingPlatformValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }


      // Sequencing Read Lengths

      String readLengthsValue = sequenceReadArchive.getReadLengths().getValue();
      if (readLengthsValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("ReadLengths");
        fileAttribute.setValue(readLengthsValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Sequencing Facility

      String sequencingFacilityValue = sequenceReadArchive.getSequencingFacility().getValue();
      if (sequencingFacilityValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("SequencingFacility");
        fileAttribute.setValue(sequencingFacilityValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Batch Number

      String batchNumberValue = sequenceReadArchive.getBatchNumber().getValue();
      if (batchNumberValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("BatchNumber");
        fileAttribute.setValue(batchNumberValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Date of Sequencing Run

      String dateOfSequencingRunValue = sequenceReadArchive.getDateOfSequencingRun().getValue();
      if (dateOfSequencingRunValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("DateOfSequencingRun");
        fileAttribute.setValue(dateOfSequencingRunValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Sequencing Kit
      String sequencingKitValue = sequenceReadArchive.getSequencingKit().getValue();
      if (sequencingKitValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("SequencingKit");
        fileAttribute.setValue(sequencingKitValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }


      ///End of AIRR SRA Elements

      // Identifier: For SRA
      TypeLocalId localSraId = spCommonObjectFactory.createTypeLocalId();
      localSraId.setValue(createNewSraId());

      TypeIdentifier sraIdentifier = spCommonObjectFactory.createTypeIdentifier();
      sraIdentifier.setLocalId(localSraId);

      sraAddFiles.setIdentifier(sraIdentifier);

      // Action
      Submission.Action sraAction = submissionObjectFactory.createSubmissionAction();
      sraAction.setAddFiles(sraAddFiles);

      submission.getAction().add(sraAction);

      sraIndex++; // increment the index counter
    }

    // Generate XML from the submission instance
    StringWriter writer = new StringWriter();
    JAXBContext ctx = JAXBContext.newInstance(Submission.class);
    Marshaller marshaller = ctx.createMarshaller();
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
    marshaller.marshal(submission, writer);

    return writer.toString();
  }

  private XMLGregorianCalendar createXMLGregorianCalendar(String date) throws DatatypeConfigurationException {
    DatatypeFactory datatypeFactory = DatatypeFactory.newInstance();
    GregorianCalendar gc = new GregorianCalendar();

    return datatypeFactory.newXMLGregorianCalendar(gc);
  }

  private String createNewBioSampleId() {
    String id = "BioSample-" + UUID.randomUUID();
    bioSampleIds.add(id);
    return id;
  }

  private String createNewSraId() {
    String id = "SRA-" + UUID.randomUUID();
    sraIds.add(id);
    return id;
  }

  private String getBioSampleId(int index) {
    return bioSampleIds.get(index);
  }

  private String getSraId(int index) {
    return sraIds.get(index);
  }

}
