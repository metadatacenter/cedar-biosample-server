package org.metadatacenter.submission.ncbi.cairr;

import biosample.TypeAttribute;
import biosample.TypeBioSample;
import biosample.TypeBioSampleIdentifier;
import common.sp.TypeBlock;
import common.sp.TypeContactInfo;
import common.sp.TypeDescriptor;
import common.sp.TypeIdentifier;
import common.sp.TypeName;
import common.sp.TypeOrganism;
import common.sp.TypePrimaryId;
import common.sp.TypeRefId;
import common.sp.TypeSPUID;
import generated.Submission;
import generated.TypeAccount;
import generated.TypeFileAttribute;
import generated.TypeFileAttributeRefId;
import generated.TypeInlineData;
import generated.TypeOrganization;
import generated.TypeTargetDb;
import org.metadatacenter.submission.BioProjectForAIRRNCBI;
import org.metadatacenter.submission.BioSampleForAIRRNCBI;
import org.metadatacenter.submission.CAIRRTemplate;
import org.metadatacenter.submission.SequenceReadArchiveForAIRRNCBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.Map;
import java.util.UUID;

// TODO Very brittle. Need to do a lot more testing for empty values

/**
 * Convert a CEDAR JSON Schema-based CAIRR template instance into a BioProject/BioSample/SRA XML-based submission.
 */
public class CAIRRTemplateInstance2SRAXMLConverter {
  final static Logger logger = LoggerFactory.getLogger(CAIRRTemplateInstance2SRAXMLConverter.class);

  private final static String CEDAR_NAMESPACE = "CEDAR";

  private List<String> bioSampleIds = new ArrayList<>();
  private List<String> sraIds = new ArrayList<>();

  private final generated.ObjectFactory submissionObjectFactory = new generated.ObjectFactory();
  private final common.sp.ObjectFactory ncbiCommonObjectFactory = new common.sp.ObjectFactory();
  private final biosample.ObjectFactory bioSampleObjectFactory = new biosample.ObjectFactory();
  private final bioproject.ObjectFactory bioProjectObjectFactory = new bioproject.ObjectFactory();

