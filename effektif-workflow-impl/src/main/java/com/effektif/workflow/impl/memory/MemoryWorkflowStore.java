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
package com.effektif.workflow.impl.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joda.time.LocalDateTime;

import com.effektif.workflow.api.query.WorkflowQuery;
import com.effektif.workflow.api.workflow.Workflow;
import com.effektif.workflow.impl.WorkflowEngineConfiguration;
import com.effektif.workflow.impl.WorkflowStore;
import com.effektif.workflow.impl.plugin.Initializable;
import com.effektif.workflow.impl.plugin.ServiceRegistry;
import com.effektif.workflow.impl.workflow.WorkflowImpl;


public class MemoryWorkflowStore implements WorkflowStore, Initializable<WorkflowEngineConfiguration> {

  protected Map<String, Long> nextVersionByName;
  protected Map<String, Workflow> workflows;

  public MemoryWorkflowStore() {
  }

  @Override
  public void initialize(ServiceRegistry serviceRegistry, WorkflowEngineConfiguration configuration) {
    this.workflows = new ConcurrentHashMap<String, Workflow>();
    this.nextVersionByName = new ConcurrentHashMap<String, Long>();
  }
  
  @Override
  public String generateWorkflowId() {
    return UUID.randomUUID().toString();
  }

  @Override
  public void insertWorkflow(Workflow workflowApi, WorkflowImpl workflowImpl) {
    workflows.put(workflowApi.getId(), workflowApi);

    String workflowName = workflowApi.getName();
    if (workflowName!=null) {
      Long nextVersion = nextVersionByName.get(workflowName);
      if (nextVersion==null) {
        nextVersion = 1l;
      }
      workflowImpl.version = nextVersion;
      workflowApi.setVersion(workflowImpl.version);
      nextVersion++;
      nextVersionByName.put(workflowName, nextVersion);
    }
  }

  @Override
  public List<Workflow> findWorkflows(WorkflowQuery query) {
    List<Workflow> result = null;
    if (query.getWorkflowId()!=null) {
      result = new ArrayList<Workflow>();
      Workflow workflow = workflows.get(query.getWorkflowId());
      if (workflow!=null) {
        result.add(workflow);
      }
    } else if (result==null) {
      result = new ArrayList<Workflow>(workflows.values());
    }
    if (query.getWorkflowName()!=null && !result.isEmpty()) {
      filterByName(result, query.getWorkflowName());
    }
    if (query.getLimit()!=null) {
      while (result.size()>query.getLimit()) {
        result.remove(result.size()-1);
      }
    }
    return result;
  }
  
  protected void filterByName(List<Workflow> result, String name) {
    for (int i=result.size()-1; i>=0; i--) {
      if (!name.equals(result.get(i).getName())) {
        result.remove(i);
      }
    }
  }

  protected boolean matchesProcessDefinitionCriteria(WorkflowImpl process, WorkflowQuery query) {
    if (query.getWorkflowName()!=null && !query.getWorkflowName().equals(process.name)) {
      return false;
    }
    return true;
  }

  @Override
  public String findLatestWorkflowIdByName(String workflowName) {
    if (workflowName==null) {
      return null;
    }
    Workflow latestWorkflow = null;
    LocalDateTime latestDeployTime = null;
    for (Workflow workflow: workflows.values()) {
      if ( workflowName.equals(workflow.getName())
           && (latestDeployTime==null || latestDeployTime.isAfter(workflow.getDeployedTime()))
         ) {
        latestWorkflow = workflow;
        latestDeployTime = workflow.getDeployedTime();
      }
    }
    return latestWorkflow!=null ? latestWorkflow.getId() : null;
  }

  @Override
  public void deleteWorkflows(WorkflowQuery query) {
    for (Workflow workflow: findWorkflows(query)) {
      workflows.remove(workflow.getId());
    }
  }

  @Override
  public Workflow loadWorkflowById(String workflowId) {
    return workflows.get(workflowId);
  }
}