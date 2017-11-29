package org.metadatacenter.submission.resources;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.metadatacenter.cedar.util.dw.CedarMicroserviceResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.submission.AMIA2016DemoBioSampleTemplate;
import org.metadatacenter.submission.ncbi.AMIA2016DemoBioSampleTemplate2BioSampleConverter;
import org.metadatacenter.submission.ncbi.BioSampleValidator;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;
import static org.metadatacenter.util.json.JsonMapper.MAPPER;

@Path("/command")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AMIA2016DemoBioSampleServerResource extends CedarMicroserviceResource {
  private final BioSampleValidator bioSampleValidator;

  private final AMIA2016DemoBioSampleTemplate2BioSampleConverter amia2016DemoBioSampleTemplate2BioSampleConverter;

  public AMIA2016DemoBioSampleServerResource(CedarConfig cedarConfig) {
    super(cedarConfig);
    this.bioSampleValidator = new BioSampleValidator();
    this.amia2016DemoBioSampleTemplate2BioSampleConverter = new AMIA2016DemoBioSampleTemplate2BioSampleConverter();
  }

  /**
   * The {@link AMIA2016DemoBioSampleTemplate} class is generated by jsonschema2pojo from the
   * AMIA2016DemoBioSampleTemplate.json JSON Schema file in the resources directory. This file
   * contains the CEDAR template that defines the example BioSample submission used for the 2016 AMIA demo.
   */
  @POST
  @Timed
  @Path("/validate-biosample")
  public Response validate() throws CedarException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);
    //c.must(c.user()).have(CedarPermission.POST_SUBMISSION);

    String payload = c.request().getRequestBody().asJsonString();

    try {

      JsonNode instance = prepareForValidation(payload);

      AMIA2016DemoBioSampleTemplate amia2016BioSampleInstance = MAPPER.readValue(instance.toString(),
          AMIA2016DemoBioSampleTemplate.class);

      String bioSampleSubmissionXML = this.amia2016DemoBioSampleTemplate2BioSampleConverter
          .generateBioSampleSubmissionXMLFromAMIA2016DemoBioSampleTemplateInstance(amia2016BioSampleInstance);

      return Response.ok(this.bioSampleValidator.validateBioSampleSubmission(bioSampleSubmissionXML)).build();

    } catch (JAXBException | DatatypeConfigurationException | IOException e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  /* Utilities */

  /**
   * Prepares an instance to be processed by the BioSample validator. We need to do this because in some BioSample
   * instances the name and/or value fields in the optionalAttribute array contain controlled terms (@id field
   * instead of (at)value). These instances are not valid according to the BioSample template but we don't want to
   * remove them because we need them to feed our Value Recommender with as much as controlled terms as possible.
   * For controlled fields, this method generates an (at)value field using rdfs:label.
   */
  public JsonNode prepareForValidation(String instanceString) throws IOException {

    final String OPT_ATT_FIELD = "optionalAttribute";
    final String VALUE_FIELD = "@value";
    final String VALUE_LABEL_FIELD = "rdfs:label";
    final String ATT_NAME_FIELD = "name";
    final String ATT_VALUE_FIELD = "value";

    JsonNode instance = MAPPER.readTree(instanceString);

    ArrayNode newOptionalAttributes = MAPPER.createArrayNode();

    if (instance.has(OPT_ATT_FIELD)) {
      for (JsonNode attribute : instance.get(OPT_ATT_FIELD)) {
        Iterator<Map.Entry<String, JsonNode>> attFields = attribute.fields();
        ObjectNode newAttribute = MAPPER.createObjectNode();
        // Iterates over the attribute name and value fields
        while (attFields.hasNext()) {
          Map.Entry<String, JsonNode> entry = attFields.next();
          if (entry.getKey().equals(ATT_NAME_FIELD) || entry.getKey().equals(ATT_VALUE_FIELD)) {
            ObjectNode newAttributeField = MAPPER.createObjectNode();
            if (entry.getValue().has(VALUE_LABEL_FIELD)) {
              // Store rdfs:label into @value
              newAttributeField.set(VALUE_FIELD, entry.getValue().get(VALUE_LABEL_FIELD));
            } else {
              newAttributeField = entry.getValue().deepCopy();
            }
            newAttribute.set(entry.getKey(), newAttributeField);
          }
        }
        newOptionalAttributes.add(newAttribute);
      }
      ((ObjectNode) instance).replace(OPT_ATT_FIELD, newOptionalAttributes);
    }
    return instance;
  }

}