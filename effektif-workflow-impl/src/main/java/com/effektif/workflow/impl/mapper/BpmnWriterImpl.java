/* Copyright (c) 2014, Effektif GmbH.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. */
package com.effektif.workflow.impl.mapper;

import static com.effektif.workflow.impl.bpmn.Bpmn.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Pattern;

import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.effektif.workflow.api.mapper.BpmnElement;
import com.effektif.workflow.api.mapper.BpmnWriter;
import com.effektif.workflow.api.mapper.XmlElement;
import com.effektif.workflow.api.model.Id;
import com.effektif.workflow.api.workflow.Activity;
import com.effektif.workflow.api.workflow.Binding;
import com.effektif.workflow.api.workflow.Scope;
import com.effektif.workflow.api.workflow.Transition;
import com.effektif.workflow.api.workflow.Workflow;
import com.effektif.workflow.impl.bpmn.xml.XmlWriter;


/**
 * @author Tom Baeyens
 */
public class BpmnWriterImpl implements BpmnWriter {
  
  public static DateTimeFormatter DATE_FORMAT = ISODateTimeFormat.dateTime();

  protected Mappings mappings;
  protected String bpmnPrefix;
  protected String effektifPrefix;
//  protected DataTypeService dataTypeService;
//  protected ActivityTypeService activityTypeService;

  /** stack of the current scopes */
  protected Stack<Scope> scopeStack = new Stack();
  /** current scope (==scopeStack.peek()) */
  protected Scope scope;
  
  /** stack of the current xml elements */
  protected Stack<XmlElement> xmlStack = new Stack();
  /** current xml element */
  protected XmlElement xml;
  
  public BpmnWriterImpl(Mappings mappings) {
    this.mappings = mappings;
  }

  public void toBpmnStream(Workflow workflow, OutputStream out) {
    XmlElement bpmnDefinitions = writeDefinitions(workflow);
    XmlWriter xmlWriter = new XmlWriter(out, "UTF-8");
    xmlWriter.writeDocument(bpmnDefinitions);
    xmlWriter.flush();
  }

  public String toBpmnString(Workflow workflow) {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    toBpmnStream(workflow, stream);
    return stream.toString();
  }

  protected void startElementBpmn(String localpart, Object source) {
    startElementBpmn(localpart, source, false);
  }

  protected void startElementBpmn(String localpart, Object source, boolean asFirst) {
    if (source==null) {
      startElementBpmn(localpart, asFirst);
    } else if (source instanceof XmlElement) {
      startElement((XmlElement) source, asFirst);
    } else {
      throw new RuntimeException("Unknown BPMN source: "+source);
    }
  }

  @Override
  public void startElementBpmn(String localPart) {
    startElementBpmn(localPart, null);
  }

  @Override
  public void startElementBpmn(String localPart, Integer index) {
    startElement(xml.createElement(BPMN_URI, localPart), index);
  }

  @Override
  public void startElementEffektif(String localPart) {
    startElementEffektif(localPart, null);
  }

  @Override
  public void startElementEffektif(String localPart, Integer index) {
    startElement(xml.createElement(EFFEKTIF_URI, localPart), index);
  }

  protected void startElement(XmlElement nestedXml, Integer index) {
    if (xml!=null) {
      xmlStack.push(xml);
      xml.addElement(nestedXml, index);
    }
    xml = nestedXml;
  }
  
  @Override
  public void endElement() {
    xml = xmlStack.pop();
  }
  
  @Override
  public void startExtensionElements() {
    startElementBpmn("extensionElements");
  }

  @Override
  public void endExtensionElements() {
    endElement();
  }
  
  public void startScope(Scope nestedScope) {
    if (this.scope!=null) {
      scopeStack.push(this.scope);
    }
    this.scope = nestedScope;
  }
  
  public void endScope() {
    this.scope = scopeStack.pop();
  }

