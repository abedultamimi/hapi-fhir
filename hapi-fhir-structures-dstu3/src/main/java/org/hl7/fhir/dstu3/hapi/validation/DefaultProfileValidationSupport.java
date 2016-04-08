package org.hl7.fhir.dstu3.hapi.validation;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.Charsets;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.dstu3.model.CodeSystem;
import org.hl7.fhir.dstu3.model.DomainResource;
import org.hl7.fhir.dstu3.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.dstu3.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.dstu3.model.StructureDefinition;
import org.hl7.fhir.dstu3.model.ValueSet;
import org.hl7.fhir.dstu3.model.ValueSet.ConceptReferenceComponent;
import org.hl7.fhir.dstu3.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.dstu3.model.ValueSet.ValueSetExpansionComponent;
import org.hl7.fhir.instance.model.api.IBaseResource;

import ca.uhn.fhir.context.FhirContext;

public class DefaultProfileValidationSupport implements IValidationSupport {

	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(DefaultProfileValidationSupport.class);

	private Map<String, CodeSystem> myCodeSystems;
	private Map<String, StructureDefinition> myStructureDefinitions;
	private Map<String, ValueSet> myValueSets;

	@Override
	public ValueSetExpansionComponent expandValueSet(FhirContext theContext, ConceptSetComponent theInclude) {
		ValueSetExpansionComponent retVal = new ValueSetExpansionComponent();

		Set<String> wantCodes = new HashSet<String>();
		for (ConceptReferenceComponent next : theInclude.getConcept()) {
			wantCodes.add(next.getCode());
		}

		CodeSystem system = fetchCodeSystem(theContext, theInclude.getSystem());
		for (ConceptDefinitionComponent next : system.getConcept()) {
			if (wantCodes.isEmpty() || wantCodes.contains(next.getCode())) {
				retVal.addContains().setSystem(theInclude.getSystem()).setCode(next.getCode()).setDisplay(next.getDisplay());
			}
		}

		return retVal;
	}

	@Override
	public CodeSystem fetchCodeSystem(FhirContext theContext, String theSystem) {
		return (CodeSystem) fetchCodeSystemOrValueSet(theContext, theSystem, true);
	}

	ValueSet fetchValueSet(FhirContext theContext, String theSystem) {
		return (ValueSet) fetchCodeSystemOrValueSet(theContext, theSystem, false);
	}

