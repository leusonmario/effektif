/*
 * Copyright 2014 Effektif GmbH.
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
 * limitations under the License.
 */
package com.effektif.workflow.api.workflow;

import java.util.HashMap;
import java.util.Map;

import com.effektif.workflow.api.bpmn.BpmnReadable;
import com.effektif.workflow.api.bpmn.BpmnReader;
import com.effektif.workflow.api.bpmn.BpmnWritable;
import com.effektif.workflow.api.bpmn.BpmnWriter;
import com.effektif.workflow.api.bpmn.XmlElement;

/**
 * @author Tom Baeyens
 */
public abstract class Trigger implements BpmnReadable, BpmnWritable {

  protected Map<String,OutputParameter> outputs;
  
  @Override
  public void readBpmn(BpmnReader r) {
    for (XmlElement element : r.readElementsEffektif("output")) {
      if (outputs == null) {
        outputs = new HashMap<>();
      }
      r.startElement(element);
      String key = r.readStringAttributeEffektif("key");
      String variableId = r.readStringAttributeEffektif("id");
      outputs.put(key, new OutputParameter().variableId(variableId));
      r.endElement();
    }
  }

  @Override
  public void writeBpmn(BpmnWriter w) {
    if (outputs != null) {
      for (Map.Entry<String, OutputParameter> parameter : outputs.entrySet()) {
        w.startElementEffektif("output");
        w.writeStringAttributeEffektif("key", parameter.getKey());
        w.writeStringAttributeEffektif("id", parameter.getValue().getVariableId());
        w.endElement();
      }
    }
  }
  
  public Map<String, OutputParameter> getOutputs() {
    return outputs;
  }
  public void setOutputs(Map<String, OutputParameter> outputs) {
    this.outputs = outputs;
  }
  
  public Trigger output(String key, String outputVariableId) {
    output(key, new OutputParameter().variableId(outputVariableId));
    return this;
  }
  
  public Trigger output(String key, OutputParameter outputParameter) {
    if (outputs==null) {
      outputs = new HashMap<>();
    }
    outputs.put(key, outputParameter);
    return this;
  }
}