  protected void initializeNamespacePrefixes() {
    if (xml.namespaces!=null) {
      for (String prefix : xml.namespaces.keySet()) {
        String uri = xml.namespaces.get(prefix);
        if (BPMN_URI.equals(uri)) {
          bpmnPrefix = prefix;
        } else if (EFFEKTIF_URI.equals(uri)) {
          effektifPrefix = prefix;
        }
      }
    }
    if (bpmnPrefix==null) {
      bpmnPrefix = "";
      xml.addNamespace(BPMN_URI, bpmnPrefix);
    } if (effektifPrefix==null) {
      effektifPrefix = "e";
      xml.addNamespace(EFFEKTIF_URI, effektifPrefix);
    }
  }
  
  @Override
  public String getQNameBpmn(String localPart) {
    return bpmnPrefix==null || "".equals(bpmnPrefix) ? localPart : bpmnPrefix+":"+localPart;
  }

  @Override
  public String getQNameEffektif(String localPart) {
    return effektifPrefix==null || "".equals(effektifPrefix) ? localPart : effektifPrefix+":"+localPart;
  }
  
  protected XmlElement writeDefinitions(Workflow workflow) {
    startElementBpmn("definitions", workflow.getProperty(KEY_DEFINITIONS));
    initializeNamespacePrefixes();
    writeWorkflow(workflow);
    return xml;
  }

  protected void writeWorkflow(Workflow workflow) {
    startScope(workflow);
    // let's add the process we write as the first process element inside the definitions
    startElementBpmn("process", workflow.getBpmn(), true);
    writeDocumentation(workflow.getDescription());
    if (workflow.getSourceWorkflowId()==null && workflow.getId()!=null) {
      workflow.setSourceWorkflowId(workflow.getId().getInternal());
    }
    workflow.writeBpmn(this);
    endElement();
  }
  
  public void writeScope() {
    writeActivities(scope.getActivities());
    writeTransitions(scope.getTransitions());
  }

  protected void writeActivities(List<Activity> activities) {
    if (activities!=null) {
      // We loop backwards and then add each activity as the first
      // This way all the parsed activities will be serialized first
      // before the unknown elements and the parsed elements will
      // appear in the order as they were parsed.
      for (int i=activities.size()-1; i>=0; i--) {
        Activity activity = activities.get(i);
        startScope(activity);
        BpmnTypeMapping bpmnTypeMapping = mappings.getBpmnTypeMapping(activity.getClass());
        if (bpmnTypeMapping==null) {
          throw new RuntimeException("Register "+activity.getClass()+" in class "+Mappings.class.getName()+" with method registerSubClass and ensure annotation "+BpmnElement.class+" is set");
        }
        startElementBpmn(bpmnTypeMapping.getBpmnElementName(), activity.getBpmn(), true);
        Map<String, String> bpmnTypeAttributes = bpmnTypeMapping.getBpmnTypeAttributes();
        if (bpmnTypeAttributes!=null) {
          for (String attribute: bpmnTypeAttributes.keySet()) {
            String value = bpmnTypeAttributes.get(attribute);
            xml.addAttribute(getQNameEffektif(attribute), value);
          }
        }
        activity.writeBpmn(this);
        endElement();
        endScope();
      }
    }
  }
  
  protected void writeTransitions(List<Transition> transitions) {
    if (transitions!=null) {
      // We loop backwards and then add each transition as the first
      // This way all the parsed activities will be serialized first
      // before the unknown elements and the parsed elements will
      // appear in the order as they were parsed.
      for (int i=transitions.size()-1; i>=0; i--) {
        Transition transition = transitions.get(i);
        startElementBpmn("sequenceFlow", transition.getBpmn(), true);
        transition.writeBpmn(this);
        endElement();
      }
    }
  }

  /** Writes binding values as extension elements with the given local name and attribute name,
   * e.g. <e:assignee value="42"/> or <e:assignee expression="v1.fullName"/>. */
  @Override
  public <T> void writeBinding(String localPart, Binding<T> binding) {
    if (binding!=null) {
      startElementEffektif(localPart);
      T value = binding.getValue();
      if (value!=null) {
        writeStringAttributeEffektif("value", value.toString());
      }
      if (binding.getExpression()!=null) {
        writeStringAttributeEffektif("expression", binding.getExpression());
      }
      endElement();
    }
  }
  