  /**
   * The {@link CAIRRTemplate} class is generated by jsonschema2pojo from the
   * CAIRRTemplate.json JSON Schema file in the resources directory.
   * <p>
   * See https://github.com/airr-community/airr-standards/blob/master/NCBI_implementation/mapping_MiAIRR_BioProject.tsv
   * for CAIRR BioProject element to NCBI BioProject element mapping.
   *
   * See https://github.com/airr-community/airr-standards/blob/master/NCBI_implementation/mapping_MiAIRR_SRA.tsv
   * for mapping to NCBI SRA.
   * <p>
   * An example BioProject submission can be found here:
   * https://www.ncbi.nlm.nih.gov/viewvc/v1/trunk/submit/public-docs/bioproject/samples/bp.submission.xml?view=markup
   *
   * @param cairrInstance A CAIRR template instance
   * @return A string containing a SRA-conformant XML representation of the supplied CAIRR instance
   * @throws DatatypeConfigurationException If a configuration error occurs during processing
   * @throws JAXBException                  If a JAXB error occurs during processing
   */
  public String convertTemplateInstanceToXML(CAIRRTemplate cairrInstance)
      throws JAXBException, DatatypeConfigurationException {
    Submission ncbiSubmission = submissionObjectFactory.createSubmission();

    BioProjectForAIRRNCBI cairrBioProject = cairrInstance.getBioProjectForAIRRNCBI();
    String bioProjectID;
    if (cairrBioProject.getStudyID() != null && cairrBioProject.getStudyID().getValue() != null) {
      bioProjectID = cairrBioProject.getStudyID().getValue();
    } else {
      bioProjectID = "";
    }

    // Create a NCBI BioProject element
    //TypeProject ncbiBioProject = bioProjectObjectFactory.createTypeProject();

    Submission.Description submissionDescription = createSubmissionDescription(ncbiSubmission, cairrBioProject);
    ncbiSubmission.setDescription(submissionDescription);

    // Retrieve the biosamples from the CAIRR instance
    for (BioSampleForAIRRNCBI bioSample : cairrInstance.getBioSampleForAIRRNCBI()) {
      // Start <BioSample> section
      TypeBioSample ncbiBioSample = bioSampleObjectFactory.createTypeBioSample();
      ncbiBioSample.setSchemaVersion("2.0"); // Hard-coded

      // Sample Name (which is actually the sample ID )
      if (bioSample.getSampleID() != null) {
        String bioSampleID = bioSample.getSampleID().getValue();
        if (bioSampleID != null && !bioProjectID.isEmpty()) {
          ncbiBioSample.setSampleId(createBioSampleIdentifier(bioSampleID));
        }
      }

      // Descriptor
      ncbiBioSample.setDescriptor(createDescriptor("AIRR Submission", "AIRR Submission")); // TODO

      // Organism
      ncbiBioSample.setOrganism(createOrganism("Homo sapiens")); // TODO

      // Package
      ncbiBioSample.setPackage("Human.1.0"); // TODO

      // Attributes
      ncbiBioSample.setAttributes(createBioSampleAttributes(bioSample));

      // XmlContent
      // Development Note: The original NCBI submission doesn't include the BioSample element, so it
      // is required to modify the submission.xsd file (See submission.xsd:441)
      TypeInlineData.XmlContent xmlContent = submissionObjectFactory.createTypeInlineDataXmlContent();
      xmlContent.setBioSample(ncbiBioSample);

      // Data
      Submission.Action.AddData.Data bioSampleData = submissionObjectFactory.createSubmissionActionAddDataData();
      bioSampleData.setContentType("XML");
      bioSampleData.setXmlContent(xmlContent);

      // Identifier
      TypeIdentifier actionIdentifier = ncbiCommonObjectFactory.createTypeIdentifier();
      TypeSPUID bioSampleSpuid = ncbiCommonObjectFactory.createTypeSPUID();
      bioSampleSpuid.setSpuidNamespace(CEDAR_NAMESPACE);
      bioSampleSpuid.setValue(createNewActionId());
      actionIdentifier.setSPUID(bioSampleSpuid);

      // Action/AddData
      Submission.Action.AddData bioSampleSubmissionActionAddData = submissionObjectFactory
          .createSubmissionActionAddData();
      bioSampleSubmissionActionAddData.setTargetDb(TypeTargetDb.BIO_SAMPLE);
      bioSampleSubmissionActionAddData.setData(bioSampleData);
      bioSampleSubmissionActionAddData.setIdentifier(actionIdentifier);

      // Action
      Submission.Action bioSampleAction = submissionObjectFactory.createSubmissionAction();
      bioSampleAction.setAddData(bioSampleSubmissionActionAddData);
      ncbiSubmission.getAction().add(bioSampleAction);
    }

    // Retrieve the SRAs from the CAIRR instance
    int sraIndex = 0; // to track the corresponding BioSample record for this SRA entry
    for (SequenceReadArchiveForAIRRNCBI sequenceReadArchive : cairrInstance.getSequenceReadArchiveForAIRRNCBI()) {
      // AddFiles
      Submission.Action.AddFiles sraAddFiles = submissionObjectFactory.createSubmissionActionAddFiles();
      sraAddFiles.setTargetDb(TypeTargetDb.SRA);

      if (sequenceReadArchive.getFileType() != null) {
        String fileType = sequenceReadArchive.getFileType().getValue();

        List<String> fileAttributeNames = sequenceReadArchive.getFilename();
        Map<String, Object> additionalProperties = sequenceReadArchive.getAdditionalProperties();

        for (String fileAttributeName : fileAttributeNames) {

          if (additionalProperties.containsKey(fileAttributeName)) {
            //
            Map<String, Object> fileNameObject = (Map<String, Object>) additionalProperties.get(fileAttributeName);

            if (fileNameObject.containsKey("@value")) {
              String fileName = fileNameObject.get("@value").toString();
              if (fileName != null || fileType != null) {
                Submission.Action.AddFiles.File sraFile = submissionObjectFactory
                    .createSubmissionActionAddFilesFile();
                sraFile.setFilePath(fileName);
                sraFile.setDataType(fileType);
                sraAddFiles.getFile().add(sraFile);

              }
            }
          }
        }
      }

      // Reference to BioSample ID

      if (sequenceReadArchive.getSampleID() != null && sequenceReadArchive.getSampleID().getValue() != null) {
        String bioSampleID = sequenceReadArchive.getSampleID().getValue();
        TypeFileAttributeRefId bioSampleAttributeRefId = submissionObjectFactory.createTypeFileAttributeRefId();
        bioSampleAttributeRefId.setName("BioSample");
        TypeRefId refId = ncbiCommonObjectFactory.createTypeRefId();
        TypeSPUID spuid = ncbiCommonObjectFactory.createTypeSPUID();
        spuid.setSpuidNamespace(CEDAR_NAMESPACE);
        spuid.setValue(bioSampleID);
        refId.setSPUID(spuid);
        bioSampleAttributeRefId.setRefId(refId);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(bioSampleAttributeRefId);
      }

      // Reference to BioProject ID

      if (!bioProjectID.isEmpty()) {
        TypeFileAttributeRefId bioProjectAttributeRefId = submissionObjectFactory.createTypeFileAttributeRefId();
        bioProjectAttributeRefId.setName("BioProject");
        TypeRefId refId = ncbiCommonObjectFactory.createTypeRefId();
        TypePrimaryId primaryId = ncbiCommonObjectFactory.createTypePrimaryId();
        primaryId.setDb("BioProject");
        primaryId.setValue(bioProjectID);
        refId.setPrimaryId(primaryId);
        bioProjectAttributeRefId.setRefId(refId);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(bioProjectAttributeRefId);
      }

      // Target Substrate

      String targetSubstrateValue = sequenceReadArchive.getTargetSubstrate().getValue();
      if (targetSubstrateValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("target_substrate");
        fileAttribute.setValue(targetSubstrateValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Target Substrate Quality

      String targetSubstrateQualityValue = sequenceReadArchive.getTargetSubstrateQuality().getValue();
      if (targetSubstrateQualityValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("target_substrate_quality");
        fileAttribute.setValue(targetSubstrateQualityValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Nucleic Acid Processing ID

      String libraryIdValue = sequenceReadArchive.getNucleicAcidProcessingID().getValue();
      if (libraryIdValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("library_ID");
        fileAttribute.setValue(libraryIdValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Template Amount

      String TemplateAmountValue = sequenceReadArchive.getTemplateAmount().getValue();
      if (TemplateAmountValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("template_amount");
        fileAttribute.setValue(TemplateAmountValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Library Generation Method

      String libraryGenerationMethodValue = sequenceReadArchive.getLibraryGenerationMethod().getValue();
      if (libraryGenerationMethodValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("library_generation_method");
        fileAttribute.setValue(libraryGenerationMethodValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Library Generation Protocol

      String libraryNameValue = sequenceReadArchive.getLibraryGenerationProtocol().getValue();
      if (libraryNameValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("design_description");
        fileAttribute.setValue(libraryNameValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Protocol ID

      String protocolIDValue = sequenceReadArchive.getProtocolIDs().getValue();
      if (protocolIDValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("protocol_ids");
        fileAttribute.setValue(protocolIDValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Target Locus for PCR

      String TargetLocusForPCRValue = sequenceReadArchive.getTargetLocusForPCR().getValue();
      if (TargetLocusForPCRValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("pcr_target_locus");
        fileAttribute.setValue(TargetLocusForPCRValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Forward PCR Primer Target Location

      String forwardPCRPrimerTargetLocationValue = sequenceReadArchive.getForwardPCRPrimerTargetLocation().getValue();
      if (forwardPCRPrimerTargetLocationValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("forward_pcr_primer_target_location");
        fileAttribute.setValue(forwardPCRPrimerTargetLocationValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Reverse PCR Primer Target Location

      String reversePCRPrimerTargetLocationValue = sequenceReadArchive.getReversePCRPrimerTargetLocation().getValue();
      if (reversePCRPrimerTargetLocationValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("reverse_pcr_primer_target_location");
        fileAttribute.setValue(reversePCRPrimerTargetLocationValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Complete Sequence

      String completeSequenceValue = sequenceReadArchive.getCompleteSequences().getValue();
      if (completeSequenceValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("complete_sequences");
        fileAttribute.setValue(completeSequenceValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Physical Linkage of Different Loci

      String physicalLinkageOfDifferentLociValue = sequenceReadArchive.getPhysicalLinkageOfDifferentLoci().getValue();
      if (physicalLinkageOfDifferentLociValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("physical_linkage");
        fileAttribute.setValue(physicalLinkageOfDifferentLociValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Total Reads Passing QC Filter

      String totalReadsPassingQCFilterValue = sequenceReadArchive.getTotalReadsPassingQCFilter().getValue();
      if (totalReadsPassingQCFilterValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("total_reads_passing_qc_filter");
        fileAttribute.setValue(totalReadsPassingQCFilterValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // library + sequencing strategy + layout + instrument model must be unique according to
      // https://www.ncbi.nlm.nih.gov/sra/docs/submitmeta/

      // Sequencing Platform

      String sequencingPlatformValue = sequenceReadArchive.getSequencingPlatform().getValue();
      if (sequencingPlatformValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("instrument_model");
        fileAttribute.setValue(sequencingPlatformValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Sequencing Read Lengths

      String readLengthsValue = sequenceReadArchive.getReadLengths().getValue();
      if (readLengthsValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("read_lengths");
        fileAttribute.setValue(readLengthsValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Sequencing Facility

      String sequencingFacilityValue = sequenceReadArchive.getSequencingFacility().getValue();
      if (sequencingFacilityValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("sequencing_facility");
        fileAttribute.setValue(sequencingFacilityValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Batch Number

      String batchNumberValue = sequenceReadArchive.getBatchNumber().getValue();
      if (batchNumberValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("batch_number");
        fileAttribute.setValue(batchNumberValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Date of Sequencing Run

      String dateOfSequencingRunValue = sequenceReadArchive.getDateOfSequencingRun().getValue();
      if (dateOfSequencingRunValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("sequencing_run_date");
        fileAttribute.setValue(dateOfSequencingRunValue);
        // TODO Possible date format issue
        //sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Sequencing Kit

      String sequencingKitValue = sequenceReadArchive.getSequencingKit().getValue();
      if (sequencingKitValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("sequencing_kit");
        fileAttribute.setValue(sequencingKitValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Library Strategy

      String libraryStrategyValue = sequenceReadArchive.getLibraryStrategy().getValue();
      if (libraryStrategyValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("library_strategy");
        fileAttribute.setValue(libraryStrategyValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Library Source

      String librarySourceValue = sequenceReadArchive.getLibrarySource().getValue();
      if (librarySourceValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("library_source");
        fileAttribute.setValue(librarySourceValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Library Selection

      String librarySelectionValue = sequenceReadArchive.getLibrarySelection().getValue();
      if (librarySelectionValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("library_selection");
        fileAttribute.setValue(librarySelectionValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // Library Layout

      String libraryLayoutValue = sequenceReadArchive.getLibraryLayout().getValue();
      if (libraryLayoutValue != null) {
        TypeFileAttribute fileAttribute = submissionObjectFactory.createTypeFileAttribute();
        fileAttribute.setName("library_layout");
        fileAttribute.setValue(libraryLayoutValue);
        sraAddFiles.getAttributeOrMetaOrAttributeRefId().add(fileAttribute);
      }

      // End of AIRR SRA Elements

      TypeSPUID sraSampleSpuid = ncbiCommonObjectFactory.createTypeSPUID();
      sraSampleSpuid.setSpuidNamespace(CEDAR_NAMESPACE);
      sraSampleSpuid.setValue(createNewSraId());

      TypeIdentifier sraIdentifier = ncbiCommonObjectFactory.createTypeIdentifier();
      sraIdentifier.setSPUID(sraSampleSpuid);

      sraAddFiles.setIdentifier(sraIdentifier);

      // Action
      Submission.Action sraAction = submissionObjectFactory.createSubmissionAction();
      sraAction.setAddFiles(sraAddFiles);

      ncbiSubmission.getAction().add(sraAction);

      sraIndex++; // increment the index counter
    }

    // Generate XML from the submission instance
    StringWriter writer = new StringWriter();
    JAXBContext ctx = JAXBContext.newInstance(Submission.class);
    Marshaller marshaller = ctx.createMarshaller();
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
    marshaller.marshal(ncbiSubmission, writer);

    return writer.toString();
  }

  private TypeOrganism createOrganism(String organismName) {
    TypeOrganism sampleOrganism = ncbiCommonObjectFactory.createTypeOrganism();
    sampleOrganism.setOrganismName(organismName);
    return sampleOrganism;
  }

  private TypeDescriptor createDescriptor(String title, String description) {
    TypeDescriptor sampleDescriptor = ncbiCommonObjectFactory.createTypeDescriptor();
    JAXBElement descriptionElement = new JAXBElement(new QName("p"), String.class, description);
    TypeBlock sampleDescription = ncbiCommonObjectFactory.createTypeBlock();
    sampleDescription.getPOrUlOrOl().add(descriptionElement);
    sampleDescriptor.setTitle(title);
    sampleDescriptor.setDescription(sampleDescription);
    return sampleDescriptor;
  }

  private TypeBioSampleIdentifier createBioSampleIdentifier(String bioSampleID) {
    TypeBioSampleIdentifier sampleID = bioSampleObjectFactory.createTypeBioSampleIdentifier();
    TypeBioSampleIdentifier.SPUID spuid = bioSampleObjectFactory.createTypeBioSampleIdentifierSPUID();
    spuid.setSpuidNamespace(CEDAR_NAMESPACE);
    spuid.setValue(bioSampleID);
    sampleID.getSPUID().add(spuid);
    return sampleID;
  }

  /*
   * Object construction for the submission <Description> section
   */
  private Submission.Description createSubmissionDescription(Submission submission,
                                                             BioProjectForAIRRNCBI cairrBioProject) throws
      DatatypeConfigurationException {
    Submission.Description submissionDescription = submissionObjectFactory.createSubmissionDescription();

    TypeContactInfo contactInfo = ncbiCommonObjectFactory.createTypeContactInfo();
    contactInfo.setEmail(cairrBioProject.getContactInformationDataCollection().getValue());

    TypeOrganization.Name organizationName = submissionObjectFactory.createTypeOrganizationName();
    organizationName.setValue(cairrBioProject.getLabName().getValue());

    TypeAccount contactSubmitter = submissionObjectFactory.createTypeAccount();
    contactSubmitter.setUserName(cairrBioProject.getContactInformationDataCollection().getValue());

    TypeOrganization contactOrganization = submissionObjectFactory.createTypeOrganization();
    contactOrganization.setType("lab");
    contactOrganization.setRole("owner"); // TODO
    contactOrganization.setName(organizationName);
    contactOrganization.getContact().add(contactInfo);

    submissionDescription.setComment("AIRR (myasthenia gravis) data to the NCBI using the CAIRR");
    submissionDescription.setSubmitter(contactSubmitter);
    submissionDescription.getOrganization().add(contactOrganization);

    Submission.Description.Hold submissionDescriptionHold = submissionObjectFactory.createSubmissionDescriptionHold();
    submissionDescriptionHold.setReleaseDate(createXMLGregorianCalendar("2019-03-03")); // TODO No place in AIRR
    submissionDescription.setHold(submissionDescriptionHold);

    return submissionDescription;
  }

  private TypeBioSample.Attributes createBioSampleAttributes(BioSampleForAIRRNCBI bioSample) {
    // Attributes
    TypeBioSample.Attributes bioSampleAttributes = bioSampleObjectFactory.createTypeBioSampleAttributes();

    // Subject ID
    String subjectIdValue = bioSample.getSubjectID().getValue();
    if (subjectIdValue != null && !subjectIdValue.isEmpty()) {
      bioSampleAttributes.getAttribute().add(createAttribute("SubjectId", subjectIdValue));
    }

    // Synthetic Library
    if (bioSample.getSyntheticLibrary() != null) {
      String syntheticLibraryValue = bioSample.getSyntheticLibrary().getValue();
      if (syntheticLibraryValue != null && !syntheticLibraryValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("SyntheticLibrary", syntheticLibraryValue));
      }
    }

    // Organism
    if (bioSample.getOrganism() != null && bioSample.getOrganism().getId() != null) {
      String organismValue = bioSample.getOrganism().getId().toString();
      if (organismValue != null && !organismValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("Organism", organismValue));
      }
    }

    // Sex
    if (bioSample.getSex() != null && bioSample.getSex().getId() != null) {
      //String sexValue = bioSample.getSex().getId().toString();
      String sexValue = bioSample.getSex().getRdfsLabel().toLowerCase();
      if (sexValue != null && !sexValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("Sex", sexValue));
      }
    }

    // Age
    if (bioSample.getAge() != null) {
      String ageValue = bioSample.getAge().getValue();
      if (ageValue != null && !ageValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("Age", ageValue));
      }
    }

    // Age Event
    if (bioSample.getAgeEvent() != null) {
      String ageEventValue = bioSample.getAgeEvent().getValue();
      if (ageEventValue != null && !ageEventValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("AgeEvent", ageEventValue));
      }
    }

    // Ancestry Population
    if (bioSample.getAncestryPopulation() != null) {
      String ancestryPopulationValue = bioSample.getAncestryPopulation().getValue();
      if (ancestryPopulationValue != null && !ancestryPopulationValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("AncestryPopulation", ancestryPopulationValue));
      }
    }

    // Ethnicity
    if (bioSample.getEthnicity() != null) {
      String ethnicityValue = bioSample.getEthnicity().getValue();
      if (ethnicityValue != null && !ethnicityValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("Ethnicity", ethnicityValue));
      }
    }

    // Race
    if (bioSample.getRace() != null) {
      String raceValue = bioSample.getRace().getValue();
      if (raceValue != null && !raceValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("Race", raceValue));
      }
    }

    // Strain Name
    if (bioSample.getStrainName() != null) {
      String strainNameValue = bioSample.getStrainName().getValue();
      if (strainNameValue != null && !strainNameValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("StrainName", strainNameValue));
      }
    }

    // Relation to other Subject
    if (bioSample.getRelatedSubjects() != null) {
      String relationToOtherSubjectValue = bioSample.getRelatedSubjects().getValue();
      if (relationToOtherSubjectValue != null && !relationToOtherSubjectValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("RelationToOtherSubject", relationToOtherSubjectValue));
      }
    }

    // Relation Type
    if (bioSample.getRelationType() != null) {
      String relationTypeValue = bioSample.getRelationType().getValue();
      if (relationTypeValue != null && !relationTypeValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("RelationType", relationTypeValue));
      }
    }

    // Projected Release Date
    // TODO Hard code for moment - need to fix front end to generate dates in correct format
    if (bioSample.getProjectedReleaseDate() != null) {
      String projectedReleaseDateValue = bioSample.getProjectedReleaseDate().getValue();
      if (projectedReleaseDateValue != null && !projectedReleaseDateValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("ProjectedReleaseDate", "2019-03-03"));
      }
    }

    // Isolate
    if (bioSample.getCellIsolation() != null) {
      String isolateValue = bioSample.getCellIsolation().getValue();
      if (isolateValue != null && !isolateValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("Isolate", isolateValue));
      }
    }

    // Diagnosis
    if (bioSample.getDiagnosis() != null) {
      String diagnosisValue = bioSample.getDiagnosis().toString();
      if (diagnosisValue != null && !diagnosisValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("Diagnosis", diagnosisValue));
      }
    }

    // Study Group Description
    if (bioSample.getStudyGroupDescription() != null) {
      String studyGroupDescriptionValue = bioSample.getStudyGroupDescription().getValue();
      if (studyGroupDescriptionValue != null && !studyGroupDescriptionValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("StudyGroupDescription", studyGroupDescriptionValue));
      }
    }

    // Length of Disease
    if (bioSample.getLengthOfDisease() != null) {
      String lengthOfDiseaseValue = bioSample.getLengthOfDisease().getValue();
      if (lengthOfDiseaseValue != null && !lengthOfDiseaseValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("LengthOfDisease", lengthOfDiseaseValue));
      }
    }

    // Disease Stage
    if (bioSample.getDiseaseStage() != null) {
      String diseaseStageValue = bioSample.getDiseaseStage().getValue();
      if (diseaseStageValue != null && !diseaseStageValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("DiseaseStage", diseaseStageValue));
      }
    }

    // Prior Therapies For Primary Disease Under Study
    if (bioSample.getPriorTherapiesForPrimaryDiseaseUnderStudy() != null) {
      String priorTherapiesForPrimaryDiseaseUnderStudyValue = bioSample.getPriorTherapiesForPrimaryDiseaseUnderStudy()
          .getValue();
      if (priorTherapiesForPrimaryDiseaseUnderStudyValue != null && !priorTherapiesForPrimaryDiseaseUnderStudyValue
          .isEmpty()) {
        bioSampleAttributes.getAttribute().add(
            createAttribute("PriorTherapiesForPrimaryDiseaseUnderStudy",
                priorTherapiesForPrimaryDiseaseUnderStudyValue));
      }
    }

    // Immunogen
    if (bioSample.getImmunogen() != null) {
      String immunogenValue = bioSample.getImmunogen().getValue();
      if (immunogenValue != null && !immunogenValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("Immunogen", immunogenValue));
      }
    }

    // Intervention Definition
    if (bioSample.getInterventionDefinition() != null) {
      String interventionDefinitionValue = bioSample.getInterventionDefinition().getValue();
      if (interventionDefinitionValue != null) {
        bioSampleAttributes.getAttribute().add(createAttribute("InterventionDefinition", interventionDefinitionValue));
      }
    }

    // Other Relevant Medical History
    if (bioSample.getOtherRelevantMedicalHistory() != null) {
      String otherRelevantMedicalHistoryValue = bioSample.getOtherRelevantMedicalHistory().getValue();
      if (otherRelevantMedicalHistoryValue != null) {
        bioSampleAttributes.getAttribute()
            .add(createAttribute("OtherRelevantMedicalHistory", otherRelevantMedicalHistoryValue));
      }
    }

    // Sample Type
    if (bioSample.getSampleType() != null) {
      String sampleTypeValue = bioSample.getSampleType().getValue();
      if (sampleTypeValue != null && !sampleTypeValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("SampleType", sampleTypeValue));
      }
    }

    // Tissue
    if (bioSample.getTissue() != null && bioSample.getTissue().getId() != null) {
      String tissueValue = bioSample.getTissue().getId().toString();
      if (tissueValue != null && !tissueValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("Tissue", tissueValue));
      }
    }

    // Anatomic Site
    if (bioSample.getAnatomicSite() != null) {
      String anatomicSiteValue = bioSample.getAnatomicSite().getValue();
      if (anatomicSiteValue != null) {
        bioSampleAttributes.getAttribute().add(createAttribute("AnatomicSite", anatomicSiteValue));
      }
    }

    // Disease State of Sample
    if (bioSample.getDiseaseStateOfSample() != null) {
      String diseaseStateOfSampleValue = bioSample.getDiseaseStateOfSample().getValue();
      if (diseaseStateOfSampleValue != null && !diseaseStateOfSampleValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("DiseaseStateOfSample", diseaseStateOfSampleValue));
      }
    }

    // Sample Collection Time
    if (bioSample.getSampleCollectionTime() != null) {
      String sampleCollectionTimeValue = bioSample.getSampleCollectionTime().getValue();
      if (sampleCollectionTimeValue != null && !sampleCollectionTimeValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("SampleCollectionTime", sampleCollectionTimeValue));
      }
    }

    // Collection Time Event T01
    if (bioSample.getCollectionTimeEvent() != null) {
      String collectionTimeEventT01Value = bioSample.getCollectionTimeEvent().getValue();
      if (collectionTimeEventT01Value != null && !collectionTimeEventT01Value.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("CollectionTimeEventT01", collectionTimeEventT01Value));
      }
    }

    // Biomaterial Provider
    if (bioSample.getBiomaterialProvider() != null) {
      String biomaterialProviderValue = bioSample.getBiomaterialProvider().getValue();
      if (biomaterialProviderValue != null && !biomaterialProviderValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("BiomaterialProvider", biomaterialProviderValue));
      }
    }

    // Tissue Processing
    if (bioSample.getTissueProcessing() != null) {
      String tissueProcessingValue = bioSample.getTissueProcessing().getValue();
      if (tissueProcessingValue != null && !tissueProcessingValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("TissueProcessing", tissueProcessingValue));
      }
    }

    // Cell Subset
    if (bioSample.getCellSubsetPhenotype() != null) {
      String cellSubsetValue = bioSample.getCellSubsetPhenotype().getValue();
      if (cellSubsetValue != null && !cellSubsetValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("CellSubset", cellSubsetValue));
      }
    }

    // Cell Subset Phenotype
    if (bioSample.getCellSubsetPhenotype() != null) {
      String cellSubsetPhenotypeValue = bioSample.getCellSubsetPhenotype().getValue();
      if (cellSubsetPhenotypeValue != null && !cellSubsetPhenotypeValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("CellSubsetPhenotype", cellSubsetPhenotypeValue));
      }
    }

    // Single-cell Sort
    if (bioSample.getSingleCellSort() != null) {
      String singleCellSortValue = bioSample.getSingleCellSort().getValue();
      if (singleCellSortValue != null && !singleCellSortValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("SingleCellSort", singleCellSortValue));
      }
    }

    // Number of Cells in Experiment
    if (bioSample.getNumberOfCellsInExperiment() != null) {
      String numberOfCellsInExperimentValue = bioSample.getNumberOfCellsInExperiment().getValue();
      if (numberOfCellsInExperimentValue != null && !numberOfCellsInExperimentValue.isEmpty()) {
        bioSampleAttributes.getAttribute()
            .add(createAttribute("NumberOfCellsInExperiment", numberOfCellsInExperimentValue));
      }
    }

    // Number of Cells per Sequencing Reaction1
    if (bioSample.getNumberOfCellsPerSequencingReaction() != null) {
      String numberOfCellsPerSequencingReactionValue = bioSample.getNumberOfCellsPerSequencingReaction().getValue();
      if (numberOfCellsPerSequencingReactionValue != null && !numberOfCellsPerSequencingReactionValue.isEmpty()) {
        bioSampleAttributes.getAttribute()
            .add(createAttribute("NumberOfCellsPerSequencingReaction", numberOfCellsPerSequencingReactionValue));
      }
    }

    // Cell Storage
    if (bioSample.getCellStorage() != null) {
      String cellStorageValue = bioSample.getCellStorage().getValue();
      if (cellStorageValue != null && !cellStorageValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("CellSubsetPhenotype", cellStorageValue));
      }
    }

    // Cell Quality
    if (bioSample.getCellQuality() != null) {
      String cellQualityValue = bioSample.getCellQuality().getValue();
      if (cellQualityValue != null && !cellQualityValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("CellQuality", cellQualityValue));
      }
    }

    // Cell Isolation
    if (bioSample.getCellIsolation() != null) {
      String cellIsolationValue = bioSample.getCellIsolation().getValue();
      if (cellIsolationValue != null && !cellIsolationValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("CellIsolationValue", cellIsolationValue));
      }
    }

    // Processing Protocol
    if (bioSample.getCellProcessingProtocol() != null) {
      String processingProtocolValue = bioSample.getCellProcessingProtocol().getValue();
      if (processingProtocolValue != null && !processingProtocolValue.isEmpty()) {
        bioSampleAttributes.getAttribute().add(createAttribute("CellProcessingProtocol", processingProtocolValue));
      }
    }

    // Custom CEDAR attribute
    bioSampleAttributes.getAttribute().add(createAttribute("SubmissionTool", "CEDAR"));

    return bioSampleAttributes;
  }

  private XMLGregorianCalendar createXMLGregorianCalendar(String date) throws DatatypeConfigurationException {
    DatatypeFactory datatypeFactory = DatatypeFactory.newInstance();
    GregorianCalendar gc = new GregorianCalendar();

    return datatypeFactory.newXMLGregorianCalendar(gc);
  }

  private String createNewSraId() {
    String id = "SRA-" + UUID.randomUUID();
    sraIds.add(id);
    return id;
  }

  private String createNewActionId() {
    String id = "Action-" + UUID.randomUUID();
    sraIds.add(id);
    return id;
  }

  private TypeAttribute createAttribute(String attributeName, String attributeValue) {
    TypeAttribute attribute = bioSampleObjectFactory.createTypeAttribute();
    attribute.setAttributeName(attributeName);
    attribute.setValue(attributeValue);

    return attribute;
  }
}
