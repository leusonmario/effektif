/* Copyright 2014 Effektif GmbH.
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
package com.effektif.workflow.api.activities;

import com.effektif.workflow.api.workflow.Binding;



public class CallMapping {
  
  protected Binding sourceBinding;
  protected String destinationVariableId;
  
  public CallMapping() {
  }

  public CallMapping(Binding sourceBinding, String destinationVariableId) {
    this.sourceBinding = sourceBinding;
    this.destinationVariableId = destinationVariableId;
  }

  public CallMapping sourceBinding(Binding sourceBinding) {
    this.sourceBinding = sourceBinding;
    return this;
  }

  public CallMapping destinationVariableId(String destinationVariableId) {
    this.destinationVariableId = destinationVariableId;
    return this;
  }

  public Binding getSourceBinding() {
    return sourceBinding;
  }

  public void setSourceBinding(Binding sourceBinding) {
    this.sourceBinding = sourceBinding;
  }
  
  public String getDestinationVariableId() {
    return destinationVariableId;
  }
  
  public void setDestinationVariableId(String destinationVariableId) {
    this.destinationVariableId = destinationVariableId;
  }
}