  @Override
  public <T> void writeBindings(String fieldName, List<Binding<T>> bindings) {
    if (bindings!=null) {
      for (Binding<T> binding: bindings) {
        writeBinding(fieldName, binding);
      }
    }
  }

  /** Writes the given documentation string as a BPMN <code>documentation</code> element. */
  public void writeDocumentation(String documentation) {
    if (documentation != null && !documentation.isEmpty()) {
      startElementBpmn("documentation", true);
      xml.addText(documentation);
      endElement();
    }
  }

  public void writeStringAttributeBpmn(String localPart, String value) {
    if (value!=null) {
      xml.addAttribute(getQNameBpmn(localPart), value);
    } 
  }
  
  public void writeStringAttributeEffektif(String localPart, String value) {
    if (value!=null) {
      xml.addAttribute(getQNameEffektif(localPart), value);
    } 
  }
  
  public void writeIdAttributeBpmn(String localPart, Id value) {
    if (value!=null) {
      xml.addAttribute(getQNameBpmn(localPart), value.getInternal());
    } 
  }
  
  public void writeIdAttributeEffektif(String localPart, Id value) {
    if (value!=null) {
      xml.addAttribute(getQNameEffektif(localPart), value.getInternal());
    } 
  }

  public void writeDateAttributeBpmn(String localPart, LocalDateTime value) {
    if (value!=null) {
      xml.addAttribute(getQNameBpmn(localPart), DATE_FORMAT.print(value));
    } 
  }
  
  public void writeDateAttributeEffektif(String localPart, LocalDateTime value) {
    if (value!=null) {
      xml.addAttribute(getQNameEffektif(localPart), DATE_FORMAT.print(value));
    } 
  }

  @Override
  public void writeTextBpmn(String localPart, String value) {
    writeText(BPMN_URI, localPart, value);
  }
  
  @Override
  public void writeTextEffektif(String localPart, String value) {
    writeText(EFFEKTIF_URI, localPart, value);
  }
  
  protected void writeText(String namespaceUri, String localPart, String value) {
    if (value!=null) {
      if (containsCdataChars(value)) {
        value = "<![CDATA[" + value + "]]>";
      }
      xml.createElement(namespaceUri, localPart).addText(value);
    }
  }