	private DomainResource fetchCodeSystemOrValueSet(FhirContext theContext, String theSystem, boolean codeSystem) {
		Map<String, CodeSystem> codeSystems = myCodeSystems;
		Map<String, ValueSet> valueSets = myValueSets;
		if (codeSystems == null) {
			codeSystems = new HashMap<String, CodeSystem>();
			valueSets = new HashMap<String, ValueSet>();

			loadCodeSystems(theContext, codeSystems, valueSets, "/org/hl7/fhir/instance/model/dstu3/valueset/valuesets.xml");
			loadCodeSystems(theContext, codeSystems, valueSets, "/org/hl7/fhir/instance/model/dstu3/valueset/v2-tables.xml");
			loadCodeSystems(theContext, codeSystems, valueSets, "/org/hl7/fhir/instance/model/dstu3/valueset/v3-codesystems.xml");

			myCodeSystems = codeSystems;
			myValueSets = valueSets;
		}

		if (codeSystem) {
			return codeSystems.get(theSystem);
		} else {
			return valueSets.get(theSystem);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends IBaseResource> T fetchResource(FhirContext theContext, Class<T> theClass, String theUri) {
		if (theUri.startsWith("http://hl7.org/fhir/StructureDefinition/")) {
			return (T) fetchStructureDefinition(theContext, theUri);
		}
		if (theUri.startsWith("http://hl7.org/fhir/ValueSet/")) {
			return (T) fetchValueSet(theContext, theUri);
		}
		//		if (theUri.startsWith("http://hl7.org/fhir/ValueSet/")) {
		//			Map<String, ValueSet> defaultValueSets = myDefaultValueSets;
		//			if (defaultValueSets == null) {
		//				String path = theContext.getVersion().getPathToSchemaDefinitions().replace("/schema", "/valueset") + "/valuesets.xml";
		//				InputStream valuesetText = DefaultProfileValidationSupport.class.getResourceAsStream(path);
		//				if (valuesetText == null) {
		//					return null;
		//				}
		//				InputStreamReader reader;
		//				try {
		//					reader = new InputStreamReader(valuesetText, "UTF-8");
		//				} catch (UnsupportedEncodingException e) {
		//					// Shouldn't happen!
		//					throw new InternalErrorException("UTF-8 encoding not supported on this platform", e);
		//				}
		//
		//				defaultValueSets = new HashMap<String, ValueSet>();
		//
		//				Bundle bundle = theContext.newXmlParser().parseResource(Bundle.class, reader);
		//				for (BundleEntryComponent next : bundle.getEntry()) {
		//					IdType nextId = new IdType(next.getFullUrl());
		//					if (nextId.isEmpty() || !nextId.getValue().startsWith("http://hl7.org/fhir/ValueSet/")) {
		//						continue;
		//					}
		//					defaultValueSets.put(nextId.toVersionless().getValue(), (ValueSet) next.getResource());
		//				}
		//
		//				myDefaultValueSets = defaultValueSets;
		//			}
		//
		//			return (T) defaultValueSets.get(theUri);
		//		}

		return null;
	}

	@Override
	public boolean isCodeSystemSupported(FhirContext theContext, String theSystem) {
		CodeSystem cs = fetchCodeSystem(theContext, theSystem);
		return cs != null;
	}

	private void loadCodeSystems(FhirContext theContext, Map<String, CodeSystem> theCodeSystems, Map<String, ValueSet> theValueSets, String theClasspath) {
		ourLog.info("Loading CodeSystem/ValueSet from classpath: {}", theClasspath);
		InputStream valuesetText = DefaultProfileValidationSupport.class.getResourceAsStream(theClasspath);
		if (valuesetText != null) {
			InputStreamReader reader = new InputStreamReader(valuesetText, Charsets.UTF_8);

			Bundle bundle = theContext.newXmlParser().parseResource(Bundle.class, reader);
			for (BundleEntryComponent next : bundle.getEntry()) {
				if (next.getResource() instanceof CodeSystem) {
					CodeSystem nextValueSet = (CodeSystem) next.getResource();
					nextValueSet.getText().setDivAsString("");
					String system = nextValueSet.getUrl();
					if (isNotBlank(system)) {
						theCodeSystems.put(system, nextValueSet);
					}
				} else if (next.getResource() instanceof ValueSet) {
					ValueSet nextValueSet = (ValueSet) next.getResource();
					nextValueSet.getText().setDivAsString("");
					String system = nextValueSet.getUrl();
					if (isNotBlank(system)) {
						theValueSets.put(system, nextValueSet);
					}
				}
			}
		} else {
			ourLog.warn("Unable to load resource: {}", theClasspath);
		}
	}

	private void loadStructureDefinitions(FhirContext theContext, Map<String, StructureDefinition> theCodeSystems, String theClasspath) {
		ourLog.info("Loading structure definitions from classpath: {}", theClasspath);
		InputStream valuesetText = DefaultProfileValidationSupport.class.getResourceAsStream(theClasspath);
		if (valuesetText != null) {
			InputStreamReader reader = new InputStreamReader(valuesetText, Charsets.UTF_8);

			Bundle bundle = theContext.newXmlParser().parseResource(Bundle.class, reader);
			for (BundleEntryComponent next : bundle.getEntry()) {
				if (next.getResource() instanceof StructureDefinition) {
					StructureDefinition nextSd = (StructureDefinition) next.getResource();
					nextSd.getText().setDivAsString("");
					String system = nextSd.getUrl();
					if (isNotBlank(system)) {
						theCodeSystems.put(system, nextSd);
					}
				}
			}
		} else {
			ourLog.warn("Unable to load resource: {}", theClasspath);
		}
	}

	@Override
	public CodeValidationResult validateCode(FhirContext theContext, String theCodeSystem, String theCode, String theDisplay) {
		CodeSystem cs = fetchCodeSystem(theContext, theCodeSystem);
		if (cs != null) {
			for (ConceptDefinitionComponent next : cs.getConcept()) {
				if (next.getCode().equals(theCode)) {
					return new CodeValidationResult(next);
				}
			}
		}

		return new CodeValidationResult(IssueSeverity.INFORMATION, "Unknown code: " + theCodeSystem + " / " + theCode);
	}

	public void flush() {
		myCodeSystems = null;
		myStructureDefinitions = null;
	}

	@Override
	public StructureDefinition fetchStructureDefinition(FhirContext theContext, String theUrl) {
		Map<String, StructureDefinition> structureDefinitions = myStructureDefinitions;
		if (structureDefinitions == null) {
			structureDefinitions = new HashMap<String, StructureDefinition>();

			loadStructureDefinitions(theContext, structureDefinitions, "/org/hl7/fhir/instance/model/dstu3/profile/profiles-resources.xml");
			loadStructureDefinitions(theContext, structureDefinitions, "/org/hl7/fhir/instance/model/dstu3/profile/profiles-types.xml");
			loadStructureDefinitions(theContext, structureDefinitions, "/org/hl7/fhir/instance/model/dstu3/profile/profiles-others.xml");

			myStructureDefinitions = structureDefinitions;
		}

		return structureDefinitions.get(theUrl);
	}

}