  static Pattern nonCdataPattern = Pattern.compile("[a-zA-Z0-9_\\- ]"); 
  protected boolean containsCdataChars(String value) {
    return !nonCdataPattern.matcher(value).matches();
  }

////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////


//  /**
//   * Writes binding values as extension elements with the given local name and attribute name,
//   * e.g. <effektif:assignee userId="42"/>.
//   */
//  public void writeBindings(XmlElement xml, String elementName, List<Binding> bindings, Type type) {
//    if (bindings==null) {
//      return;
//    }
//    for (Binding nestedBinding : bindings) {
//      writeBinding(xml, elementName, nestedBinding, type);
//    }
//  }
//
//  /**
//   * Writes an extension element with the string value as an attribute.
//   */
//  public void writeStringValue(String elementName, String value) {
//    if (value != null && !value.isEmpty()) {
//      XmlElement newElement = new XmlElement(getEffektifQName(elementName));
//      newElement.addAttribute("value", value);
//      XmlElement extensionElements = xml.findOrAddChildElement(getBpmnQName("extensionElements"));
//      extensionElements.addElement(newElement);
//    }
//  }
//
//  /**
//   * Writes an extension element with the string value as text content.
//   */
//  public void writeStringValueAsText(String elementName, String value) {
//    if (value != null && !value.isEmpty()) {
//      XmlElement newElement = new XmlElement(getEffektifQName(elementName));
//      newElement.addText(value);
//      XmlElement extensionElements = xml.findOrAddChildElement(getBpmnQName("extensionElements"));
//      extensionElements.addElement(newElement);
//    }
//  }
//
//  /**
//   * Writes an extension element with the string value as text content.
//   */
//  public void writeStringValueAsCData(String elementName, String value) {
//    if (value != null && !value.isEmpty()) {
//      writeStringValueAsText(elementName, "<![CDATA[" + value + "]]>");
//    }
//  }
//
//  /**
//   * Writes extension elements with the string mappings as attribute values.
//   */
//  public void writeStringMappings(XmlElement xml, String elementName, String keyAttribute, String valueAttribute, Map<String, String> mappings) {
//    if (mappings != null && !mappings.isEmpty()) {
//      XmlElement extensionElements = xml.findOrAddChildElement(getBpmnQName("extensionElements"));
//      for (String mappingKey : mappings.keySet()) {
//        XmlElement newElement = new XmlElement(getEffektifQName(elementName));
//        newElement.addAttribute(keyAttribute, mappingKey);
//        newElement.addAttribute(valueAttribute, mappings.get(mappingKey));
//        extensionElements.addElement(newElement);
//      }
//    }
//  }
//
//  @Override
//  public void writeBoolean(String fieldName, Boolean value) {
//    if (value!=null) {
//      writeBpmnAttribute(fieldName, value.toString());
//    }
//  }
//
//  @Override
//  public void writeNumber(String fieldName, Number value) {
//    if (value!=null) {
//      writeBpmnAttribute(fieldName, value.toString());
//    }
//  }
//
//  @Override
//  public void writeObject(String fieldName, JsonWritable o) {
//  }
//
//  @Override
//  public void writeId(Id id) {
//    writeId("id", id);
//  }
//
//  @Override
//  public void writeId(String fieldName, Id id) {
//    if (id!=null) {
//      writeBpmnAttribute(fieldName, id.getInternal());
//
//      writeAttribute(fieldName, id.getInternal());
//    }
//  }

//  protected void writeActivities() {
//    XmlElement parentXml = xml;
//    Scope parentScope = scope;
//    List<Activity> activities = scope.getActivities();
//    if (activities!=null) {
//      // We loop backwards and then add each activity as the first
//      // This way all the parsed activities will be serialized first
//      // before the unknown elements and the parsed elements will
//      // appear in the order as they were parsed.
//      for (int i=activities.size()-1; i>=0; i--) {
//        scope = activities.get(i);
//        xml = pushXml(scope.getBpmn());
//        BpmnTypeMapping bpmnTypeMapping = mappings.getBpmnTypeMapping(scope.getClass());
//        if (bpmnTypeMapping==null) {
//          throw new RuntimeException("Register "+scope.getClass()+" in class "+Mappings.class.getName()+" with method registerSubClass and ensure annotation "+BpmnElement.class+" is set");
//        }
//        bpmnFieldMappings = bpmnTypeMapping.getBpmnFieldMappings();
//        setBpmnName(bpmnTypeMapping.getBpmnElementName());
//        Map<String, String> bpmnTypeAttributes = bpmnTypeMapping.getBpmnTypeAttributes();
//        if (bpmnTypeAttributes!=null) {
//          for (String attribute: bpmnTypeAttributes.keySet()) {
//            String value = bpmnTypeAttributes.get(attribute);
//            xml.addAttribute(getEffektifQName(attribute), value);
//          }
//        }
//        scope.writeFields(this);
//        parentXml.addElementFirst(xml);
//        bpmnFieldMappings = null;
//      }
//    }
//    xml = parentXml;
//    scope = parentScope;
//  }

//  @Override
//  public void writeFields(Map<String, ? extends Object> fieldValues) {
//  }
//
//  @Override
//  public void writeMap(String fieldName, Map<String, ? extends Object> map) {
//  }
//
//
//  /** adds the element to the current xmlElement's bpmn:extensionElements. */
//  public void writeExtensionElement(XmlElement extensionElement) {
//    if (extensionElement!=null) {
//      XmlElement extensionElements = xml.findOrAddChildElement(getBpmnQName("extensionElements"));
//      extensionElements.addElement(extensionElement);
//    }
//  }
//
}